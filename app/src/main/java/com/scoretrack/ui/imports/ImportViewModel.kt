package com.scoretrack.ui.imports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scoretrack.AppContainer
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Student
import com.scoretrack.core.Subject
import com.scoretrack.data.db.SourceDocumentEntity
import com.scoretrack.data.repo.DocumentStore
import com.scoretrack.data.repo.Ids
import com.scoretrack.ocr.ImportOutcome
import com.scoretrack.ocr.ParseWarning
import com.scoretrack.ocr.PreprocessOptions
import com.scoretrack.ocr.RecognizedRow
import com.scoretrack.ocr.SheetReconstructor
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReviewDraft(
    val examName: String,
    val date: LocalDate,
    val studentId: String,
    val studentName: String,
    val className: String,
    val note: String = "",
    val scores: Map<Subject, String>,
    val raws: Map<Subject, String>,
    val confidences: Map<Subject, Float>,
    val evidence: Map<Subject, com.scoretrack.core.FieldEvidence>,
    val warnings: List<ParseWarning>,
    val saveOriginal: Boolean = true,
    val sourceUris: List<String> = emptyList(),
    val previewPath: String? = null,
)

class ImportViewModel(private val container: AppContainer) : ViewModel() {

    data class Ui(
        val busy: Boolean = false,
        val progress: Float = 0f,
        val stage: String = "",
        val outcome: ImportOutcome? = null,
        val error: String? = null,
        val draft: ReviewDraft? = null,
        val savedExamId: String? = null,
        val saving: Boolean = false,
        /** 正在对某科做局部重新识别 */
        val retrying: Subject? = null,
        /** 局部重识别的结果提示 */
        val regionMessage: String? = null,
    )

    private val _state = MutableStateFlow(Ui())
    val state: StateFlow<Ui> = _state.asStateFlow()

    private val documentStore = DocumentStore(container.appContext)

    fun reset() {
        _state.value = Ui()
    }

    fun clearDraft() {
        _state.value = _state.value.copy(draft = null, savedExamId = null)
    }

