package com.scoretrack.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scoretrack.backup.BackupArchive
import com.scoretrack.backup.RestoreMode
import com.scoretrack.backup.RestorePreview
import com.scoretrack.report.PdfExporter
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.appContainer
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.WarningBanner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active by vm.activeStudent.collectAsState()
    val exams by vm.exams.collectAsState()
    val analysis by vm.analysis.collectAsState()

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<String, RestorePreview>?>(null) }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }

    fun writeBytes(uri: Uri, bytes: ByteArray, successMessage: String) {
        scope.launch {
            busy = true
            error = null
            status = null
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("无法写入目标文件")
                }
            }.onSuccess { status = successMessage }.onFailure { error = "导出失败：${it.message}" }
            busy = false
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            runCatching {
                val json = container.backupService.exportJson()
                val files = File(context.filesDir, "documents").listFiles()?.toList() ?: emptyList()
                val bytes = withContext(Dispatchers.IO) { BackupArchive.zip(json, files) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                container.settingsRepository.setLastBackupAt(System.currentTimeMillis())
                status = "已导出完整备份（含 ${files.size} 个原始成绩单文件）"
            }.onFailure { error = "备份失败：${it.message}" }
            busy = false
        }
    }

    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = container.backupService.exportJson()
            writeBytes(uri, json.toByteArray(Charsets.UTF_8), "已导出 JSON 备份数据")
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = container.backupService.exportCsv()
            writeBytes(uri, csv.toByteArray(Charsets.UTF_8), "已导出 CSV 成绩表")
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            runCatching {
                val student = active ?: throw IllegalStateException("请先选择学生")
                val bytes = PdfExporter.analysisReport(
                    studentName = student.name,
                    className = student.className,
                    exams = exams,
                    analysis = analysis,
                )
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                }
                status = "已导出成绩分析 PDF（${exams.size} 次考试）"
            }.onFailure { error = "PDF 导出失败：${it.message}" }
            busy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            status = null
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取文件")
                }
                val text = BackupArchive.read(bytes)
                val backup = container.backupService.parse(text)
                val preview = container.backupService.preview(backup)
                pendingRestore = text to preview
            }.onFailure { error = "恢复失败：${it.message}" }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与导出") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
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
            SectionCard(title = "备份", subtitle = "备份包含学生、考试、成绩、原始成绩单文件与历史 AI 报告；不包含 API Key") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { zipLauncher.launch("ScoreTrack备份_" + dateStamp() + ".zip") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Backup, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导出完整备份（ZIP，含原始成绩单）")
                    }
                    OutlinedButton(
                        onClick = { jsonLauncher.launch("ScoreTrack数据_" + dateStamp() + ".json") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("仅导出 JSON 数据") }
                }
            }

            SectionCard(title = "导出") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("ScoreTrack成绩_" + dateStamp() + ".csv") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导出 CSV 成绩表（全部学生）")
                    }
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("ScoreTrack分析_" + (active?.name ?: "学生") + ".pdf") },
                        enabled = !busy && active != null && exams.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导出当前学生成绩分析 PDF")
                    }
                }
            }

            SectionCard(title = "恢复", subtitle = "恢复前会先解析并校验格式与版本号；合并模式会自动跳过重复考试") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "application/zip", "application/octet-stream")) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Restore, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("选择备份文件恢复")
                    }
                    Text(
                        "恢复不会覆盖已有记录（合并模式）；如需重建，请选择「清空并恢复」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("处理中…", style = MaterialTheme.typography.bodySmall)
                }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            error?.let { WarningBanner(it, severe = true) }
        }
    }

    pendingRestore?.let { (text, preview) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("确认恢复备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份版本：v${preview.version} · 导出时间：${preview.exportedAt.ifBlank { "未知" }}")
                    Text("学生：${preview.students} · 考试：${preview.exams} · 成绩项：${preview.scores}")
                    Text("原始成绩单：${preview.documents} · AI 报告：${preview.reports}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = restoreMode == RestoreMode.MERGE, onClick = { restoreMode = RestoreMode.MERGE })
                        Column { Text("合并（推荐）"); Text("按 ID 与内容指纹去重，跳过重复考试", style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = restoreMode == RestoreMode.REPLACE, onClick = { restoreMode = RestoreMode.REPLACE })
                        Column { Text("清空并恢复"); Text("先删除本机现有全部成绩数据", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = restoreMode
                    scope.launch {
                        busy = true
                        pendingRestore = null
                        runCatching {
                            val result = container.backupService.restore(text, chosen)
                            status = "恢复完成：新增学生 ${result.studentsAdded}，新增考试 ${result.examsAdded}，跳过重复 ${result.examsSkipped}，报告 ${result.reportsAdded}"
                        }.onFailure { error = "恢复失败：${it.message}" }
                        busy = false
                    }
                }) { Text("开始恢复") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } },
        )
    }
}

private fun dateStamp(): String =
    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
