package com.scoretrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scoretrack.ui.MainViewModel
import com.scoretrack.ui.components.WallpaperLayer

/**
 * 壁纸编辑器：预览区域铺满整个窗口，与主页壁纸层使用同一套变换逻辑，
 * 因此所见即所得（不会出现拉伸、变形或黑边）。
 * 支持双指缩放、单指拖动、透明度调整与恢复初始状态。
 */
@Composable
fun WallpaperEditorScreen(vm: MainViewModel, onBack: () -> Unit) {
    val wallpaper by vm.wallpaper.collectAsState()

    var scale by remember(wallpaper.path) { mutableFloatStateOf(wallpaper.scale.coerceIn(1f, 4f)) }
    var offsetX by remember(wallpaper.path) { mutableFloatStateOf(wallpaper.offsetX) }
    var offsetY by remember(wallpaper.path) { mutableFloatStateOf(wallpaper.offsetY) }
    var opacity by remember(wallpaper.path) { mutableFloatStateOf(wallpaper.opacity) }

    val editing = wallpaper.copy(
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
        opacity = opacity,
        enabled = true,
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WallpaperLayer(
            state = editing,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale
                        val maxOffset = ((newScale - 1f) / 2f).coerceAtLeast(0f)
                        offsetX = (offsetX + pan.x / size.width).coerceIn(-maxOffset, maxOffset)
                        offsetY = (offsetY + pan.y / size.height).coerceIn(-maxOffset, maxOffset)
                    }
                },
        )

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 3.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text("壁纸编辑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "双指缩放 / 单指拖动，预览与主页显示区域一致",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "恢复初始位置")
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("透明度", style = MaterialTheme.typography.bodyMedium)
                    Text("${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0f..1f)
                Text(
                    "0% 完全不显示壁纸，100% 完全显示；透明度只作用于图片，文字与按钮始终清晰。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                            opacity = 1f
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("恢复初始") }
                    Button(
                        onClick = {
                            vm.saveWallpaperTransform(scale, offsetX, offsetY, opacity)
                            onBack()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存") }
                }
            }
        }
    }
}
