package com.scoretrack.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * 图像预处理：解码、方向纠正、去噪、对比度拉伸、倾斜检测与校正、自适应二值化。
 * 全部在本地完成，不依赖网络。
 */
object BitmapOps {

    private const val TAG = "BitmapOps"

    /** 读取并解码图片，按 EXIF 方向纠正，限制最长边（过大会拖慢 OCR）。 */
    fun decode(context: android.content.Context, uri: Uri, maxDimension: Int = 2600): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val w = opts.outWidth
            val h = opts.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (maxOf(w, h) / sample > maxDimension * 1.6) sample *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null
            val rotated = applyExifRotation(context, uri, raw)
            if (maxOf(rotated.width, rotated.height) > maxDimension) {
                val scale = maxDimension.toFloat() / maxOf(rotated.width, rotated.height)
                scale(rotated, scale)
            } else {
                rotated
            }
        } catch (t: Throwable) {
            Log.w(TAG, "decode failed", t)
            null
        }
    }

    fun decodeFile(path: String, maxDimension: Int = 2600): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            val maxSide = maxOf(opts.outWidth, opts.outHeight)
            while (maxSide / sample > maxDimension * 1.6) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            if (maxOf(bmp.width, bmp.height) > maxDimension) {
                scale(bmp, maxDimension.toFloat() / maxOf(bmp.width, bmp.height))
            } else bmp
        } catch (t: Throwable) {
            Log.w(TAG, "decodeFile failed", t)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun applyExifRotation(context: android.content.Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) bitmap else rotate(bitmap, degrees)
        } catch (t: Throwable) {
            bitmap
        }
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (abs(degrees) < 0.01f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    fun scale(bitmap: Bitmap, factor: Float): Bitmap {
        if (abs(factor - 1f) < 0.01f) return bitmap
        val w = (bitmap.width * factor).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * factor).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    fun crop(bitmap: Bitmap, rect: Rect): Bitmap {
        val r = Rect(
            rect.left.coerceIn(0, bitmap.width - 1),
            rect.top.coerceIn(0, bitmap.height - 1),
            rect.right.coerceIn(1, bitmap.width),
            rect.bottom.coerceIn(1, bitmap.height),
        )
        if (r.width() < 4 || r.height() < 4) return bitmap
        return Bitmap.createBitmap(bitmap, r.left, r.top, r.width(), r.height())
    }

    /** 灰度 + 对比度拉伸 + 轻度去噪，用于改善昏暗/低对比照片。 */
    fun enhance(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val p = pixels[i]
            val g = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            gray[i] = g
            histogram[g]++
        }
        val total = w * h
        val lowCut = (total * 0.02).toInt()
        val highCut = (total * 0.98).toInt()
        var acc = 0
        var lo = 0
        var hi = 255
        for (i in 0..255) {
            acc += histogram[i]
            if (acc >= lowCut) { lo = i; break }
        }
        acc = 0
        for (i in 0..255) {
            acc += histogram[i]
            if (acc >= highCut) { hi = i; break }
        }
        if (hi - lo < 24) { hi = (lo + 24).coerceAtMost(255) }
        val range = (hi - lo).coerceAtLeast(1)
        val lut = IntArray(256)
        for (i in 0..255) {
            var v = ((i - lo) * 255 / range)
            if (v < 0) v = 0
            if (v > 255) v = 255
            // 轻微 gamma 提升暗部细节
            val f = v / 255.0
            val boosted = (Math.pow(f, 0.92) * 255).roundToInt().coerceIn(0, 255)
            lut[i] = boosted
        }
        // 3x3 中值风格去噪（简单均值核，避免椒盐噪点）
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                var sum = 0
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        sum += lut[gray[yy * w + xx]]
                        count++
                    }
                }
                val v = sum / count
                out[idx] = Color.rgb(v, v, v)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    fun toGray(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val g = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            pixels[i] = Color.rgb(g, g, g)
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    fun grayArray(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        return gray
    }

    /**
     * 自适应二值化（积分图均值阈值）。返回 true 表示“墨迹/深色”。
     */
    fun binarize(gray: IntArray, w: Int, h: Int, windowDiv: Int = 32, k: Double = 0.86): BooleanArray {
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += gray[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + rowSum
            }
        }
        val half = (minOf(w, h) / windowDiv).coerceIn(4, 40)
        val out = BooleanArray(w * h)
        for (y in 0 until h) {
            val y1 = (y - half).coerceAtLeast(0)
            val y2 = (y + half).coerceAtMost(h - 1)
            for (x in 0 until w) {
                val x1 = (x - half).coerceAtLeast(0)
                val x2 = (x + half).coerceAtMost(w - 1)
                val area = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * (w + 1) + x2 + 1] -
                    integral[y1 * (w + 1) + x2 + 1] -
                    integral[(y2 + 1) * (w + 1) + x1] +
                    integral[y1 * (w + 1) + x1]
                val mean = sum.toDouble() / area
                out[y * w + x] = gray[y * w + x] < mean * k
            }
        }
        return out
    }

    /**
     * 通过水平投影方差估计倾斜角（度）。返回 0 表示无需校正。
     */
    fun estimateSkew(gray: IntArray, w: Int, h: Int): Float {
        val binary = binarize(gray, w, h, 40, 0.80)
        val pts = ArrayList<Pair<Int, Int>>(80000)
        var step = 1
        if (w * h > 4_000_000) step = 3 else if (w * h > 1_500_000) step = 2
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                if (binary[y * w + x]) pts.add(x to y)
                x += step
            }
            y += step
        }
        if (pts.size < 200) return 0f
        var bestAngle = 0f
        var bestScore = -1.0
        var angle = -7.0f
        while (angle <= 7.0f) {
            val rad = Math.toRadians(angle.toDouble())
            val sin = Math.sin(rad)
            val cos = Math.cos(rad)
            val hist = IntArray(h + 64)
            val offset = 32
            for ((px, py) in pts) {
                val yRot = (py * cos - px * sin).roundToInt()
                val idx = yRot + offset
                if (idx in hist.indices) hist[idx]++
            }
            var score = 0.0
            for (v in hist) if (v > 0) score += v.toDouble() * v
            if (score > bestScore) {
                bestScore = score
                bestAngle = angle
            }
            angle += 0.25f
        }
        return if (abs(bestAngle) < 0.3f) 0f else bestAngle
    }

    fun deskew(bitmap: Bitmap): Pair<Bitmap, Float> {
        val gray = grayArray(bitmap)
        val angle = estimateSkew(gray, bitmap.width, bitmap.height)
        if (abs(angle) < 0.3f) return bitmap to 0f
        // 校正后裁掉旋转产生的透明边
        val matrix = Matrix().apply { postRotate(-angle) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val cropped = trimBorders(rotated, matrix) ?: rotated
        if (cropped !== bitmap) bitmap.recycle()
        return cropped to angle
    }

    private fun trimBorders(rotated: Bitmap, matrix: Matrix): Bitmap? {
        val w = rotated.width
        val h = rotated.height
        val pixels = IntArray(w * h)
        rotated.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        var found = false
        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val p = pixels[y * w + x]
                val alpha = (p ushr 24) and 0xFF
                if (alpha > 200) {
                    val v = p and 0xFF
                    if (v < 245) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        found = true
                    }
                }
            }
        }
        if (!found || maxX - minX < w / 3 || maxY - minY < h / 3) return null
        val pad = 8
        val r = Rect((minX - pad).coerceAtLeast(0), (minY - pad).coerceAtLeast(0), (maxX + pad).coerceAtMost(w), (maxY + pad).coerceAtMost(h))
        return Bitmap.createBitmap(rotated, r.left, r.top, r.width(), r.height())
    }

    /** 将位图粘贴到白底画布，避免透明背景影响 OCR。 */
    fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888 && !bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun saveJpeg(bitmap: Bitmap, path: String, quality: Int = 88): Boolean = try {
        java.io.File(path).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        true
    } catch (t: Throwable) {
        false
    }

    /** 计算 tan，避免每像素重复调用。 */
    fun slope(degrees: Float): Float = tan(Math.toRadians(degrees.toDouble())).toFloat()
}
