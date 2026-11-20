package com.scoretrack.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** PDF：逐页渲染为位图后交给 OCR（Android 无本地 PDF 文本抽取 API）。 */
object PdfPages {

    fun copyToCache(context: Context, uri: Uri, name: String = "import.pdf"): File {
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val target = File(dir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        return target
    }

    fun pageCount(file: File): Int = open(file) { it.pageCount } ?: 0

    fun renderPage(file: File, index: Int, maxDimension: Int = 2400): Bitmap? = open(file) { renderer ->
        if (index < 0 || index >= renderer.pageCount) return@open null
        renderer.openPage(index).use { page ->
            val ratio = page.width.toFloat() / page.height.toFloat()
            var width = page.width
            var height = page.height
            val longest = maxOf(width, height)
            if (longest > maxDimension) {
                val scale = maxDimension.toFloat() / longest
                width = (page.width * scale).toInt().coerceAtLeast(1)
                height = (page.height * scale).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    private fun <T> open(file: File, block: (PdfRenderer) -> T): T? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> block(renderer) }
        }
    } catch (t: Throwable) {
        null
    }
}
