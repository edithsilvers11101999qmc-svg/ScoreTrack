package com.scoretrack.ocr

import kotlin.math.abs

/**
 * 表格没有可见线框（打印模糊、拍照变形、纯文本导出）时的兜底：按文字行/列聚类恢复表格结构。
 */
object TextTableBuilder {

    fun build(page: OcrPage): TableData? {
        val tokens = page.lines.flatMap { line ->
            if (line.tokens.isNotEmpty()) line.tokens
            else listOf(TextToken(line.text, line.box, line.confidence))
        }.filter { it.text.isNotBlank() }
        if (tokens.size < 3) return null

        val heights = tokens.map { it.box.height }.filter { it > 1f }.sorted()
        val medianHeight = if (heights.isEmpty()) 20f else heights[heights.size / 2]

        val rows = clusterRows(tokens, medianHeight)
        if (rows.size < 2) return null

        val rowCells = rows.map { rowTokens ->
            splitCells(rowTokens, medianHeight)
        }

        val allCells = rowCells.flatten()
        val centers = allCells.map { it.box.centerX }.sorted()
        val colCenters = clusterCenters(centers, medianHeight * 1.1f)
        if (colCenters.isEmpty()) return null
        val colCount = colCenters.size
        val rowCount = rows.size
        if (rowCount > 200 || colCount > 40) return null

        val grid = HashMap<Long, MutableList<TableCell>>()
        rowCells.forEachIndexed { r, cells ->
            cells.forEach { cell ->
                val c = nearestIndex(colCenters, cell.box.centerX)
                grid.getOrPut(r * 100L + c) { ArrayList() }.add(cell)
            }
        }
        val out = ArrayList<TableCell>()
        for ((key, list) in grid) {
            val r = (key / 100).toInt()
            val c = (key % 100).toInt()
            val sorted = list.sortedBy { it.box.left }
            val box = sorted.map { it.box }.reduce { a, b -> a.union(b) }
            val text = sorted.joinToString(" ") { it.text }.trim()
            val conf = sorted.mapNotNull { it.confidence }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            out.add(
                TableCell(
                    row = r, col = c, rowSpan = 1, colSpan = 1, box = box, text = text,
                    tokens = sorted.flatMap { it.tokens }, confidence = conf,
                    rowStart = r, rowEnd = r, colStart = c, colEnd = c,
                )
            )
        }
        out.sortWith(compareBy({ it.row }, { it.col }))
        val rowBounds = (0..rowCount).map { i ->
            if (i < rowCount) rows[i].minOf { it.box.top } - medianHeight * 0.2f
            else rows.last().maxOf { it.box.bottom } + medianHeight * 0.2f
        }
        return TableData(rowCount, colCount, out, rowBounds, colCenters, hasGrid = false)
    }

    private fun clusterRows(tokens: List<TextToken>, medianHeight: Float): List<List<TextToken>> {
        val sorted = tokens.sortedBy { it.box.centerY }
        val rows = ArrayList<MutableList<TextToken>>()
        val rowCenters = ArrayList<Float>()
        for (token in sorted) {
            var placed = false
            for (i in rows.indices) {
                val cy = rowCenters[i]
                if (abs(token.box.centerY - cy) <= medianHeight * 0.65f) {
                    rows[i].add(token)
                    rowCenters[i] = (cy * (rows[i].size - 1) + token.box.centerY) / rows[i].size
                    placed = true
                    break
                }
            }
            if (!placed) {
                rows.add(mutableListOf(token))
                rowCenters.add(token.box.centerY)
            }
        }
        return rows.map { it.sortedBy { t -> t.box.left } }
    }

    private fun splitCells(rowTokens: List<TextToken>, medianHeight: Float): List<TableCell> {
        if (rowTokens.isEmpty()) return emptyList()
        val cells = ArrayList<TableCell>()
        var current = mutableListOf(rowTokens.first())
        for (i in 1 until rowTokens.size) {
            val prev = rowTokens[i - 1]
            val cur = rowTokens[i]
            val gap = cur.box.left - prev.box.right
            val threshold = (medianHeight * 1.35f).coerceAtLeast(10f)
            if (gap > threshold) {
                cells.add(makeCell(cells.size, current))
                current = mutableListOf(cur)
            } else {
                current.add(cur)
            }
        }
        cells.add(makeCell(cells.size, current))
        return cells
    }

    private fun makeCell(col: Int, tokens: List<TextToken>): TableCell {
        val box = tokens.map { it.box }.reduce { a, b -> a.union(b) }
        val conf = tokens.mapNotNull { it.confidence }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        return TableCell(
            row = 0, col = col, rowSpan = 1, colSpan = 1, box = box,
            text = tokens.joinToString(" ") { it.text }.trim(),
            tokens = tokens, confidence = conf, rowStart = 0, rowEnd = 0, colStart = col, colEnd = col,
        )
    }

    private fun clusterCenters(sorted: List<Float>, tolerance: Float): List<Float> {
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<Float>()
        var sum = 0.0
        var count = 0
        var last = sorted.first()
        for (v in sorted) {
            if (v - last > tolerance && count > 0) {
                out.add((sum / count).toFloat())
                sum = 0.0
                count = 0
            }
            sum += v
            count++
            last = v
        }
        if (count > 0) out.add((sum / count).toFloat())
        return out
    }

    private fun nearestIndex(centers: List<Float>, x: Float): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        centers.forEachIndexed { i, c ->
            val d = abs(c - x)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }
}
