package com.scoretrack.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 从二值图像中检测表格横线/竖线，恢复网格，并处理合并单元格。
 */
object GridDetector {

    data class HLine(val y: Int, val x1: Int, val x2: Int, val thickness: Int)
    data class VLine(val x: Int, val y1: Int, val y2: Int, val thickness: Int)
    data class Lines(val horizontals: List<HLine>, val verticals: List<VLine>)

    private data class RawRun(val pos: Int, val start: Int, val end: Int)

    fun detect(binary: BooleanArray, w: Int, h: Int): Lines {
        val minLenH = max(40, (w * 0.12f).toInt())
        val minLenV = max(30, (h * 0.08f).toInt())
        val runsH = ArrayList<RawRun>()
        val runsV = ArrayList<RawRun>()

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                if (!binary[y * w + x]) { x++; continue }
                var start = x
                var end = x
                var gap = 0
                var i = x + 1
                while (i < w) {
                    if (binary[y * w + i]) {
                        end = i
                        gap = 0
                    } else {
                        gap++
                        if (gap > 2) break
                    }
                    i++
                }
                if (end - start + 1 >= minLenH) runsH.add(RawRun(y, start, end))
                x = max(i, start + 1)
            }
            y += 1
        }

        var x = 0
        while (x < w) {
            var yy = 0
            while (yy < h) {
                if (!binary[yy * w + x]) { yy++; continue }
                var start = yy
                var end = yy
                var gap = 0
                var i = yy + 1
                while (i < h) {
                    if (binary[i * w + x]) {
                        end = i
                        gap = 0
                    } else {
                        gap++
                        if (gap > 2) break
                    }
                    i++
                }
                if (end - start + 1 >= minLenV) runsV.add(RawRun(x, start, end))
                yy = max(i, start + 1)
            }
            x += 1
        }

        return Lines(
            groupRuns(runsH, horizontal = true, minSpan = (w * 0.18f).toInt()).filterIsInstance<HLine>(),
            groupRuns(runsV, horizontal = false, minSpan = (h * 0.10f).toInt()).filterIsInstance<VLine>(),
        )
    }

    private fun groupRuns(runs: List<RawRun>, horizontal: Boolean, minSpan: Int): List<Any> {
        val parent = IntArray(runs.size) { it }
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        for (i in runs.indices) {
            for (j in i + 1 until runs.size) {
                val a = runs[i]
                val b = runs[j]
                if (abs(a.pos - b.pos) > 3) continue
                val overlap = min(a.end, b.end) - max(a.start, b.start)
                if (overlap >= -12) union(i, j)
            }
        }
        val groups = HashMap<Int, MutableList<RawRun>>()
        for (i in runs.indices) groups.getOrPut(find(i)) { ArrayList() }.add(runs[i])
        val out = ArrayList<Any>()
        for ((_, g) in groups) {
            val pos = g.map { it.pos }.sorted()[g.size / 2]
            val start = g.minOf { it.start }
            val end = g.maxOf { it.end }
            if (end - start < minSpan) continue
            if (horizontal) {
                out.add(HLine(pos, start, end, g.size))
            } else {
                out.add(VLine(pos, start, end, g.size))
            }
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    fun buildTable(lines: Lines, tokens: List<TextToken>, w: Int, h: Int): TableData? {
        val hs = (lines.horizontals as List<HLine>).sortedBy { it.y }
        val vs = (lines.verticals as List<VLine>).sortedBy { it.x }
        if (hs.size < 3 || vs.size < 3) return null

        val ys = clusterPositions(hs.map { it.y }.toIntArray(), 8f).toMutableList()
        val xs = clusterPositions(vs.map { it.x }.toIntArray(), 8f).toMutableList()
        if (ys.first() > h * 0.02f) ys.add(0, 0f)
        if (ys.last() < h * 0.98f) ys.add(h - 1f)
        if (xs.first() > w * 0.02f) xs.add(0, 0f)
        if (xs.last() < w * 0.98f) xs.add(w - 1f)
        val rows = ys.size - 1
        val cols = xs.size - 1
        if (rows < 2 || cols < 2 || rows > 200 || cols > 40) return null

        val parent = IntArray(rows * cols) { it }
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        for (r in 0 until rows) {
            for (c in 0 until cols - 1) {
                val x = xs[c + 1]
                val y1 = ys[r]
                val y2 = ys[r + 1]
                if (!hasVerticalDivider(vs, x, y1, y2)) union(r * cols + c, r * cols + c + 1)
            }
        }
        for (r in 0 until rows - 1) {
            for (c in 0 until cols) {
                val y = ys[r + 1]
                val x1 = xs[c]
                val x2 = xs[c + 1]
                if (!hasHorizontalDivider(hs, y, x1, x2)) union(r * cols + c, (r + 1) * cols + c)
            }
        }

        val groups = HashMap<Int, MutableList<Int>>()
        for (i in 0 until rows * cols) groups.getOrPut(find(i)) { ArrayList() }.add(i)

        val cells = ArrayList<TableCell>(groups.size)
        for ((_, members) in groups) {
            val rr = members.map { it / cols }
            val cc = members.map { it % cols }
            val r0 = rr.min(); val r1 = rr.max()
            val c0 = cc.min(); val c1 = cc.max()
            val box = Box(xs[c0], ys[r0], xs[c1 + 1], ys[r1 + 1])
            val inside = tokens.filter { box.contains(it.box.centerX, it.box.centerY) }
                .sortedWith(compareBy({ it.box.top }, { it.box.left }))
            val text = inside.joinToString(" ") { it.text }.trim()
            val conf = inside.mapNotNull { it.confidence }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
            cells.add(
                TableCell(
                    row = r0, col = c0, rowSpan = r1 - r0 + 1, colSpan = c1 - c0 + 1,
                    box = box, text = text, tokens = inside, confidence = conf,
                    rowStart = r0, rowEnd = r1, colStart = c0, colEnd = c1,
                )
            )
        }
        cells.sortWith(compareBy({ it.rowStart }, { it.colStart }))
        return TableData(rows, cols, cells, ys, xs, hasGrid = true)
    }

    private fun clusterPositions(values: IntArray, tolerance: Float): List<Float> {
        if (values.isEmpty()) return emptyList()
        val sorted = values.sorted()
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

    private fun hasVerticalDivider(vs: List<VLine>, x: Float, y1: Float, y2: Float): Boolean {
        val span = (y2 - y1).coerceAtLeast(1f)
        return vs.any { line ->
            abs(line.x - x) <= 7f &&
                (min(line.y2.toFloat(), y2) - max(line.y1.toFloat(), y1)).coerceAtLeast(0f) / span >= 0.55f
        }
    }

    private fun hasHorizontalDivider(hs: List<HLine>, y: Float, x1: Float, x2: Float): Boolean {
        val span = (x2 - x1).coerceAtLeast(1f)
        return hs.any { line ->
            abs(line.y - y) <= 7f &&
                (min(line.x2.toFloat(), x2) - max(line.x1.toFloat(), x1)).coerceAtLeast(0f) / span >= 0.55f
        }
    }
}
