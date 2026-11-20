package com.scoretrack.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun `parses comma separated with header`() {
        val text = "姓名,语文,数学\n张三,125,132\n李四,118,120\n"
        val sheet = CsvParser.parseText(text)
        assertEquals(3, sheet.rowCount)
        assertEquals(3, sheet.colCount)
        assertEquals("姓名", sheet.cell(0, 0))
        assertEquals("132", sheet.cell(1, 2))
    }

    @Test
    fun `handles quoted fields containing delimiter`() {
        val text = "姓名,备注\n张三,\"期中,含逗号\"\n"
        val sheet = CsvParser.parseText(text)
        assertEquals("期中,含逗号", sheet.cell(1, 1))
    }

    @Test
    fun `detects tab and semicolon delimiters`() {
        val tab = CsvParser.parseText("姓名\t语文\n张三\t125\n")
        assertEquals("125", tab.cell(1, 1))
        val semi = CsvParser.parseText("姓名;语文\n张三;125\n")
        assertEquals("125", semi.cell(1, 1))
    }

    @Test
    fun `decodes gb18030 fallback`() {
        val gbk = "姓名,语文\n张三,125\n".toByteArray(charset("GB18030"))
        val text = CsvParser.decodeSmart(gbk)
        assertEquals("姓名,语文", text.lineSequence().first().trim())
    }

    @Test
    fun `handles escaped quotes and trailing newline`() {
        val text = "a,b\n\"说\"\"话\"\",1\n"
        val sheet = CsvParser.parseText(text)
        assertEquals("说\"话\",1", sheet.cell(1, 0))
    }
}
