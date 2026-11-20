package com.scoretrack.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scoretrack.BuildConfig
import com.scoretrack.core.Subject
import com.scoretrack.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    var showChangelog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
            SectionCard {
                Text("得分追踪 · ScoreTrack", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                Text("策划：Clara Windy", style = MaterialTheme.typography.bodyMedium)
                Text("制作：deepseek", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { showChangelog = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text("查看更新日志") }
                Spacer(Modifier.size(8.dp))
                Text(
                    "本地优先的高中成绩管理与分析工具：手动录入或 OCR 识别成绩单，生成曲线与统计，并可选用你自己的 API 进行 AI 学情分析。所有数据保存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionCard(title = "科目与满分") {
                Subject.displayOrder().forEach { subject ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(subject.cnName + if (subject.isScaled) "（赋分成绩）" else "（原始成绩）")
                        Text("${subject.maxScore.toInt()}")
                    }
                }
                Text("总分满分 750 分；缺失科目不会按 0 分计算。", style = MaterialTheme.typography.bodySmall)
            }

            SectionCard(title = "数据与隐私") {
                Text("· 不需要账号，不联网也能使用全部本地功能。", style = MaterialTheme.typography.bodySmall)
                Text("· AI 分析仅在你主动点击时调用你配置的 API，只发送结构化成绩数据。", style = MaterialTheme.typography.bodySmall)
                Text("· API Key 使用 Android Keystore 加密保存，不写入日志、不包含在备份中。", style = MaterialTheme.typography.bodySmall)
                Text("· 可随时在设置中删除 AI 报告、清除 API 配置。", style = MaterialTheme.typography.bodySmall)
            }

            SectionCard(title = "开源组件") {
                Text("Jetpack Compose · Material 3 · Room · Kotlin Coroutines", style = MaterialTheme.typography.bodySmall)
                Text("Google ML Kit 文字识别（中文）", style = MaterialTheme.typography.bodySmall)
                Text("MPAndroidChart（Apache-2.0）· Coil · JExcelAPI", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("更新日志") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("V4.0.0", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "· 新增新高考 3+1+2 选科管理：首选 2 选 1 + 再选 4 选 2，共 12 种组合\n" +
                            "· 科目扩展到 9 科，原有 750 分总分规则不变，选考总分独立统计\n" +
                            "· 选科按学生隔离并持久化，切换学生自动切换\n" +
                            "· 录入页高亮选考科目，分析页标注选考/未选\n" +
                            "· AI 分析结合选科组合与选考三科\n" +
                            "· 数据库升级到 v3，升级前自动备份并校验旧数据",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("V3.0.0", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "· 新增主题颜色系统：7 种预设 + 自定义 HEX，深浅色全局生效\n" +
                            "· 新增主页壁纸：相册选择、缩放拖动、透明度、持久化\n" +
                            "· 新增大学目标卡：进度百分比、剩余分数、关闭与排序\n" +
                            "· 新增关于页署名与版本信息\n" +
                            "· 数据库升级到 v2，升级前自动备份并校验旧数据",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("V2.0.0", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "· 成绩单 OCR（图片 / PDF / XLSX / XLS / CSV）与人工复核\n" +
                            "· 成绩曲线、得分率、综合统计\n" +
                            "· 可配置 API 的 AI 学情分析报告\n" +
                            "· 本地备份 / 恢复 / CSV / PDF 导出",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("关闭") } },
        )
    }
}
