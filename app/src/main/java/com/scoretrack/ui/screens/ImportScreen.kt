package com.scoretrack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.scoretrack.core.Subject
import com.scoretrack.ocr.PreprocessOptions
import com.scoretrack.ocr.WarningKind
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.WarningBanner
import com.scoretrack.ui.imports.ImportViewModel
import com.scoretrack.ui.imports.ReviewDraft
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 导入流程：选择来源 → 预处理 + OCR + 表格恢复 → 确认页（可修改每个字段）→ 保存。
 * 只有点击「确认保存」才会写入数据库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    mainVm: MainViewModel,
    importVm: ImportViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = LocalContext.current
    val state by importVm.state.collectAsState()
    val students by mainVm.students.collectAsState()
    val active by mainVm.activeStudent.collectAsState()

    var options by remember {
        mutableStateOf(PreprocessOptions(deskew = true, enhance = true, perspective = true))
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> if (uris.isNotEmpty()) importVm.start(uris, active, options) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) importVm.start(uris, active, options) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) importVm.start(listOf(uri), active, options)
    }

    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val draft = state.draft
    val showingReview = draft != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showingReview) "核对识别结果" else "导入成绩") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showingReview) importVm.clearDraft() else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.busy) {
                SectionCard(title = "正在识别", subtitle = state.stage) {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本地处理：图像增强 / 倾斜校正 / 表格线检测 / ML Kit 中文 OCR。识别结果会先进入确认页，不会直接入库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!showingReview && !state.busy) {
                cameraError?.let { msg ->
                    WarningBanner(text = msg, severe = true, modifier = Modifier.padding(bottom = 8.dp))
                }
                SectionCard(title = "选择成绩单来源", subtitle = "支持 JPG/PNG/WEBP/PDF/XLSX/XLS/CSV，可一次选择多张图片或多页 PDF") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("从相册选择（可多选）")
                        }
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val uri = newCameraUri()
                                    cameraUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    cameraError = "无法启动相机：${e.message ?: "本机没有可用的相机应用"}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("拍照识别")
                        }
                        OutlinedButton(
                            onClick = {
                                fileLauncher.launch(
                                    arrayOf(
                                        "image/*",
                                        "application/pdf",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/octet-stream",
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("从文件管理器导入")
                        }
                    }
                }

                SectionCard(title = "识别选项") {
                    OptionRow("自动旋转 / 倾斜校正", options.deskew) { options = options.copy(deskew = it) }
                    OptionRow("图像增强（对比度、去噪）", options.enhance) { options = options.copy(enhance = it) }
                    OptionRow("透视矫正（表格边框）", options.perspective) { options = options.copy(perspective = it) }
                }

                if (active == null) {
                    WarningBanner("请先在首页创建学生档案，导入时才能自动匹配成绩行。")
                }

                Text(
                    "识别完成后会展示识别到的学生行、六科成绩、总分与置信度，确认保存后才会写入数据库。找不到目标学生、同名多人或赋分列不明确时会要求你手动确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.error?.let { WarningBanner(it, severe = true) }

            if (!showingReview && !state.busy) {
                state.outcome?.let { outcome ->
                    SectionCard(title = "识别结果", subtitle = "共 ${outcome.pages.size} 页 / 识别到 ${outcome.rows.size} 名学生行") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            outcome.previewPath?.let { path ->
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "预处理后的成绩单预览",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                                )
                            }
                            if (outcome.rows.isEmpty()) {
                                WarningBanner("没有识别到可用的学生成绩行。可以尝试重新拍摄（光线更均匀、正对表格），或在成绩页手动录入。", severe = true)
                            }
                            outcome.warnings.take(6).forEach { warning ->
                                WarningBanner(
                                    warning.message,
                                    severe = warning.kind == WarningKind.HEADER_NOT_FOUND ||
                                        warning.kind == WarningKind.STUDENT_NOT_FOUND,
                                )
                            }
                            if (outcome.rows.isNotEmpty()) {
                                Text("识别到的学生行（点击目标学生进入核对）：", style = MaterialTheme.typography.bodySmall)
                                outcome.rows.take(50).forEach { row ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "${row.rowIndex + 1}. ${row.name.ifBlank { "未识别姓名" }}" +
                                                    if (row.className.isNotBlank()) " · ${row.className}" else "",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                Subject.displayOrder().joinToString(" ") { subject ->
                                                    subject.cnName.take(1) + ":" + (row.scores[subject]?.let {
                                                        if (it == Math.floor(it)) it.toInt().toString() else it.toString()
                                                    } ?: "-")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = { importVm.chooseRow(row, active) }) { Text("选这个") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showingReview && draft != null) {
                ReviewContent(
                    draft = draft,
                    students = students,
                    saving = state.saving,
                    retrying = state.retrying,
                    regionMessage = state.regionMessage,
                    onReRecognize = importVm::reRecognize,
                    onUpdate = importVm::updateDraft,
                    onSave = { importVm.save { examId -> if (examId != null) onSaved(examId) } },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewContent(
    draft: ReviewDraft,
    students: List<com.scoretrack.core.Student>,
    saving: Boolean,
    retrying: Subject?,
    regionMessage: String?,
    onReRecognize: (Subject) -> Unit,
    onUpdate: ((ReviewDraft) -> ReviewDraft) -> Unit,
    onSave: () -> Unit,
) {
    val dateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日")
    var showDatePicker by remember { mutableStateOf(false) }
    var showStudentMenu by remember { mutableStateOf(false) }

    val parsed = Subject.displayOrder().associateWith { subject ->
        draft.scores[subject]?.trim()?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    }
    val invalid = Subject.displayOrder().filter { subject ->
        val v = parsed[subject]
        v != null && (v < 0 || v > subject.maxScore)
    }
    val total = if (parsed.values.all { it != null }) parsed.values.sumOf { it ?: 0.0 } else null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        draft.previewPath?.let { path ->
            SectionCard(title = "原始成绩单", subtitle = "保存后会与这次考试记录关联，可随时回来核对") {
                AsyncImage(
                    model = File(path),
                    contentDescription = "成绩单预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                )
            }
        }

        SectionCard(title = "考试信息", subtitle = "所有字段都可以手动修改") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { showStudentMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            students.firstOrNull { it.id == draft.studentId }?.let {
                                "${it.name}${if (it.className.isNotBlank()) " · ${it.className}" else ""}"
                            } ?: "选择学生（必选）"
                        )
                    }
                    DropdownMenu(expanded = showStudentMenu, onDismissRequest = { showStudentMenu = false }) {
                        students.forEach { student ->
                            DropdownMenuItem(
                                text = { Text(student.name + if (student.className.isNotBlank()) " · ${student.className}" else "") },
                                onClick = {
                                    onUpdate { it.copy(studentId = student.id, studentName = student.name, className = student.className) }
                                    showStudentMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.examName,
                    onValueChange = { value -> onUpdate { it.copy(examName = value) } },
                    label = { Text("考试名称 *") },
                    placeholder = { Text("例如：2026年11月月考") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("考试日期：${draft.date.format(dateFormat)}")
                }
                if (draft.className.isNotBlank()) {
                    Text("识别到的班级：${draft.className}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { value -> onUpdate { it.copy(note = value) } },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (regionMessage != null) {
            WarningBanner(regionMessage)
        }

        SectionCard(title = "六科成绩", subtitle = "低置信度字段会用红色标记，请对照原始成绩单核对") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Subject.displayOrder().forEach { subject ->
                    val value = draft.scores[subject].orEmpty()
                    val evidence = draft.evidence[subject]
                    val confidence = draft.confidences[subject]
                    val lowConfidence = (confidence != null && confidence < 0.6f) ||
                        draft.warnings.any { it.kind == WarningKind.LOW_CONFIDENCE && it.rowIndex == null }
                    val isBad = invalid.contains(subject)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { raw ->
                                onUpdate { it.copy(scores = it.scores + (subject to raw.filter { ch -> ch.isDigit() || ch == '.' }.take(6))) }
                            },
                            label = {
                                Text(
                                    subject.cnName + if (subject.isScaled) "（赋分）" else "",
                                    color = if (lowConfidence) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            isError = isBad || lowConfidence,
                            supportingText = {
                                Column {
                                    Text(if (isBad) "超出满分 ${subject.maxScore.toInt()}" else "满分 ${subject.maxScore.toInt()}")
                                    if (evidence != null && evidence.rawText.isNotBlank()) {
                                        Text(
                                            "识别原文：${evidence.rawText}" +
                                                (evidence.confidence?.let { "（置信度 ${String.format("%.2f", it)}）" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onReRecognize(subject) },
                            enabled = draft.previewPath != null && evidence != null && retrying == null,
                        ) {
                            if (retrying == subject) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = "局部重新识别")
                            }
                        }
                    }
                }
                Subject.displayOrder().filter { it.isScaled }.forEach { subject ->
                    OutlinedTextField(
                        value = draft.raws[subject].orEmpty(),
                        onValueChange = { raw ->
                            onUpdate { it.copy(raws = it.raws + (subject to raw.filter { ch -> ch.isDigit() || ch == '.' }.take(6))) }
                        },
                        label = { Text("${subject.cnName}原始分（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Divider()
                Text(
                    "总分：" + (total?.let { if (it == Math.floor(it)) it.toInt().toString() else String.format("%.1f", it) } ?: "六科未填完，暂不计算") +
                        "（满分 750；缺失科目不会按 0 分处理）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        draft.warnings.filter { it.kind != WarningKind.LOW_CONFIDENCE }.take(8).forEach { warning ->
            WarningBanner(warning.message)
        }

        SectionCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("保存原始成绩单", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "保存在应用私有目录，删除文件不会影响已保存的成绩",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = draft.saveOriginal, onCheckedChange = { checked -> onUpdate { it.copy(saveOriginal = checked) } })
            }
        }

        Button(
            onClick = onSave,
            enabled = !saving && invalid.isEmpty() && draft.studentId.isNotBlank() && draft.examName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text("确认保存到成绩记录")
        }
        Text(
            "保存后会生成一条考试记录；如需修改，可在「成绩」页面编辑。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onUpdate { it.copy(date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) }
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}
