package com.scoretrack.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.scoretrack.core.ColorHex

data class ThemePreset(val id: String, val label: String, val hex: String)

object ThemePresets {

    val all: List<ThemePreset> = listOf(
        ThemePreset("default_blue", "默认蓝", "#2F5FA8"),
        ThemePreset("sky", "天空蓝", "#2E8BC0"),
        ThemePreset("mint", "薄荷绿", "#2E9E7E"),
        ThemePreset("sakura", "樱花粉", "#D96C93"),
        ThemePreset("lavender", "薰衣草紫", "#7A6BC8"),
        ThemePreset("sunset", "日落橙", "#E07A3F"),
        ThemePreset("deep_sea", "深海蓝", "#1B4F72"),
    )

    fun byId(id: String): ThemePreset? = all.firstOrNull { it.id == id }

    fun hexToColor(hex: String): Color {
        val argb = ColorHex.parse(hex) ?: 0xFF2F5FA8L
        return Color(argb.toInt())
    }
}

/**
 * 由品牌色派生出完整的 Material 3 配色：
 * 容器色/表面色由品牌色与黑白色按固定比例混合，保证任意自定义颜色下
 * 前景文字对比度仍然足够（onPrimary / onPrimaryContainer 通过亮度自动选择）。
 */
object ColorSchemeFactory {

    fun build(seedHex: String, dark: Boolean): ColorScheme {
        val seed = (ColorHex.parse(seedHex) ?: 0xFF2F5FA8L) and 0xFFFFFFFFL
        fun c(v: Long): Color = Color(v.toInt())

        return if (!dark) {
            val primary = seed
            val container = ColorHex.blend(seed, WHITE, 0.78)
            val secondary = ColorHex.blend(seed, 0xFF5F6368L, 0.35)
            val background = ColorHex.blend(seed, WHITE, 0.955)
            val surfaceVariant = ColorHex.blend(seed, WHITE, 0.88)
            lightColorScheme(
                primary = c(primary),
                onPrimary = c(ColorHex.onColor(primary)),
                primaryContainer = c(container),
                onPrimaryContainer = c(ColorHex.blend(seed, BLACK, 0.62)),
                secondary = c(secondary),
                onSecondary = c(ColorHex.onColor(secondary)),
                secondaryContainer = c(ColorHex.blend(seed, WHITE, 0.86)),
                onSecondaryContainer = c(ColorHex.blend(seed, BLACK, 0.55)),
                tertiary = c(ColorHex.blend(seed, 0xFFB0543A, 0.55)),
                onTertiary = Color.White,
                tertiaryContainer = ColorHex.blend(seed, WHITE, 0.80).let { c(it) },
                onTertiaryContainer = c(ColorHex.blend(seed, BLACK, 0.60)),
                background = c(background),
                onBackground = c(0xFF171C21L),
                surface = Color.White,
                onSurface = c(0xFF171C21L),
                surfaceVariant = c(surfaceVariant),
                onSurfaceVariant = c(ColorHex.blend(seed, 0xFF3F444AL, 0.35)),
                outline = c(ColorHex.blend(seed, 0xFF6B7078L, 0.35)),
                error = c(0xFFBA1A1AL),
                errorContainer = c(0xFFFFDAD6L),
                onErrorContainer = c(0xFF410002L),
            )
        } else {
            val primary = ColorHex.blend(seed, WHITE, 0.58)
            val background = ColorHex.blend(seed, 0xFF07090CL, 0.90)
            val surface = ColorHex.blend(seed, 0xFF0C0F13L, 0.86)
            darkColorScheme(
                primary = c(primary),
                onPrimary = c(ColorHex.blend(seed, BLACK, 0.75)),
                primaryContainer = c(ColorHex.blend(seed, BLACK, 0.62)),
                onPrimaryContainer = c(ColorHex.blend(seed, WHITE, 0.80)),
                secondary = c(ColorHex.blend(seed, 0xFFB8C2CCL, 0.45)),
                onSecondary = c(0xFF10151AL),
                secondaryContainer = c(ColorHex.blend(seed, 0xFF1E252CL, 0.55)),
                onSecondaryContainer = c(ColorHex.blend(seed, WHITE, 0.82)),
                tertiary = c(ColorHex.blend(seed, 0xFFFFB59FL, 0.55)),
                onTertiary = c(0xFF3A1206L),
                tertiaryContainer = c(ColorHex.blend(seed, 0xFF5A2412L, 0.60)),
                onTertiaryContainer = c(ColorHex.blend(seed, WHITE, 0.85)),
                background = c(background),
                onBackground = c(0xFFE2E6E8L),
                surface = c(surface),
                onSurface = c(0xFFE2E6E8L),
                surfaceVariant = c(ColorHex.blend(seed, 0xFF2A3138L, 0.55)),
                onSurfaceVariant = c(ColorHex.blend(seed, WHITE, 0.72)),
                outline = c(ColorHex.blend(seed, WHITE, 0.42)),
                error = c(0xFFFFB4ABL),
                errorContainer = c(0xFF93000AL),
                onErrorContainer = c(0xFFFFDAD6L),
            )
        }
    }

    private const val WHITE = 0xFFFFFFFFL
    private const val BLACK = 0xFF000000L
}
