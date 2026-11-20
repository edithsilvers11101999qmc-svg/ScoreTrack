package com.scoretrack.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import com.scoretrack.core.formatRate
import com.scoretrack.core.formatScore
import com.scoretrack.domain.analytics.AnalysisResult
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * PDF 报告导出。基于系统 PdfDocument 绘制，中文使用系统字体，
 * 真实排版（标题 / 小节 / 表格 / 自动分页），不是把界面截图贴进 PDF。
 */
object PdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val SCALE = 2f

    class Styles {
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK
        }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.rgb(31, 95, 75)
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f; color = Color.rgb(33, 33, 33)
        }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f; color = Color.rgb(110, 110, 110)
        }
        val tableHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.BLACK
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 0.6f; color = Color.rgb(200, 200, 200)
        }
    }

    private class Cursor(var document: PdfDocument, val pageW: Int, val pageH: Int) {
        var page: PdfDocument.Page = document.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        var pageNumber = 1
        var y = MARGIN + 8f
        val canvas: Canvas get() = page.canvas
    }

    fun analysisReport(
        studentName: String,
        className: String,
        exams: List<ExamRecord>,
        analysis: AnalysisResult,
    ): ByteArray {
        val document = PdfDocument()
        val cursor = Cursor(document, (PAGE_W * SCALE).toInt(), (PAGE_H * SCALE).toInt())
        cursor.canvas.scale(SCALE, SCALE)

        val s = Styles()
        drawHeader(cursor, s, "成绩分析报告", listOf(
            "学生：$studentName" + if (className.isNotBlank()) "（$className）" else "",
            "考试次数：${exams.size} 次 · 生成时间：${nowText()}",
            "科目满分：语文 150 / 数学 150 / 英语 150 / 物理 100 / 化学(赋分) 100 / 地理(赋分) 100 · 总分 750",
        ))

        section(cursor, s, "一、考试记录")
        val headers = listOf("考试名称", "日期") + Subject.displayOrder().map { it.cnName } + listOf("总分")
        val widths = mutableListOf(118f, 58f)
        Subject.displayOrder().forEach { widths.add(40f) }
        widths.add(44f)
        tableHeader(cursor, s, headers, widths)
        exams.forEachIndexed { index, exam ->
            val cells = mutableListOf(exam.name, exam.dateText)
            Subject.displayOrder().forEach { subject ->
                cells.add(exam.scores[subject]?.let { formatScore(it) } ?: "—")
            }
            cells.add(exam.total?.let { formatScore(it) } ?: "—")
            tableRow(cursor, s, cells, widths, shaded = index % 2 == 1)
        }

        section(cursor, s, "二、综合统计")
        val overall = analysis.overall
        body(cursor, s, "总分平均：${overall.average?.let { formatScore(it) } ?: "数据不足"}    " +
            "总分最高：${overall.best?.let { formatScore(it) } ?: "数据不足"}    " +
            "最近总分：${overall.latest?.let { formatScore(it) } ?: "数据不足"}")
        body(cursor, s, "最近一次总分变化：${overall.latestDelta?.let { formatSigned(it) } ?: "数据不足"}    " +
            "趋势判断：${overall.trend.label}")

        section(cursor, s, "三、六科数据")
        val subjectHeaders = listOf("科目", "当前", "最高", "平均", "得分率", "最近变化", "趋势")
        val subjectWidths = listOf(70f, 55f, 55f, 55f, 70f, 80f, 80f)
        tableHeader(cursor, s, subjectHeaders, subjectWidths)
        analysis.subjects.forEachIndexed { index, stat ->
            val cells = listOf(
                stat.subject.cnName,
                stat.current?.let { formatScore(it) } ?: "—",
                stat.best?.let { formatScore(it) } ?: "—",
                stat.average?.let { formatScore(kotlin.math.round(it * 10) / 10.0) } ?: "—",
                stat.averageRate?.let { formatRate(it) } ?: "—",
                stat.latestDelta?.let { formatSigned(it) } ?: "数据不足",
                stat.trend.label,
            )
            tableRow(cursor, s, cells, subjectWidths, shaded = index % 2 == 1)
        }

        if (analysis.strengths.isNotEmpty() || analysis.weaknesses.isNotEmpty()) {
            section(cursor, s, "四、优势与薄弱科目（按平均得分率）")
            if (analysis.strengths.isNotEmpty()) {
                body(cursor, s, "相对优势：${analysis.strengths.joinToString("、") { "${it.subject.cnName}（${it.rateText}）" }}")
            }
            if (analysis.weaknesses.isNotEmpty()) {
                body(cursor, s, "相对薄弱：${analysis.weaknesses.joinToString("、") { "${it.subject.cnName}（${it.rateText}）" }}")
            }
        }

        if (analysis.notes.isNotEmpty()) {
            section(cursor, s, "五、数据说明")
            analysis.notes.forEach { body(cursor, s, "· $it") }
        }

        return finish(document, cursor, s)
    }

    fun aiReport(
        title: String,
        studentName: String,
        examRange: String,
        content: String,
        meta: String,
    ): ByteArray {
        val document = PdfDocument()
        val cursor = Cursor(document, (PAGE_W * SCALE).toInt(), (PAGE_H * SCALE).toInt())
        cursor.canvas.scale(SCALE, SCALE)
        val s = Styles()
        drawHeader(cursor, s, title, listOf("学生：$studentName", examRange, meta))
        cursor.y += 6f

        content.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> cursor.y += 5f
                line.startsWith("### ") -> heading(cursor, s, cleanInline(line.removePrefix("### ")), 11.5f)
                line.startsWith("## ") -> heading(cursor, s, cleanInline(line.removePrefix("## ")), 13f)
                line.startsWith("# ") -> heading(cursor, s, cleanInline(line.removePrefix("# ")), 14f)
                line.startsWith("- ") || line.startsWith("* ") ->
                    paragraph(cursor, s, "• " + cleanInline(line.substring(2)), indent = 8f)
                line.matches(Regex("^\\d+[.、]\\s*.*")) ->
                    paragraph(cursor, s, cleanInline(line), indent = 8f)
                else -> paragraph(cursor, s, cleanInline(line), indent = 0f)
            }
        }
        return finish(document, cursor, s)
    }

    // ------------------------------------------------------------------ draw

    private fun drawHeader(cursor: Cursor, s: Styles, title: String, meta: List<String>) {
        val canvas = cursor.canvas
        canvas.drawColor(Color.WHITE)
        s.title.let { canvas.drawText(title, MARGIN, cursor.y + 14f, it) }
        cursor.y += 30f
        s.heading.let {
            canvas.drawRect(MARGIN, cursor.y - 10f, MARGIN + 46f, cursor.y - 7f, Paint().apply { color = Color.rgb(31, 95, 75) })
        }
        meta.forEach { line ->
            cursor.y += 13f
            canvas.drawText(line, MARGIN, cursor.y, s.muted)
        }
        cursor.y += 10f
        canvas.drawLine(MARGIN, cursor.y, PAGE_W - MARGIN, cursor.y, s.line)
        cursor.y += 16f
    }

    private fun section(cursor: Cursor, s: Styles, title: String) {
        ensureSpace(cursor, s, 42f)
        cursor.y += 8f
        cursor.canvas.drawText(title, MARGIN, cursor.y, s.heading)
        cursor.y += 8f
        cursor.canvas.drawLine(MARGIN, cursor.y, PAGE_W - MARGIN, cursor.y, s.line)
        cursor.y += 14f
    }

    private fun heading(cursor: Cursor, s: Styles, text: String, size: Float) {
        ensureSpace(cursor, s, 26f)
        cursor.y += 10f
        s.heading.textSize = size
        cursor.canvas.drawText(text, MARGIN, cursor.y, s.heading)
        s.heading.textSize = 14f
        cursor.y += 14f
    }

    private fun body(cursor: Cursor, s: Styles, text: String) {
        paragraph(cursor, s, text, indent = 0f)
    }

    private fun paragraph(cursor: Cursor, s: Styles, text: String, indent: Float) {
        val paint = s.body
        val maxWidth = PAGE_W - MARGIN * 2 - indent
        if (maxWidth <= 10f) return
        val chars = text.toCharArray()
        var start = 0
        while (start < chars.size) {
            ensureSpace(cursor, s, 16f)
            val count = paint.breakText(chars, start, chars.size - start, maxWidth, null)
            if (count <= 0) break
            val line = String(chars, start, count)
            cursor.y += 14f
            cursor.canvas.drawText(line, MARGIN + indent, cursor.y, paint)
            start += count
            if (start < chars.size && chars[start] == '\n') start++
        }
        cursor.y += 3f
    }

    private fun tableHeader(cursor: Cursor, s: Styles, cells: List<String>, widths: List<Float>) {
        ensureSpace(cursor, s, 22f)
        var x = MARGIN
        cells.forEachIndexed { index, cell ->
            cursor.canvas.drawText(cell, x + 3f, cursor.y + 11f, s.tableHeader)
            x += widths.getOrElse(index) { 40f }
        }
        cursor.y += 15f
        cursor.canvas.drawLine(MARGIN, cursor.y, MARGIN + widths.sum(), cursor.y, s.line)
        cursor.y += 3f
    }

    private fun tableRow(cursor: Cursor, s: Styles, cells: List<String>, widths: List<Float>, shaded: Boolean) {
        ensureSpace(cursor, s, 18f)
        if (shaded) {
            cursor.canvas.drawRect(
                MARGIN, cursor.y - 1f, MARGIN + widths.sum(), cursor.y + 13f,
                Paint().apply { color = Color.rgb(246, 248, 247) }
            )
        }
        var x = MARGIN
        cells.forEachIndexed { index, cell ->
            val width = widths.getOrElse(index) { 40f }
            val paint = s.body
            val fitted = fit(paint, cell, width - 6f)
            cursor.canvas.drawText(fitted, x + 3f, cursor.y + 10f, paint)
            x += width
        }
        cursor.y += 14f
    }

    private fun fit(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }

    private fun ensureSpace(cursor: Cursor, s: Styles, needed: Float) {
        if (cursor.y + needed < PAGE_H - MARGIN) return
        cursor.canvas.drawText(
            "第 ${cursor.pageNumber} 页 · ScoreTrack", MARGIN, PAGE_H - MARGIN + 12f, s.muted
        )
        cursor.document.finishPage(cursor.page)
        cursor.pageNumber++
        cursor.page = cursor.document.startPage(
            PdfDocument.PageInfo.Builder(cursor.pageW, cursor.pageH, cursor.pageNumber).create()
        )
        cursor.page.canvas.scale(SCALE, SCALE)
        cursor.page.canvas.drawColor(Color.WHITE)
        cursor.y = MARGIN
    }

    private fun finish(document: PdfDocument, cursor: Cursor, s: Styles): ByteArray {
        cursor.canvas.drawText(
            "第 ${cursor.pageNumber} 页 · ScoreTrack", MARGIN, PAGE_H - MARGIN + 12f, s.muted
        )
        document.finishPage(cursor.page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun cleanInline(text: String): String = text
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .trim()

    private fun formatSigned(value: Double): String =
        (if (value > 0) "+" else "") + formatScore(value)

    private fun nowText(): String =
        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    @Suppress("unused")
    private fun maxOf(a: Float, b: Float): Float = max(a, b)
}