    fun start(uris: List<Uri>, student: Student?, options: PreprocessOptions) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.value = Ui(busy = true, progress = 0f, stage = "准备中…")
            val outcome = container.importPipeline.process(uris, student, options) { progress, stage ->
                _state.value = _state.value.copy(progress = progress, stage = stage)
            }
            val draft = outcome.matched?.let { row ->
                buildDraft(row, student, outcome)
            }
            _state.value = Ui(
                busy = false,
                progress = 1f,
                stage = "识别完成",
                outcome = outcome,
                error = outcome.failure,
                draft = draft,
            )
        }
    }

    fun chooseRow(row: RecognizedRow, student: Student?) {
        val outcome = _state.value.outcome ?: return
        val draft = buildDraft(row, student, outcome)
        _state.value = _state.value.copy(draft = draft, error = null)
    }

    /**
     * 对单科成绩所在区域放大后重新识别（用于低置信度/模糊单元格）。
     * 解析失败时不写脏数据，只提示用户手动输入。
     */
    fun reRecognize(subject: Subject) {
        val draft = _state.value.draft ?: return
        val evidence = draft.evidence[subject]
        val path = draft.previewPath
        if (evidence == null || path == null) {
            _state.value = _state.value.copy(regionMessage = "该科目没有可用于重识别的区域，请手动输入")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(retrying = subject, regionMessage = null)
            val text = container.importPipeline.recognizeRegion(path, evidence.left, evidence.top, evidence.right, evidence.bottom)
            if (text == null) {
                _state.value = _state.value.copy(retrying = null, regionMessage = "局部重新识别没有读到文字，请手动输入")
                return@launch
            }
            val warnings = ArrayList<ParseWarning>()
            val value = SheetReconstructor.parseScore(text, subject.maxScore, subject, warnings, 0)
            val current = _state.value.draft ?: draft
            _state.value = _state.value.copy(
                retrying = null,
                draft = current.copy(
                    scores = if (value != null) current.scores + (subject to formatNumber(value)) else current.scores,
                    evidence = current.evidence + (subject to evidence.copy(rawText = text)),
                ),
                regionMessage = if (value == null) {
                    "重识别结果「$text」不是有效分数（满分 ${subject.maxScore.toInt()}），请手动输入"
                } else {
                    "${subject.cnName} 已更新为 ${formatNumber(value)}（识别原文「$text」）"
                },
            )
        }
    }

    fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        val draft = _state.value.draft ?: return
        _state.value = _state.value.copy(draft = transform(draft))
    }

    private fun buildDraft(row: RecognizedRow, student: Student?, outcome: ImportOutcome): ReviewDraft {
        return ReviewDraft(
            examName = "",
            date = LocalDate.now(),
            studentId = student?.id ?: "",
            studentName = row.name.ifBlank { student?.name ?: "" },
            className = row.className.ifBlank { student?.className ?: "" },
            scores = Subject.displayOrder().associateWith { subject ->
                row.scores[subject]?.let { formatNumber(it) } ?: ""
            },
            raws = Subject.displayOrder().associateWith { subject ->
                row.rawScores[subject]?.let { formatNumber(it) } ?: ""
            },
            confidences = row.confidences,
            evidence = row.evidence,
            warnings = outcome.warnings + row.warnings,
            saveOriginal = true,
            sourceUris = outcome.sources.map { it.uriString },
            previewPath = outcome.previewPath,
        )
    }

    private fun formatNumber(value: Double): String =
        if (value == Math.floor(value)) value.toInt().toString() else String.format("%.1f", value)

    fun save(onSaved: (String?) -> Unit) {
        val draft = _state.value.draft ?: return
        val scope = viewModelScope
        scope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            val messages = mutableListOf<String>()
            if (draft.studentId.isBlank()) {
                _state.value = _state.value.copy(saving = false, error = "请选择学生后再保存")
                onSaved(null)
                return@launch
            }
            if (draft.examName.isBlank()) {
                _state.value = _state.value.copy(saving = false, error = "请填写考试名称")
                onSaved(null)
                return@launch
            }
            val scores: Map<Subject, Double?> = Subject.displayOrder().associateWith { subject ->
                val raw = draft.scores[subject]?.trim().orEmpty()
                if (raw.isBlank()) null else raw.toDoubleOrNull()
            }
            val invalid = Subject.displayOrder().filter { subject ->
                val v = scores[subject]
                v != null && (v < 0 || v > subject.maxScore)
            }
            if (invalid.isNotEmpty()) {
                _state.value = _state.value.copy(
                    saving = false,
                    error = "成绩超出满分：" + invalid.joinToString("、") { it.cnName },
                )
                onSaved(null)
                return@launch
            }
            if (scores.values.all { it == null }) {
                _state.value = _state.value.copy(saving = false, error = "至少填写一科成绩后再保存")
                onSaved(null)
                return@launch
            }

            var documentId: String? = null
            if (draft.saveOriginal && draft.sourceUris.isNotEmpty()) {
                val files = runCatching {
                    documentStore.copy(draft.sourceUris.map { Uri.parse(it) }, "sheet_" + System.currentTimeMillis())
                }.getOrElse {
                    messages += "原始文件保存失败：${it.message ?: "未知错误"}"
                    emptyList()
                }
                if (files.isNotEmpty()) {
                    documentId = Ids.newId()
                    container.documentRepository.save(
                        SourceDocumentEntity(
                            id = documentId,
                            studentId = draft.studentId,
                            fileName = files.first().name,
                            mimeType = mimeOf(files.first().name),
                            localPath = files.joinToString("|") { it.absolutePath },
                            pageCount = maxOf(1, files.size),
                            importedAt = System.currentTimeMillis(),
                            kind = when (files.first().name.substringAfterLast('.', "").lowercase()) {
                                "pdf" -> "PDF"
                                "csv", "xlsx", "xls" -> "SHEET"
                                else -> "IMAGE"
                            },
                            ocrPayloadJson = null,
                        )
                    )
                }
            }

            val rawScores = draft.raws.filterValues { it.isNotBlank() }
                .mapValues { it.value.toDoubleOrNull() }
                .filterValues { it != null }
            val examId = container.examRepository.save(
                id = null,
                studentId = draft.studentId,
                name = draft.examName,
                date = draft.date,
                note = draft.note,
                scores = scores,
                rawScores = rawScores,
                confidences = draft.confidences,
                sourceDocumentId = documentId,
            )
            _state.value = _state.value.copy(saving = false, savedExamId = examId, error = null)
            onSaved(examId)
        }
    }

    private fun mimeOf(name: String): String = when {
        name.endsWith(".pdf", true) -> "application/pdf"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".webp", true) -> "image/webp"
        else -> "image/jpeg"
    }

    fun latestExamFor(studentId: String): ExamRecord? = null
}
