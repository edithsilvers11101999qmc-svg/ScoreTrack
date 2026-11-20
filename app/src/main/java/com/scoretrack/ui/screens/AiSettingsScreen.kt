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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.scoretrack.ai.ConnectionCheck
import com.scoretrack.data.settings.AiConfig
import com.scoretrack.data.settings.buildEndpoint
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.appContainer
import com.scoretrack.ui.components.ConfirmDialog
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.WarningBanner
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val container = appContainer()
    val settings by vm.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(settings.aiConfig.baseUrl) }
    var model by remember { mutableStateOf(settings.aiConfig.model) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(settings.aiConfig.temperature.toFloat()) }
    var maxTokens by remember { mutableStateOf(settings.aiConfig.maxTokens.toString()) }
    var testing by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<ConnectionCheck?>(null) }
    var showClear by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.aiConfig.baseUrl, settings.aiConfig.model) {
        if (apiKey.isBlank()) {
            baseUrl = settings.aiConfig.baseUrl
            model = settings.aiConfig.model
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard(
            title = "AI 分析设置",
            subtitle = "支持任何 OpenAI 兼容的 Chat Completions 接口，Base URL 与模型名都可以修改",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    placeholder = { Text(AiConfig.DEFAULT_BASE_URL) },
                    singleLine = true,
                    supportingText = { Text("实际请求地址：${buildEndpoint(baseUrl)}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model Name") },
                    placeholder = { Text(AiConfig.DEFAULT_MODEL) },
                    singleLine = true,
                    supportingText = { Text("例如 deepseek-chat、gpt-4o-mini、qwen-plus 等") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (showKey) "隐藏密钥" else "查看密钥",
                            )
                        }
                    },
                    supportingText = {
                        Text(
                            if (settings.aiConfig.hasApiKey) {
                                "已保存：${settings.aiConfig.keyPreview}（留空表示不修改）"
                            } else {
                                "尚未配置。密钥使用 Android Keystore 加密保存在本机，不会写入日志或备份。"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    Text("Temperature：${String.format("%.1f", temperature)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        steps = 19,
                    )
                }
                OutlinedTextField(
                    value = maxTokens,
                    onValueChange = { maxTokens = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("最大输出 Token 数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("默认 2048，范围 64–32768") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        check?.let { result ->
            if (result.ok) {
                SectionCard(title = "连接测试成功") {
                    Text(result.message, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "耗时 ${result.latencyMs} ms · ${result.endpoint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                WarningBanner("连接失败：${result.message}\n地址：${result.endpoint}", severe = true)
            }
        }

        savedMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    scope.launch {
                        val tokens = maxTokens.toIntOrNull()?.coerceIn(64, 32768) ?: 2048
                        container.settingsRepository.saveAiConfig(
                            baseUrl = baseUrl,
                            model = model,
                            temperature = temperature.toDouble(),
                            maxTokens = tokens,
                            apiKey = apiKey.trim().ifBlank { null },
                        )
                        apiKey = ""
                        savedMessage = "配置已保存，无需重新安装即可生效。"
                        check = null
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存配置") }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        testing = true
                        check = null
                        savedMessage = null
                        val key = apiKey.trim().ifBlank { container.settingsRepository.apiKey() }
                        if (key.isNullOrBlank()) {
                            check = ConnectionCheck(
                                ok = false,
                                type = com.scoretrack.ai.AiErrorType.NO_KEY,
                                message = "请先填写 API Key",
                                latencyMs = 0,
                                endpoint = buildEndpoint(baseUrl),
                            )
                            testing = false
                            return@launch
                        }
                        val config = AiConfig(
                            baseUrl = baseUrl,
                            model = model,
                            temperature = temperature.toDouble(),
                            maxTokens = maxTokens.toIntOrNull() ?: 2048,
                        )
                        check = container.openAiClient.testConnection(config, key)
                        testing = false
                    }
                },
                enabled = !testing,
                modifier = Modifier.weight(1f),
            ) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text("测试中")
                } else {
                    Text("测试连接")
                }
            }
        }

        if (settings.aiConfig.hasApiKey) {
            TextButton(onClick = { showClear = true }) {
                Text("清除 API 配置", color = MaterialTheme.colorScheme.error)
            }
        }

        SectionCard(title = "隐私说明") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("· 只有在你点击「生成 AI 分析报告」时才会发起网络请求。", style = MaterialTheme.typography.bodySmall)
                Text("· 发送内容是当前学生已完成保存的结构化成绩（考试名称、日期、各科成绩、总分）、选科组合（3+1+2）与本地统计结果。", style = MaterialTheme.typography.bodySmall)
                Text("· 不会上传原始成绩单照片、文件或无关个人信息；", style = MaterialTheme.typography.bodySmall)
                Text("· API Key 只保存在本机 Keystore，日志中不记录；可在本页随时清除。", style = MaterialTheme.typography.bodySmall)
                Text("· 未配置 API 时，成绩管理、OCR、图表等功能完全可用。", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            "提示：默认示例为 DeepSeek（${AiConfig.DEFAULT_BASE_URL} / ${AiConfig.DEFAULT_MODEL}），API Key 需自行在服务商后台申请。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showClear) {
        ConfirmDialog(
            title = "清除 AI 配置",
            message = "将删除 Base URL、模型与本地保存的 API Key（含 Keystore 密钥）。已生成的 AI 报告不会被删除。",
            confirmLabel = "清除",
            destructive = true,
            onConfirm = {
                scope.launch {
                    container.settingsRepository.clearAiConfig()
                    apiKey = ""
                    check = null
                    savedMessage = "AI 配置已清除"
                    baseUrl = AiConfig.DEFAULT_BASE_URL
                    model = AiConfig.DEFAULT_MODEL
                }
            },
            onDismiss = { showClear = false },
        )
    }
}
