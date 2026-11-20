package com.scoretrack.parser

import com.scoretrack.ocr.Box
import com.scoretrack.ocr.OcrPage
import com.scoretrack.ocr.TableCell
import com.scoretrack.ocr.TableData
import com.scoretrack.ocr.TextToken

/**
 * 把电子表格/CSV 的文本网格转换成与 OCR 结果一致的 TableData，
 * 这样表头识别与成绩解析逻辑只有一份实现。
 */
object SheetTableBuilder {

    private const val COL_WIDTH = 120f
    private const val ROW_HEIGHT = 32f

    fun toTableData(sheet: SheetData): TableData {
        val cells = ArrayList<TableCell>()
        val rowCount = sheet.rowCount
        val colCount = sheet.colCount.coerceAtLeast(1)
        for (r in 0 until rowCount) {
            val row = sheet.rows[r]
            for (c in 0 until colCount) {
                val text = row.getOrNull(c)?.trim() ?: ""
                if (text.isEmpty()) continue
                val box = Box(c * COL_WIDTH, r * ROW_HEIGHT, (c + 1) * COL_WIDTH, (r + 1) * ROW_HEIGHT)
                cells.add(
                    TableCell(
                        row = r, col = c, rowSpan = 1, colSpan = 1, box = box, text = text,
                        tokens = listOf(TextToken(text, box, null)),
                        confidence = null,
                        rowStart = r, rowEnd = r, colStart = c, colEnd = c,
                    )
                )
            }
        }
        val rowBounds = (0..rowCount).map { it * ROW_HEIGHT }
        val colBounds = (0..colCount).map { it * COL_WIDTH }
        return TableData(rowCount, colCount, cells, rowBounds, colBounds, hasGrid = true)
    }

    fun fakePage(sheet: SheetData, pageIndex: Int = 0): OcrPage = OcrPage(
        pageIndex = pageIndex,
        imageWidth = (sheet.colCount * COL_WIDTH).toInt(),
        imageHeight = (sheet.rowCount * ROW_HEIGHT).toInt(),
        lines = emptyList(),
        rawText = sheet.toText(),
        averageConfidence = null,
        sourceLabel = sheet.sheetName,
    )
}
