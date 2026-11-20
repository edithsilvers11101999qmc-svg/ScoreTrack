package com.scoretrack.backup

import com.scoretrack.core.Subject
import com.scoretrack.data.db.AiReportEntity
import com.scoretrack.data.db.ExamEntity
import com.scoretrack.data.db.ScoreTrackDatabase
import com.scoretrack.data.db.ScoreEntity
import com.scoretrack.data.db.SourceDocumentEntity
import com.scoretrack.data.db.DashboardCardEntity
import com.scoretrack.data.db.GoalCardEntity
import com.scoretrack.data.db.StudentEntity
import com.scoretrack.data.db.SubjectSelectionEntity
import com.scoretrack.data.db.ThemeSettingEntity
import com.scoretrack.data.db.WallpaperSettingEntity
import com.scoretrack.data.db.SubjectEntity
import androidx.room.withTransaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StudentDto(
    val id: String,
    val name: String,
    val className: String,
    val studentNumber: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean = false,
)

@Serializable
data class SubjectDto(
    val code: String,
    val displayName: String,
    val maxScore: Double,
    val scaled: Boolean,
    val sortOrder: Int,
    val enabled: Boolean = true,
)

@Serializable
data class ScoreDto(
    val subjectCode: String,
    val score: Double? = null,
    val rawScore: Double? = null,
    val maxScore: Double,
    val confidence: Float? = null,
    val sourceText: String? = null,
)

@Serializable
data class ExamDto(
    val id: String,
    val studentId: String,
    val name: String,
    val examDate: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val sourceDocumentId: String? = null,
    val scores: List<ScoreDto> = emptyList(),
)

@Serializable
data class DocumentDto(
    val id: String,
    val studentId: String? = null,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val pageCount: Int,
    val importedAt: Long,
    val kind: String,
    val ocrPayloadJson: String? = null,
)

@Serializable
data class ReportDto(
    val id: String,
    val studentId: String,
    val studentName: String,
    val title: String,
    val examRange: String,
    val createdAt: Long,
    val model: String,
    val providerHost: String,
    val content: String,
    val dataSnapshotJson: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val status: String = "SUCCESS",
)

@Serializable
data class ThemeDto(
    val themeMode: String = "SYSTEM",
    val presetId: String = "default_blue",
    val primaryColorHex: String = "#2B5F9E",
    val customColorHex: String = "#2B5F9E",
    val useCustom: Boolean = false,
)

@Serializable
data class WallpaperDto(
    val wallpaperPath: String? = null,
    val enabled: Boolean = false,
    val opacity: Float = 1f,
    val cropScale: Float = 1f,
    val cropOffsetX: Float = 0f,
    val cropOffsetY: Float = 0f,
)

@Serializable
data class GoalDto(
    val cardId: String,
    val studentId: String,
    val universityName: String,
    val majorName: String = "",
    val targetScore: Double,
    val isVisible: Boolean = true,
    val sortOrder: Int = 0,
    val accentColorHex: String? = null,
    val cardOpacity: Float = 1f,
    val showAuxInfo: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** 校徽图片（Base64，JPEG）；为空表示没有校徽。超过 700KB 的图片不写入备份。 */
    val logoBase64: String? = null,
)

@Serializable
data class DashboardDto(
    val cardType: String,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0,
)

@Serializable
data class SelectionDto(
    val studentId: String,
    val firstSubject: String? = null,
    val secondSubject1: String? = null,
    val secondSubject2: String? = null,
    val updatedAt: Long = 0L,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val appVersion: String = "2.0.0",
    val exportedAt: String = "",
    val students: List<StudentDto> = emptyList(),
    val subjects: List<SubjectDto> = emptyList(),
    val exams: List<ExamDto> = emptyList(),
    val documents: List<DocumentDto> = emptyList(),
    val reports: List<ReportDto> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    // V3 新增（旧备份可缺省，用默认值）
    val theme: ThemeDto? = null,
    val wallpaper: WallpaperDto? = null,
    val goals: List<GoalDto> = emptyList(),
    val dashboard: List<DashboardDto> = emptyList(),
    // V4 新增（3+1+2 选科，旧备份缺省为空）
    val selections: List<SelectionDto> = emptyList(),
) {
    companion object {
        const val FORMAT = "scoretrack-backup"
        const val VERSION = 5
        const val MIN_SUPPORTED = 1
    }
}

data class RestorePreview(
    val students: Int,
    val exams: Int,
    val scores: Int,
    val documents: Int,
    val reports: Int,
    val exportedAt: String,
    val version: Int,
)

