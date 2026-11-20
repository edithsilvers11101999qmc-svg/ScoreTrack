package com.scoretrack.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 透视矫正：用表格最外框的四条边定位四角，偏差明显时做单应变换拉直。
 * 边线检测不到时返回原图，交给后续倾斜校正处理。
 */
object PerspectiveCorrector {

    data class Result(val bitmap: Bitmap, val applied: Boolean, val maxDeviation: Float)

    fun correct(bitmap: Bitmap, lines: GridDetector.Lines, w: Int, h: Int): Result {
        val hs = lines.horizontals.sortedBy { it.y }
        val vs = lines.verticals.sortedBy { it.x }
        if (hs.size < 2 || vs.size < 2) return Result(bitmap, false, 0f)

        val top = hs.first()
        val bottom = hs.last()
        val left = vs.first()
        val right = vs.last()

        val src = floatArrayOf(
            left.x.toFloat().coerceAtLeast(0f), top.y.toFloat(),
            right.x.toFloat().coerceAtMost(w - 1f), top.y.toFloat(),
            right.x.toFloat().coerceAtMost(w - 1f), bottom.y.toFloat(),
            left.x.toFloat().coerceAtLeast(0f), bottom.y.toFloat(),
        )
        val topLen = hypot(src[2] - src[0], src[3] - src[1])
        val bottomLen = hypot(src[6] - src[4], src[7] - src[5])
        val leftLen = hypot(src[6] - src[0], src[7] - src[1])
        val rightLen = hypot(src[4] - src[2], src[5] - src[3])
        if (topLen < w * 0.35f || bottomLen < w * 0.35f) return Result(bitmap, false, 0f)

        val deviation = maxOf(abs(topLen - bottomLen) / maxOf(topLen, 1f), abs(leftLen - rightLen) / maxOf(leftLen, 1f))
        if (deviation < 0.035f) return Result(bitmap, false, deviation)

        val dstW = ((topLen + bottomLen) / 2f).toInt().coerceAtLeast(16)
        val dstH = ((leftLen + rightLen) / 2f).toInt().coerceAtLeast(16)
        val dst = floatArrayOf(0f, 0f, dstW.toFloat(), 0f, dstW.toFloat(), dstH.toFloat(), 0f, dstH.toFloat())
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return Result(bitmap, false, deviation)
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return Result(out, true, deviation)
    }
}
