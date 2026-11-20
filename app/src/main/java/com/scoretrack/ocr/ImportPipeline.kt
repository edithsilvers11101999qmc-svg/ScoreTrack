package com.scoretrack.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.scoretrack.core.Subject
import com.scoretrack.core.Student
import com.scoretrack.parser.CsvParser
import com.scoretrack.parser.PdfPages
import com.scoretrack.parser.SheetData
import com.scoretrack.parser.SheetTableBuilder
import com.scoretrack.parser.XlsParser
import com.scoretrack.parser.XlsxParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PreprocessOptions(
    val deskew: Boolean = true,
    val enhance: Boolean = true,
    val perspective: Boolean = true,
)

data class ImportSource(
    val uriString: String,
    val fileName: String,
    val mimeType: String,
    val kind: String,
    val pageCount: Int,
)

data class PageOutcome(
    val pageIndex: Int,
    val label: String,
    val previewPath: String?,
    val ocrText: String,
    val parse: SheetParseResult?,
    val rows: List<RecognizedRow>,
    val warnings: List<ParseWarning>,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotation: Float,
    val perspectiveApplied: Boolean,
)

data class ImportOutcome(
    val pages: List<PageOutcome>,
    val rows: List<RecognizedRow>,
    val matched: RecognizedRow?,
    val candidates: List<RecognizedRow>,
    val warnings: List<ParseWarning>,
    val needsManualSelection: Boolean,
    val sources: List<ImportSource>,
    val previewPath: String?,
    val failure: String? = null,
)

/**
 * 导入流水线：文件/图片 -> 预处理 -> OCR -> 表格结构恢复 -> 表头绑定 -> 学生匹配。
 * 只输出候选数据，写入数据库必须经过用户确认。
 */