data class RestoreResult(
    val studentsAdded: Int,
    val examsAdded: Int,
    val examsSkipped: Int,
    val reportsAdded: Int,
    val documentsAdded: Int,
)

enum class RestoreMode { MERGE, REPLACE }

/**
 * 完整备份 / 恢复：JSON 文本格式带版本号，恢复前校验格式与版本；
 * 合并模式按记录 ID 与内容指纹去重，避免重复导入产生重复考试。
 */
class BackupService(private val db: ScoreTrackDatabase, private val filesDir: java.io.File? = null) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun exportJson(): String {
        val backup = BackupFile(
            exportedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            students = db.studentDao().all().map { it.toDto() },
            subjects = db.subjectDao().all().map { it.toDto() },
            exams = db.examDao().listAll().map { exam ->
                ExamDto(
                    id = exam.exam.id,
                    studentId = exam.exam.studentId,
                    name = exam.exam.name,
                    examDate = exam.exam.examDate,
                    note = exam.exam.note,
                    createdAt = exam.exam.createdAt,
                    updatedAt = exam.exam.updatedAt,
                    sourceDocumentId = exam.exam.sourceDocumentId,
                    scores = exam.scores.map { it.toDto() },
                )
            },
            documents = db.documentDao().all().map { it.toDto() },
            selections = db.subjectSelectionDao().all().map {
                SelectionDto(it.studentId, it.firstSubject, it.secondSubject1, it.secondSubject2, it.updatedAt)
            },
            reports = db.aiReportDao().all().map { it.toDto() },
            // 不导出 API Key（密文与本机 Keystore 绑定，导出无意义且有泄露风险）
            settings = db.settingsDao().all()
                .filterNot { it.key.startsWith("ai.api_key") }
                .associate { it.key to it.value },
            theme = db.themeDao().get()?.let {
                ThemeDto(it.themeMode, it.presetId, it.primaryColorHex, it.customColorHex, it.useCustom)
            },
            wallpaper = db.wallpaperDao().get()?.let {
                WallpaperDto(
                    it.wallpaperPath, it.enabled, it.opacity,
                    it.cropScale, it.cropOffsetX, it.cropOffsetY,
                )
            },
            goals = db.goalDao().listAll().map {
                GoalDto(
                    it.cardId, it.studentId, it.universityName, it.majorName, it.targetScore,
                    it.isVisible, it.sortOrder, it.accentColorHex, it.cardOpacity, it.showAuxInfo,
                    it.createdAt, it.updatedAt, encodeLogo(it.logoPath),
                )
            },
            dashboard = db.dashboardDao().list().map { DashboardDto(it.cardType, it.isEnabled, it.sortOrder) },
        )
        return json.encodeToString(backup)
    }

    fun parse(text: String): BackupFile {
        val trimmed = text.trim()
        require(trimmed.startsWith("{")) { "文件不是有效的 ScoreTrack 备份（应为 JSON 对象）" }
        val backup = try {
            json.decodeFromString<BackupFile>(trimmed)
        } catch (t: Throwable) {
            throw IllegalArgumentException("备份文件解析失败：${t.message ?: "JSON 格式错误"}")
        }
        require(backup.format == BackupFile.FORMAT) { "不是 ScoreTrack 备份文件（format=${backup.format}）" }
        require(backup.version in BackupFile.MIN_SUPPORTED..BackupFile.VERSION) {
            "备份版本 ${backup.version} 不受支持（当前支持 ${BackupFile.MIN_SUPPORTED}~${BackupFile.VERSION}）"
        }
        return backup
    }

    fun preview(backup: BackupFile): RestorePreview = RestorePreview(
        students = backup.students.size,
        exams = backup.exams.size,
        scores = backup.exams.sumOf { it.scores.size },
        documents = backup.documents.size,
        reports = backup.reports.size,
        exportedAt = backup.exportedAt,
        version = backup.version,
    )

    suspend fun restore(text: String, mode: RestoreMode): RestoreResult {
        val backup = parse(text)
        var studentsAdded = 0
        var examsAdded = 0
        var examsSkipped = 0
        var reportsAdded = 0
        var documentsAdded = 0

        // 去重所需的现有数据必须在事务外读取：
        // Room 的 suspend 事务里再调用 @Transaction 的关联查询时，读到的可能是空快照，
        // 会导致「按 ID 去重」失效并覆盖已有考试（连带丢 sourceDocumentId）。
        val existingStudents = db.studentDao().all()
        val existingDocIds = db.documentDao().all().map { it.id }.toHashSet()
        val existingReportIds = db.aiReportDao().all().map { it.id }.toHashSet()
        val existingExamList = db.examDao().listAll()
        val existingStudentIds = existingStudents.map { it.id }.toHashSet()
        val existingExamIds = existingExamList.map { it.exam.id }.toHashSet()
        val fingerprints = existingExamList.map { examFingerprint(it.exam, it.scores) }.toHashSet()

        db.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                existingExamList.forEach { db.examDao().deleteExam(it.exam.id) }
                existingStudents.forEach { db.studentDao().deleteById(it.id) }
                db.documentDao().all().forEach { db.documentDao().deleteById(it.id) }
                db.aiReportDao().all().forEach { db.aiReportDao().deleteById(it.id) }
                existingStudentIds.clear()
                existingExamIds.clear()
                fingerprints.clear()
                existingDocIds.clear()
                existingReportIds.clear()
            }

            for (s in backup.students) {
                db.studentDao().upsert(
                    StudentEntity(s.id, s.name, s.className, s.studentNumber, s.createdAt, s.updatedAt, s.isActive)
                )
                if (existingStudentIds.add(s.id)) studentsAdded++
            }
            backup.selections.forEach { sel ->
                if (sel.studentId in existingStudentIds) {
                    db.subjectSelectionDao().upsert(
                        SubjectSelectionEntity(
                            studentId = sel.studentId,
                            firstSubject = sel.firstSubject,
                            secondSubject1 = sel.secondSubject1,
                            secondSubject2 = sel.secondSubject2,
                            updatedAt = sel.updatedAt,
                        )
                    )
                }
            }
            if (db.studentDao().activeStudent() == null) {
                db.studentDao().all().firstOrNull()?.let { db.studentDao().activate(it.id) }
            }

            for (e in backup.exams) {
                if (existingExamIds.contains(e.id)) {
                    examsSkipped++
                    continue
                }
                val entity = ExamEntity(
                    id = e.id,
                    studentId = e.studentId,
                    name = e.name,
                    examDate = e.examDate,
                    note = e.note,
                    createdAt = e.createdAt,
                    updatedAt = e.updatedAt,
                    sourceDocumentId = e.sourceDocumentId,
                )
                val scores = e.scores.mapNotNull { s ->
                    val max = Subject.fromCode(s.subjectCode)?.maxScore ?: s.maxScore
                    ScoreEntity(e.id, s.subjectCode, s.score, s.rawScore, max, s.confidence, s.sourceText)
                }
                // 内容指纹：即使 ID 不同，同一学生同一天同名同分的考试也视为重复
                if (!fingerprints.add(examFingerprint(entity, scores))) {
                    examsSkipped++
                    continue
                }
                db.examDao().saveExam(entity, scores)
                existingExamIds.add(e.id)
                examsAdded++
            }

            for (d in backup.documents) {
                if (!existingDocIds.add(d.id)) continue
                db.documentDao().upsert(
                    SourceDocumentEntity(
                        d.id, d.studentId, d.fileName, d.mimeType, d.localPath,
                        d.pageCount, d.importedAt, d.kind, d.ocrPayloadJson,
                    )
                )
                documentsAdded++
            }

            for (r in backup.reports) {
                if (!existingReportIds.add(r.id)) continue
                db.aiReportDao().upsert(
                    AiReportEntity(
                        r.id, r.studentId, r.studentName, r.title, r.examRange, r.createdAt,
                        r.model, r.providerHost, r.content, r.dataSnapshotJson,
                        r.promptTokens, r.completionTokens, r.status,
                    )
                )
                reportsAdded++
            }

            db.settingsDao().putAll(backup.settings.map { com.scoretrack.data.db.AppSettingEntity(it.key, it.value) })

            // V3 设置与目标卡：旧备份没有这些字段，保持现有值不动
            backup.theme?.let { t ->
                db.themeDao().upsert(
                    ThemeSettingEntity(
                        id = 1, themeMode = t.themeMode, presetId = t.presetId,
                        primaryColorHex = t.primaryColorHex, customColorHex = t.customColorHex,
                        useCustom = t.useCustom, updatedAt = System.currentTimeMillis(),
                    )
                )
            }
            backup.wallpaper?.let { w ->
                val exists = w.wallpaperPath?.let { java.io.File(it).isFile } == true
                db.wallpaperDao().upsert(
                    WallpaperSettingEntity(
                        id = 1,
                        wallpaperPath = if (exists) w.wallpaperPath else null,
                        enabled = exists && w.enabled,
                        opacity = w.opacity, cropScale = w.cropScale,
                        cropOffsetX = w.cropOffsetX, cropOffsetY = w.cropOffsetY,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
            if (backup.goals.isNotEmpty()) {
                val studentIds = db.studentDao().all().map { it.id }.toSet()
                db.goalDao().upsertAll(
                    backup.goals.filter { studentIds.contains(it.studentId) }.map {
                        GoalCardEntity(
                            it.cardId, it.studentId, it.universityName, it.majorName, it.targetScore,
                            it.isVisible, it.sortOrder, it.accentColorHex, it.cardOpacity, it.showAuxInfo,
                            restoreLogo(it.cardId, it.logoBase64), it.createdAt, it.updatedAt,
                        )
                    }
                )
            }
            if (backup.dashboard.isNotEmpty()) {
                db.dashboardDao().upsertAll(
                    backup.dashboard.map { DashboardCardEntity(it.cardType, it.isEnabled, it.sortOrder) }
                )
            }
        }

        return RestoreResult(studentsAdded, examsAdded, examsSkipped, reportsAdded, documentsAdded)
    }

    private fun examFingerprint(exam: com.scoretrack.data.db.ExamEntity, scores: List<ScoreEntity>): String {
        val scorePart = scores.sortedBy { it.subjectCode }
            .joinToString(",") { "${it.subjectCode}=${it.score ?: "-"}" }
        return "${exam.studentId}|${exam.name}|${exam.examDate}|$scorePart"
    }

    // ------------------------------------------------------------- 校徽图片

    private fun logoDir(): java.io.File? =
        filesDir?.let { java.io.File(it, "goal_logos").apply { mkdirs() } }

    /** 把校徽文件读成 Base64 放进备份；超过 700KB 或文件不存在时返回 null。 */
    private fun encodeLogo(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = java.io.File(path)
        if (!file.isFile || file.length() > 700_000L) return null
        return try {
            android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /** 恢复校徽：把 Base64 写回私有目录，返回新的绝对路径。 */
    private fun restoreLogo(cardId: String, base64: String?): String? {
        val dir = logoDir() ?: return null
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            val file = java.io.File(dir, "logo_restored_$cardId.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ CSV

    suspend fun exportCsv(): String {
        val students = db.studentDao().all().associateBy { it.id }
        val exams = db.examDao().listAll()
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("学生,班级,学号,考试名称,考试日期,备注")
        Subject.displayOrder().forEach { sb.append(",${it.cnName}(${it.maxScore.toInt()})") }
        sb.append(",总分\n")
        for (exam in exams.sortedWith(compareBy({ it.exam.examDate }, { it.exam.name }))) {
            val student = students[exam.exam.studentId]
            val values = Subject.displayOrder().associate { s ->
                s to exam.scores.firstOrNull { it.subjectCode == s.code }?.score
            }
            val total = if (values.values.all { it != null }) values.values.sumOf { it ?: 0.0 } else null
            sb.append(csv(student?.name ?: "")).append(',')
            sb.append(csv(student?.className ?: "")).append(',')
            sb.append(csv(student?.studentNumber ?: "")).append(',')
            sb.append(csv(exam.exam.name)).append(',')
            sb.append(csv(java.time.LocalDate.ofEpochDay(exam.exam.examDate / 86_400_000L).toString())).append(',')
            sb.append(csv(exam.exam.note))
            Subject.displayOrder().forEach { s ->
                val v = values[s]
                sb.append(',').append(v?.let { trimNumber(it) } ?: "")
            }
            sb.append(',').append(total?.let { trimNumber(it) } ?: "")
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun dateText(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

    private fun trimNumber(value: Double): String =
        if (value == Math.floor(value)) value.toInt().toString() else value.toString()

    private fun csv(value: String): String {
        val needsQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    // ------------------------------------------------------------- mappings

    private fun StudentEntity.toDto() = StudentDto(id, name, className, studentNumber, createdAt, updatedAt, isActive)
    private fun SubjectEntity.toDto() = SubjectDto(code, displayName, maxScore, scaled, sortOrder, enabled)
    private fun ScoreEntity.toDto() = ScoreDto(subjectCode, score, rawScore, maxScore, confidence, sourceText)
    private fun SourceDocumentEntity.toDto() = DocumentDto(
        id, studentId, fileName, mimeType, localPath, pageCount, importedAt, kind, ocrPayloadJson,
    )
    private fun AiReportEntity.toDto() = ReportDto(
        id, studentId, studentName, title, examRange, createdAt, model, providerHost,
        content, dataSnapshotJson, promptTokens, completionTokens, status,
    )
}
