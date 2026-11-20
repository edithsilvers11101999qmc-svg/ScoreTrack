package com.scoretrack.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.scoretrack.ai.AiException
import com.scoretrack.data.db.AiReportEntity
import com.scoretrack.report.PdfExporter
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.appContainer
import com.scoretrack.ui.components.ConfirmDialog
import com.scoretrack.ui.components.EmptyState
import com.scoretrack.ui.components.MarkdownText
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.WarningBanner
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnalysisScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenReports: () -> Unit,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val active by vm.activeStudent.collectAsState()
    val exams by vm.exams.collectAsState()
    val analysis by vm.analysis.collectAsState()
    val settings by vm.settings.collectAsState()
    val reports by vm.reports.collectAsState()

    var generating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var reportMeta by remember { mutableStateOf<AiReportEntity?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }
    var pendingGenerate by remember { mutableStateOf(false) }

    fun generate() {
        val student = active ?: return
        if (!settings.aiConfig.hasApiKey) {
            error = "尚未配置 API Key，请前往「设置 → AI 分析设置」填写后重试。"
            return
        }
        if (exams.isEmpty()) {
            error = "当前学生还没有考试记录，无法生成分析报告。"
            return
        }
        scope.launch {
            generating = true
            error = null
            try {
                val report = container.aiAnalysisService.generate(
                    student = student,
                    analysis = analysis,
                    selection = vm.selection.value,
                )
                reportMeta = report
                content = report.content
            } catch (e: AiException) {
                error = "${e.type.title}：${e.message}"
            } catch (e: Exception) {
                error = "生成失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                generating = false
            }
        }
    }

    fun requestGenerate() {
        if (!settings.aiPrivacyAccepted) {
            pendingGenerate = true
            showPrivacy = true
        } else {
            generate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 成绩分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = onOpenAiSettings) { Icon(Icons.Outlined.Settings, contentDescription = "AI 设置") }
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
            SectionCard(
                title = "分析对象",
                subtitle = active?.let { "${it.name} · ${exams.size} 次考试 · 完整 ${analysis.overall.completeExams} 次" } ?: "未选择学生",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "AI 只读取当前学生已确认保存的成绩数据，并在本地整理为结构化 JSON 后发送给你配置的 API。不会上传原始照片。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!settings.aiConfig.hasApiKey) {
                        WarningBanner("尚未配置 API Key，AI 分析不可用（其他功能不受影响）。")
                        OutlinedButton(onClick = onOpenAiSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("前往配置 API")
                        }
                    }
                    Button(
                        onClick = { requestGenerate() },
                        enabled = !generating && active != null && exams.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (generating) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("正在生成，请稍候…")
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("生成 AI 分析报告")
                        }
                    }
                    if (reports.isNotEmpty()) {
                        TextButton(onClick = onOpenReports) { Text("查看历史 AI 报告（${reports.size}）") }
                    }
                }
            }

            error?.let { WarningBanner(it, severe = true) }

            if (content != null) {
                SectionCard(
                    title = reportMeta?.title ?: "AI 分析报告",
                    subtitle = reportMeta?.let { "${it.studentName} · ${it.examRange} · ${it.model}" },
                ) {
                    Column {
                        MarkdownText(content!!)
                        Spacer(Modifier.size(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenReports, modifier = Modifier.weight(1f)) {
                                Text("查看历史报告")
                            }
                            OutlinedButton(onClick = { requestGenerate() }, modifier = Modifier.weight(1f)) {
                                Text("重新生成")
                            }
                        }
                    }
                }
            } else if (!generating) {
                EmptyState(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "还没有生成报告",
                    message = "点击上方按钮，AI 会结合总分趋势、六科得分率、稳定性给出分析。数据不足时会明确说明。",
                )
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false; pendingGenerate = false },
            title = { Text("AI 分析数据说明") },
            text = {
                Text(
                    "本次分析将向你在「AI 分析设置」中配置的 API 服务发送：\n\n" +
                        "· 学生姓名、班级（用于报告称呼）\n" +
                        "· 已保存考试的考试名称、日期、六科成绩、总分、得分率\n" +
                        "· 本地统计结果（平均分、最高分、趋势）\n\n" +
                        "不会发送原始成绩单图片、文件、学号以外的个人信息，也不会发送 API Key。报告只保存在本机，可随时删除。\n\n" +
                        "AI 分析必须由你主动触发，费用由你的 API 账户承担。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.settingsRepository.setAiPrivacyAccepted() }
                    showPrivacy = false
                    if (pendingGenerate) {
                        pendingGenerate = false
                        generate()
                    }
                }) { Text("同意并继续") }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacy = false; pendingGenerate = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenReport: (String) -> Unit,
    onNewReport: () -> Unit,
) {
    val reports by vm.reports.collectAsState()
    var deleting by remember { mutableStateOf<AiReportEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史 AI 报告") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = onNewReport) { Icon(Icons.Outlined.AutoAwesome, contentDescription = "生成新报告") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (reports.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "还没有 AI 报告",
                        message = "在分析页点击「生成 AI 分析报告」，报告会保存在这里，并保留生成时的数据快照。",
                        actionLabel = "去生成报告",
                        onAction = onNewReport,
                    )
                }
            }
            items(reports, key = { it.id }) { report ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(report.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${report.studentName} · ${report.examRange}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                dateText(report.createdAt) + " · ${report.model}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onOpenReport(report.id) }) { Text("查看") }
                        IconButton(onClick = { deleting = report }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    deleting?.let { report ->
        ConfirmDialog(
            title = "删除 AI 报告",
            message = "确定删除「${report.title}」（${dateText(report.createdAt)}）吗？删除后不可恢复，但不会影响成绩数据。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = { vm.deleteReport(report.id) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    reportId: String,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf<AiReportEntity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reportId) {
        report = container.aiReportRepository.find(reportId)
        loading = false
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val current = report ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = PdfExporter.aiReport(
                    title = current.title,
                    studentName = current.studentName,
                    examRange = current.examRange,
                    content = current.content,
                    meta = "生成时间：" + dateText(current.createdAt) + " · 模型：" + current.model,
                )
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                message = "PDF 已导出"
            }.onFailure { message = "导出失败：${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 分析报告") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = {
                        report?.let { clipboard.setText(AnnotatedString(it.content)); message = "报告文字已复制" }
                    }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "复制") }
                    IconButton(onClick = {
                        report?.let { pdfLauncher.launch(it.title + ".pdf") }
                    }) { Icon(Icons.Outlined.PictureAsPdf, contentDescription = "导出 PDF") }
                },
            )
        },
    ) { padding ->
        if (loading) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("正在读取报告…") }
            return@Scaffold
        }
        val current = report
        if (current == null) {
            EmptyState(title = "报告不存在", message = "该报告可能已被删除。")
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Text(current.studentName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(current.examRange, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    dateText(current.createdAt) + " · " + current.model + " · " + current.providerHost,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (current.promptTokens != null || current.completionTokens != null) {
                    Text(
                        "tokens: ${current.promptTokens ?: "-"} / ${current.completionTokens ?: "-"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionCard {
                MarkdownText(current.content)
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(current.content)); message = "报告文字已复制" },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("复制")
                }
                OutlinedButton(
                    onClick = { pdfLauncher.launch(current.title + ".pdf") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("导出 PDF")
                }
            }
            TextButton(onClick = { showDelete = true }) {
                Text("删除这份报告", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "删除报告",
            message = "删除后不可恢复，但不会影响成绩数据。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                scope.launch {
                    container.aiReportRepository.delete(reportId)
                    onBack()
                }
            },
            onDismiss = { showDelete = false },
        )
    }
}

private fun dateText(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

@Suppress("unused")
private fun shareFile(context: android.content.Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}
