package com.scoretrack.ocr

import com.scoretrack.core.Subject
import com.scoretrack.core.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetReconstructorTest {

    private fun table(vararg rows: List<String>): TableData {
        val cols = rows.maxOf { it.size }
        val cells = ArrayList<TableCell>()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, text ->
                cells.add(
                    TableCell(
                        row = r,
                        col = c,
                        rowSpan = 1,
                        colSpan = 1,
                        box = Box(c * 100f, r * 40f, c * 100f + 95f, r * 40f + 35f),
                        text = text,
                        tokens = listOf(
                            TextToken(
                                text,
                                Box(c * 100f, r * 40f, c * 100f + 95f, r * 40f + 35f),
                                0.95f,
                            )
                        ),
                        confidence = 0.95f,
                    )
                )
            }
        }
        return TableData(
            rowCount = rows.size,
            colCount = cols,
            cells = cells,
            rowBounds = (0..rows.size).map { it * 40f },
            colBounds = (0..cols).map { it * 100f },
            hasGrid = true,
        )
    }

    private fun page() = OcrPage(
        pageIndex = 0,
        imageWidth = 1000,
        imageHeight = 600,
        lines = emptyList(),
        rawText = "",
        averageConfidence = 0.9f,
        sourceLabel = "test",
    )

    private val student = Student(id = "s1", name = "张三", className = "高二3班", studentNumber = "20260301")

    @Test
    fun `parses six subjects and scaled column`() {
        val t = table(
            listOf("姓名", "学号", "语文", "数学", "英语", "物理", "化学", "地理", "总分"),
            listOf("", "", "", "", "", "", "赋分", "赋分", ""),
            listOf("张三", "20260301", "125", "132", "128", "85", "88", "77", "635"),
            listOf("李四", "20260302", "118", "120", "110", "70", "80", "72", "570"),
        )
        val result = SheetReconstructor.parse(page(), t, student)
        assertEquals("warnings=" + result.warnings, 0, result.headerRow)
        assertEquals(2, result.rows.size)
        val row = result.rows.first { it.name == "张三" }
        assertEquals(125.0, row.scores[Subject.CHINESE])
        assertEquals(88.0, row.scores[Subject.CHEMISTRY])
        assertEquals(77.0, row.scores[Subject.GEOGRAPHY])
        assertEquals(0, result.matchedRowIndex)
    }

    @Test
    fun `missing score is not filled with zero`() {
        val t = table(
            listOf("姓名", "语文", "数学", "英语", "物理", "化学", "地理", "总分"),
            listOf("张三", "125", "", "128", "85", "88", "77", "503"),
        )
        val result = SheetReconstructor.parse(page(), t, student)
        val row = result.rows.single()
        assertEquals(125.0, row.scores[Subject.CHINESE])
        assertNull(row.scores[Subject.MATH])
        assertTrue(result.warnings.any { it.kind == WarningKind.MISSING_SCORE })
    }

    @Test
    fun `out of range score is rejected`() {
        val t = table(
            listOf("姓名", "语文", "数学", "英语", "物理", "化学", "地理"),
            listOf("张三", "250", "132", "128", "85", "88", "77"),
        )
        val result = SheetReconstructor.parse(page(), t, student)
        val row = result.rows.single()
        assertNull(row.scores[Subject.CHINESE])
        assertTrue(result.warnings.any { it.kind == WarningKind.OUT_OF_RANGE })
    }

    @Test
    fun `duplicate names are not merged and require confirmation`() {
        val t = table(
            listOf("姓名", "学号", "语文", "数学", "英语", "物理", "化学", "地理"),
            listOf("张三", "20260301", "125", "132", "128", "85", "88", "77"),
            listOf("张三", "20260399", "110", "120", "118", "75", "80", "70"),
        )
        val noNumber = Student(id = "s2", name = "张三", className = "", studentNumber = "")
        val result = SheetReconstructor.parse(page(), t, noNumber)
        assertEquals(2, result.candidates.size)
        assertNull(result.matchedRowIndex)
        assertTrue(result.warnings.any { it.kind == WarningKind.STUDENT_AMBIGUOUS })
    }

    @Test
    fun `student number disambiguates same name`() {
        val t = table(
            listOf("姓名", "学号", "语文", "数学", "英语", "物理", "化学", "地理"),
            listOf("张三", "20260301", "125", "132", "128", "85", "88", "77"),
            listOf("张三", "20260399", "110", "120", "118", "75", "80", "70"),
        )
        val result = SheetReconstructor.parse(page(), t, student)
        assertEquals(0, result.matchedRowIndex)
        assertEquals(125.0, result.rows[0].scores[Subject.CHINESE])
    }

    @Test
    fun `header not found reports warning`() {
        val t = table(
            listOf("说明", "文字"),
            listOf("没有表头", "1"),
        )
        val result = SheetReconstructor.parse(page(), t, student)
        assertTrue(result.warnings.any { it.kind == WarningKind.HEADER_NOT_FOUND })
        assertTrue(result.rows.isEmpty())
    }

    @Test
    fun `parse score normalizes common ocr noises`() {
        val warnings = ArrayList<ParseWarning>()
        assertEquals(125.0, SheetReconstructor.parseScore("l25", 150.0, Subject.CHINESE, warnings, 0))
        assertEquals(88.0, SheetReconstructor.parseScore("88分", 100.0, Subject.CHEMISTRY, warnings, 0))
        assertNull(SheetReconstructor.parseScore("", 100.0, Subject.PHYSICS, warnings, 0))
        assertNull(SheetReconstructor.parseScore("190", 150.0, Subject.CHINESE, warnings, 0))
    }
}
