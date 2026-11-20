package com.scoretrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.scoretrack.data.wallpaper.WallpaperState
import java.io.File

/**
 * 主页壁纸层：位于最底层（表面色）与前景内容之间的独立图层。
 * - 只对图片本身应用 alpha，前景文字/按钮/卡片不受影响；
 * - 缩放与位移使用相对比例，保证不同屏幕尺寸下与壁纸编辑器的预览一致。
 */
@Composable
fun WallpaperLayer(
    state: WallpaperState,
    modifier: Modifier = Modifier,
) {
    val path = state.path
    if (!state.visible || path.isNullOrBlank()) return
    val file = File(path)
    if (!file.exists()) return

    Box(modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            painter = rememberAsyncImagePainter(model = file),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.offsetX * size.width
                    translationY = state.offsetY * size.height
                    alpha = state.opacity
                },
        )
    }
}

/** 壁纸之上、前景之下的可读性蒙层（不改变前景透明度）。 */
@Composable
fun WallpaperScrim(modifier: Modifier = Modifier, strength: Float = 0.55f) {
    Box(
        modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = strength)),
    )
}
