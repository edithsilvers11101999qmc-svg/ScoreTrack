package com.scoretrack.parser

import java.io.File
import jxl.Workbook

/** 旧版 .xls（BIFF）读取，基于 jxl。 */
object XlsParser {

    fun parse(file: File, maxRows: Int = 2000, maxCols: Int = 60): List<SheetData> {
        val workbook = Workbook.getWorkbook(file)
        try {
            val result = ArrayList<SheetData>()
            for (i in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheet(i)
                val rows = minOf(sheet.rows, maxRows)
                val cols = minOf(sheet.columns, maxCols)
                val grid = ArrayList<List<String>>(rows)
                for (r in 0 until rows) {
                    val row = ArrayList<String>(cols)
                    for (c in 0 until cols) {
                        val text = try {
                            sheet.getCell(c, r).contents ?: ""
                        } catch (t: Throwable) {
                            ""
                        }
                        row.add(text.trim())
                    }
                    grid.add(row)
                }
                if (grid.any { row -> row.any { it.isNotEmpty() } }) {
                    result.add(SheetData(sheet.name ?: "Sheet${i + 1}", grid))
                }
            }
            return result
        } finally {
            workbook.close()
        }
    }
}
