package com.scoretrack.ocr

/** 浮点矩形（像素坐标），与 OCR 结果保持一致。 */
data class Box(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom

    fun overlapRatio(other: Box): Float {
        val w = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
        val h = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        val inter = w * h
        val minArea = minOf(width * height, other.width * other.height)
        return if (minArea <= 0f) 0f else inter / minArea
    }

    fun union(other: Box): Box = Box(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )

    companion object {
        val EMPTY = Box(0f, 0f, 0f, 0f)
    }
}

/** OCR 识别出的一段文字及其位置、置信度。 */
data class TextToken(
    val text: String,
    val box: Box,
    val confidence: Float?,
)

/** OCR 的一行。 */
data class OcrLine(
    val text: String,
    val box: Box,
    val tokens: List<TextToken>,
    val confidence: Float?,
)

/** 一张图片（或 PDF 的一页）的识别结果。 */
data class OcrPage(
    val pageIndex: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val lines: List<OcrLine>,
    val rawText: String,
    val averageConfidence: Float?,
    val sourceLabel: String,
)

/** 表格单元格（已处理合并）。 */
data class TableCell(
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val box: Box,
    val text: String,
    val tokens: List<TextToken>,
    val confidence: Float?,
    val rowStart: Int = row,
    val rowEnd: Int = row,
    val colStart: Int = col,
    val colEnd: Int = col,
)

/** 由图像线框或文本聚类恢复出的二维表格。 */
data class TableData(
    val rowCount: Int,
    val colCount: Int,
    val cells: List<TableCell>,
    val rowBounds: List<Float>,
    val colBounds: List<Float>,
    val hasGrid: Boolean,
) {
    fun cellAt(row: Int, col: Int): TableCell? =
        cells.firstOrNull { row in it.rowStart..it.rowEnd && col in it.colStart..it.colEnd }

    fun rowCells(row: Int): List<TableCell> =
        cells.filter { row in it.rowStart..it.rowEnd }.sortedBy { it.colStart }

    fun rowText(row: Int): String = rowCells(row).joinToString(" | ") { it.text }
}
