package com.scoretrack.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scoretrack.core.ColorHex
import com.scoretrack.data.settings.ThemeMode
import com.scoretrack.data.theme.ThemeState
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.SectionCard
import com.scoretrack.ui.components.WarningBanner
import com.scoretrack.ui.theme.ThemePresets
import java.io.File

/**
 * 个性化设置：主题颜色 + 壁纸入口。主题切换立即生效（无需重启），
 * 壁纸只作用于主页，且透明度只影响壁纸图层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onEditWallpaper: (String) -> Unit,
) {
    val theme by vm.themeState.collectAsState()
    val wallpaper by vm.wallpaper.collectAsState()

    var hexInput by remember(theme) { mutableStateOf(theme.effectiveHex) }
    var hexError by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.importWallpaper(uri) { ok -> if (ok) onEditWallpaper("import") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个性化设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxWidth().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionCard(title = "主题颜色", subtitle = "切换后立即生效，重启后仍然保留") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = theme.mode == mode,
                                    onClick = { vm.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                ) { Text(mode.label) }
                            }
                        }

                        Text("预设主题", style = MaterialTheme.typography.labelLarge)
                        ThemePresets.all.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { preset ->
                                    PresetSwatch(
                                        label = preset.label,
                                        hex = preset.hex,
                                        selected = !theme.useCustom && theme.presetId == preset.id,
                                        modifier = Modifier.weight(1f),
                                        onClick = { vm.setThemePreset(preset.id, preset.hex) },
                                    )
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }

                        Text("自定义颜色", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = {
                                hexInput = it
                                hexError = null
                            },
                            label = { Text("十六进制颜色（如 #2F5FA8）") },
                            singleLine = true,
                            isError = hexError != null,
                            supportingText = hexError?.let { { Text(it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val normalized = ColorHex.normalize(hexInput)
                                    if (normalized == null) {
                                        hexError = "请输入 #RGB / #RRGGBB / #AARRGGBB 格式"
                                    } else {
                                        vm.setCustomTheme(normalized)
                                        hexInput = normalized
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("应用自定义颜色") }
                            OutlinedButton(onClick = { vm.resetTheme(); hexInput = ThemeState.DEFAULT_HEX }) {
                                Text("恢复默认")
                            }
                        }
                        if (theme.useCustom) {
                            Text(
                                "当前使用自定义颜色 ${theme.customHex}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "壁纸", subtitle = "仅作用于主页；选择后可调整缩放、位置与透明度") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (wallpaper.hasImage) {
                            AsyncImage(
                                model = File(wallpaper.path!!),
                                contentDescription = "壁纸预览",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                    Text("尚未设置壁纸", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (wallpaper.hasImage) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("显示壁纸", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "关闭后使用默认背景，图片不会被删除",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = wallpaper.enabled,
                                    onCheckedChange = { vm.setWallpaperEnabled(it) },
                                )
                            }

                            Text(
                                "透明度 ${(wallpaper.opacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Slider(
                                value = wallpaper.opacity,
                                onValueChange = { value ->
                                    vm.saveWallpaperTransform(
                                        wallpaper.scale,
                                        wallpaper.offsetX,
                                        wallpaper.offsetY,
                                        value,
                                    )
                                },
                                valueRange = 0f..1f,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { onEditWallpaper("edit") },
                                    modifier = Modifier.weight(1f),
                                ) { Text("调整位置与缩放") }
                                OutlinedButton(onClick = { vm.clearWallpaper() }) { Text("恢复默认背景") }
                            }
                        }

                        Button(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (wallpaper.hasImage) "更换壁纸" else "从相册选择壁纸") }

                        Text(
                            "支持 JPG / JPEG / PNG / WEBP；图片会复制到应用私有目录，重启后自动恢复。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                SectionCard(title = "主页模块", subtitle = "控制主页各模块的显示与顺序") {
                    val dashboard by vm.dashboard.collectAsState()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dashboard.sortedBy { it.sortOrder }.forEach { section ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (section.cardType) {
                                        "goals" -> "大学目标卡"
                                        "latest_exam" -> "最近考试"
                                        "subject_overview" -> "六科概览"
                                        else -> section.cardType
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { vm.moveDashboard(section.cardType, -1) }) { Text("↑") }
                                TextButton(onClick = { vm.moveDashboard(section.cardType, 1) }) { Text("↓") }
                                Switch(
                                    checked = section.enabled,
                                    onCheckedChange = { vm.setDashboardEnabled(section.cardType, it) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "主题色统一作用于导航栏、按钮、选中态、进度条、图表与强调文字；深浅色模式会自动调整前景文字对比度。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetSwatch(
    label: String,
    hex: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = ThemePresets.hexToColor(hex)
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(color, CircleShape)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(ColorHex.onColor(ColorHex.parse(hex) ?: 0xFF000000L).toInt()),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
