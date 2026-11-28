package com.scoretrack.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.theme.ThemePresets

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onOpenStudents: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenSelection: () -> Unit,
    onOpenCountdown: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val theme by vm.themeState.collectAsState()
    val students by vm.students.collectAsState()
    val active by vm.activeStudent.collectAsState()
    val goals by vm.goals.collectAsState()
    val selection by vm.selection.collectAsState()

    val presetLabel = ThemePresets.all.firstOrNull { it.id == theme.presetId }?.label ?: "自定义颜色"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(title = "学生", subtitle = "共 ${students.size} 个档案 · 当前：${active?.name ?: "未选择"}") {
            SettingRow(Icons.Outlined.Groups, "学生管理", "创建、切换、编辑、删除学生档案") { onOpenStudents() }
            SettingRow(
                Icons.Outlined.School,
                "大学目标",
                if (goals.isEmpty()) "设置目标大学与目标总分" else "共 ${goals.size} 个目标 · 进度按最近考试计算",
            ) { onOpenGoals() }
            SettingRow(
                Icons.Outlined.MenuBook,
                "选科管理",
                if (selection.isComplete) "当前：${selection.comboLabel}" else "尚未完成选科（3+1+2）",
            ) { onOpenSelection() }
            SettingRow(
                Icons.Outlined.Event,
                "考试倒计时",
                "添加考试、设置倒计时与主页主显示",
            ) { onOpenCountdown() }
        }
        SectionCard(title = "自定义与主题", subtitle = "主题颜色、壁纸、主页模块") {
            SettingRow(
                Icons.Outlined.Palette,
                "个性化设置",
                "${theme.mode.label} · $presetLabel",
            ) { onOpenPersonalization() }
        }
        SectionCard(title = "AI 智能分析", subtitle = "OpenAI 兼容接口，可自定义 Base URL 与模型") {
            SettingRow(
                Icons.Outlined.AutoAwesome,
                "AI 分析设置",
                if (settings.aiConfig.hasApiKey) "已配置 ${settings.aiConfig.model} · ${settings.aiConfig.displayHost}" else "未配置 API Key",
            ) { onOpenAiSettings() }
            SettingRow(Icons.Outlined.Description, "历史 AI 报告", "查看、复制、导出或删除已生成的报告") { onOpenReports() }
        }
        SectionCard(title = "数据") {
            SettingRow(Icons.Outlined.Backup, "数据备份与恢复", "JSON 完整备份 / 恢复，原始成绩单文件备份") { onOpenBackup() }
            SettingRow(Icons.Outlined.CloudUpload, "导出 CSV", "导出全部学生成绩（Excel 可直接打开）") { onExportCsv() }
            SettingRow(Icons.Outlined.PictureAsPdf, "导出分析 PDF", "当前学生的成绩分析报告") { onExportPdf() }
        }
        SectionCard(title = "关于") {
            SettingRow(Icons.Outlined.Info, "关于 ScoreTrack", "版本、署名、开源许可与隐私说明") { onOpenAbout() }
        }
        Text(
            "ScoreTrack 所有成绩数据保存在本机数据库中，不需要账号，也不会自动上传。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