class ImportPipeline(
    private val context: Context,
    private val ocrEngine: OcrEngine,
) {

    suspend fun process(
        uris: List<Uri>,
        student: Student?,
        options: PreprocessOptions = PreprocessOptions(),
        onProgress: (Float, String) -> Unit = { _, _ -> },
    ): ImportOutcome = withContext(Dispatchers.Default) {
        val pages = ArrayList<PageOutcome>()
        val sources = ArrayList<ImportSource>()
        val allWarnings = ArrayList<ParseWarning>()
        var failure: String? = null
        var pageCounter = 0

        for ((fileIndex, uri) in uris.withIndex()) {
            if (failure != null) break
            val info = queryFileInfo(uri)
            val kind = detectKind(uri, info.mimeType, info.fileName)
            val baseProgress = fileIndex.toFloat() / uris.size.coerceAtLeast(1)
            onProgress(baseProgress, "正在读取 ${info.fileName}")

            when (kind) {
                "IMAGE" -> {
                    val result = processImage(uri, pageCounter, info.fileName, options)
                    if (result == null) {
                        failure = "无法解码图片：${info.fileName}"
                    } else {
                        pages.add(result.copy(pageIndex = pageCounter))
                        allWarnings.addAll(result.warnings)
                        pageCounter++
                    }
                    sources.add(ImportSource(uri.toString(), info.fileName, info.mimeType, kind, 1))
                }
                "PDF" -> {
                    val cache = PdfPages.copyToCache(context, uri, "import_${System.currentTimeMillis()}.pdf")
                    val count = PdfPages.pageCount(cache)
                    if (count == 0) {
                        failure = "PDF 无法读取或已加密：${info.fileName}"
                    } else {
                        val limit = minOf(count, 30)
                        sources.add(ImportSource(uri.toString(), info.fileName, info.mimeType, kind, count))
                        for (p in 0 until limit) {
                            onProgress(baseProgress + p.toFloat() / limit / uris.size, "正在识别 PDF 第 ${p + 1}/$count 页")
                            val bitmap = PdfPages.renderPage(cache, p)
                            if (bitmap == null) {
                                allWarnings.add(ParseWarning(WarningKind.NO_TABLE, "第 ${p + 1} 页渲染失败"))
                                continue
                            }
                            val result = processBitmap(bitmap, pageCounter, "${info.fileName} 第${p + 1}页", options)
                            pages.add(result.copy(pageIndex = pageCounter))
                            allWarnings.addAll(result.warnings)
                            pageCounter++
                        }
                    }
                    cache.delete()
                }
                "XLSX", "XLS", "CSV" -> {
                    val cache = copyToCache(context, uri, info.fileName)
                    if (cache == null) {
                        failure = "无法读取文件：${info.fileName}"
                    } else {
                        val sheets = try {
                            when (kind) {
                                "XLSX" -> XlsxParser.parse(cache)
                                "XLS" -> XlsParser.parse(cache)
                                else -> listOf(CsvParser.parse(cache))
                            }
                        } catch (t: Throwable) {
                            failure = "解析 ${info.fileName} 失败：${t.message ?: t.javaClass.simpleName}"
                            emptyList()
                        }
                        if (failure == null && sheets.isEmpty()) failure = "文件中没有可读取的工作表：${info.fileName}"
                        for (sheet in sheets) {
                            val label = "${info.fileName} · ${sheet.sheetName}"
                            val page = processSheet(sheet, pageCounter, label)
                            pages.add(page.copy(pageIndex = pageCounter))
                            allWarnings.addAll(page.warnings)
                            pageCounter++
                        }
                        sources.add(ImportSource(uri.toString(), info.fileName, info.mimeType, kind, sheets.size.coerceAtLeast(1)))
                        cache.delete()
                    }
                }
                else -> failure = "不支持的文件类型：${info.fileName}"
            }
            onProgress((fileIndex + 1).toFloat() / uris.size, "处理完成 ${fileIndex + 1}/${uris.size}")
        }

        // 统一重编号，跨页/跨文件保持唯一行号
        var global = 0
        val rows = ArrayList<RecognizedRow>()
        for (page in pages) {
            for (row in page.rows) {
                rows.add(row.copy(rowIndex = global++))
            }
        }

        val matchWarnings = ArrayList<ParseWarning>()
        val match = if (student != null) SheetReconstructor.matchStudent(rows, student, matchWarnings) else null
        allWarnings.addAll(matchWarnings)
        val matchedRow = match?.rowIndex?.let { idx -> rows.firstOrNull { it.rowIndex == idx } }
        val candidates = match?.candidates?.mapNotNull { idx -> rows.firstOrNull { it.rowIndex == idx } } ?: emptyList()

        ImportOutcome(
            pages = pages,
            rows = rows,
            matched = matchedRow,
            candidates = candidates,
            warnings = allWarnings,
            needsManualSelection = matchedRow == null,
            sources = sources,
            previewPath = pages.firstOrNull { it.previewPath != null }?.previewPath,
            failure = failure,
        )
    }

    private suspend fun processImage(
        uri: Uri,
        pageIndex: Int,
        label: String,
        options: PreprocessOptions,
    ): PageOutcome? {
        val bitmap = BitmapOps.decode(context, uri) ?: return null
        return processBitmap(bitmap, pageIndex, label, options)
    }

    private suspend fun processBitmap(
        decoded: Bitmap,
        pageIndex: Int,
        label: String,
        options: PreprocessOptions,
    ): PageOutcome {
        var bitmap = BitmapOps.flattenOnWhite(decoded)
        if (options.enhance) {
            val enhanced = BitmapOps.enhance(bitmap)
            if (enhanced !== bitmap) bitmap.recycle()
            bitmap = enhanced
        }
        var rotation = 0f
        if (options.deskew) {
            val (deskewed, angle) = BitmapOps.deskew(bitmap)
            bitmap = deskewed
            rotation = angle
        }

        var binary = BitmapOps.binarize(BitmapOps.grayArray(bitmap), bitmap.width, bitmap.height)
        var lines = GridDetector.detect(binary, bitmap.width, bitmap.height)
        var perspectiveApplied = false
        if (options.perspective) {
            val corrected = PerspectiveCorrector.correct(bitmap, lines, bitmap.width, bitmap.height)
            if (corrected.applied) {
                if (corrected.bitmap !== bitmap) bitmap.recycle()
                bitmap = corrected.bitmap
                perspectiveApplied = true
                binary = BitmapOps.binarize(BitmapOps.grayArray(bitmap), bitmap.width, bitmap.height)
                lines = GridDetector.detect(binary, bitmap.width, bitmap.height)
            }
        }

        val previewDir = File(context.cacheDir, "ocr_preview").apply { mkdirs() }
        val previewFile = File(previewDir, "page_${pageIndex}_${System.currentTimeMillis()}.jpg")
        BitmapOps.saveJpeg(bitmap, previewFile.absolutePath, 85)
        val previewPath: String? = if (previewFile.exists()) previewFile.absolutePath else null

        val page = ocrEngine.recognize(bitmap, pageIndex, label)
        val gridTable = GridDetector.buildTable(lines, page.lines.flatMap { it.tokens }, bitmap.width, bitmap.height)
        val table = gridTable ?: TextTableBuilder.build(page)
        val parse = SheetReconstructor.parse(page, table, student = null, pageIndex = pageIndex, sourceLabel = label)
        val warnings = ArrayList<ParseWarning>()
        if (gridTable == null && table != null) {
            warnings.add(ParseWarning(WarningKind.PARSER_FALLBACK, "未检测到清晰表格线，已按文字行列位置重建表格，请核对"))
        }
        warnings.addAll(parse.warnings)
        if (page.averageConfidence != null && page.averageConfidence!! < 0.5f) {
            warnings.add(ParseWarning(WarningKind.LOW_CONFIDENCE, "本页整体识别置信度偏低（${"%.2f".format(page.averageConfidence)}），建议重拍或人工核对"))
        }
        return PageOutcome(
            pageIndex = pageIndex,
            label = label,
            previewPath = previewPath,
            ocrText = page.rawText,
            parse = parse,
            rows = parse.rows,
            warnings = warnings,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            rotation = rotation,
            perspectiveApplied = perspectiveApplied,
        )
    }

    private fun processSheet(sheet: SheetData, pageIndex: Int, label: String): PageOutcome {
        val table = SheetTableBuilder.toTableData(sheet)
        val page = SheetTableBuilder.fakePage(sheet, pageIndex)
        val parse = SheetReconstructor.parse(page, table, student = null, pageIndex = pageIndex, sourceLabel = label)
        return PageOutcome(
            pageIndex = pageIndex,
            label = label,
            previewPath = null,
            ocrText = sheet.toText(),
            parse = parse,
            rows = parse.rows,
            warnings = parse.warnings,
            imageWidth = table.colBounds.lastOrNull()?.toInt() ?: 0,
            imageHeight = table.rowBounds.lastOrNull()?.toInt() ?: 0,
            rotation = 0f,
            perspectiveApplied = false,
        )
    }

    /** 局部重识别：对指定区域放大后重新 OCR，用于用户核对低置信度字段。 */
    suspend fun recognizeRegion(imagePath: String, left: Int, top: Int, right: Int, bottom: Int): String? =
        withContext(Dispatchers.Default) {
            val bitmap = BitmapOps.decodeFile(imagePath) ?: return@withContext null
            val padX = ((right - left) * 0.15f).toInt().coerceAtLeast(4)
            val padY = ((bottom - top) * 0.25f).toInt().coerceAtLeast(4)
            val rect = android.graphics.Rect(
                (left - padX).coerceAtLeast(0),
                (top - padY).coerceAtLeast(0),
                (right + padX).coerceAtMost(bitmap.width),
                (bottom + padY).coerceAtMost(bitmap.height),
            )
            val crop = BitmapOps.crop(bitmap, rect)
            val scaled = BitmapOps.scale(crop, 2f)
            val page = ocrEngine.recognize(scaled, 0, "region")
            page.lines.joinToString(" ") { it.text }.trim().ifBlank { null }
        }

    // -------------------------------------------------------------- helpers

    data class FileInfo(val fileName: String, val mimeType: String)

    private fun queryFileInfo(uri: Uri): FileInfo {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "文件"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex) ?: name
            }
        }
        val mime = context.contentResolver.getType(uri) ?: guessMime(name)
        return FileInfo(name, mime)
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls" -> "application/vnd.ms-excel"
        "csv" -> "text/csv"
        else -> "application/octet-stream"
    }

    private fun detectKind(uri: Uri, mime: String, name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> "IMAGE"
            mime == "application/pdf" || ext == "pdf" -> "PDF"
            ext == "xlsx" || mime.contains("spreadsheetml") -> "XLSX"
            ext == "xls" || mime.contains("ms-excel") -> "XLS"
            ext == "csv" || mime.contains("csv") || mime.startsWith("text/") -> "CSV"
            else -> "UNKNOWN"
        }
    }

    private fun copyToCache(context: Context, uri: Uri, fileName: String): File? = try {
        val dir = File(context.cacheDir, "import").apply { mkdirs() }
        val safe = fileName.replace(Regex("[^\\w.\\-\\u4e00-\\u9fa5]"), "_").takeLast(64)
        val target = File(dir, "${System.currentTimeMillis()}_$safe")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        if (target.exists() && target.length() > 0) target else null
    } catch (t: Throwable) {
        null
    }
}
