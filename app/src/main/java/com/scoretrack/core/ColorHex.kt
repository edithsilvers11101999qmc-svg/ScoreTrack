package com.scoretrack.core

import kotlin.math.roundToLong

/**
 * 颜色十六进制解析（#RGB / #RRGGBB / #AARRGGBB，允许省略 #）。
 * 纯函数便于单元测试；返回 ARGB Long 或 null。
 */
object ColorHex {

    fun parse(input: String): Long? {
        var text = input.trim().removePrefix("#").uppercase()
        if (text.startsWith("0X")) text = text.removePrefix("0X")
        if (text.length == 3) {
            text = text.map { "$it$it" }.joinToString("")
        }
        if (text.length == 6) text = "FF$text"
        if (text.length != 8) return null
        val value = text.toLongOrNull(16) ?: return null
        return value and 0xFFFFFFFFL
    }

    /** 规范化：不透明色输出 6 位（#RRGGBB），带透明度时输出 8 位（#AARRGGBB）。 */
    fun normalize(input: String): String? {
        val argb = parse(input) ?: return null
        val alpha = (argb ushr 24) and 0xFF
        return if (alpha == 0xFFL) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)
    }

    fun toHex(argb: Long, withAlpha: Boolean = false): String =
        if (withAlpha) "#%08X".format(argb and 0xFFFFFFFFL)
        else "#%06X".format(argb and 0xFFFFFFL)

    /** 相对亮度（sRGB，0..1），用于自动选择前景文字颜色。 */
    fun luminance(argb: Long): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** 深色背景用白字，浅色背景用近黑字。 */
    fun onColor(argb: Long): Long = if (luminance(argb) > 0.45) 0xFF10130FL else 0xFFFFFFFFL

    fun blend(from: Long, to: Long, ratio: Double): Long {
        val t = ratio.coerceIn(0.0, 1.0)
        fun mix(shift: Int): Long {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return ((a + (b - a) * t).roundToLong() and 0xFF) shl shift
        }
        return 0xFF000000L or mix(16) or mix(8) or mix(0)
    }
}
