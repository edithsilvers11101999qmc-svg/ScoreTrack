package com.scoretrack.parser

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 一个电子表格/CSV 工作表的纯文本内容。 */
data class SheetData(
    val sheetName: String,
    val rows: List<List<String>>,
) {
    val rowCount: Int get() = rows.size
    val colCount: Int get() = rows.maxOfOrNull { it.size } ?: 0

    fun cell(row: Int, col: Int): String =
        rows.getOrNull(row)?.getOrNull(col)?.trim() ?: ""

    fun toText(): String = rows.joinToString("\n") { it.joinToString("\t") }
}

/** CSV / TSV 解析：支持引号包裹、引号转义、分隔符自动探测。 */
object CsvParser {

    fun parse(file: File): SheetData {
        val text = decodeSmart(file.readBytes())
        return parseText(text, file.nameWithoutExtension)
    }

    fun parseText(text: String, name: String = "CSV"): SheetData {
        val clean = text.removePrefix("\uFEFF")
        val delimiter = detectDelimiter(clean)
        val rows = ArrayList<List<String>>()
        var current = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < clean.length) {
            val ch = clean[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < clean.length && clean[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == delimiter -> {
                    current.add(field.toString().trim())
                    field.setLength(0)
                }
                ch == '\r' -> Unit
                ch == '\n' -> {
                    current.add(field.toString().trim())
                    field.setLength(0)
                    if (current.any { it.isNotEmpty() }) rows.add(current)
                    current = ArrayList()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || current.isNotEmpty()) {
            current.add(field.toString().trim())
            if (current.any { it.isNotEmpty() }) rows.add(current)
        }
        return SheetData(name, rows)
    }

    private fun detectDelimiter(text: String): Char {
        val sample = text.lineSequence().take(8).joinToString("\n")
        val candidates = listOf(',', '\t', ';', '|')
        var best = ','
        var bestScore = -1.0
        for (c in candidates) {
            val lines = sample.lineSequence().filter { it.isNotBlank() }.toList()
            if (lines.isEmpty()) continue
            val counts = lines.map { line -> countOutsideQuotes(line, c) }
            val avg = counts.average()
            if (avg <= 0.5) continue
            val variance = counts.map { (it - avg) * (it - avg) }.average()
            val score = avg - variance
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        return best
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var count = 0
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == delimiter -> count++
            }
        }
        return count
    }

    /** UTF-8 优先，失败回退 GB18030（国内成绩单常见编码）。 */
    fun decodeSmart(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val utf8 = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
        if (utf8 != null && !utf8.contains('\uFFFD')) return utf8
        return try {
            String(bytes, Charset.forName("GB18030"))
        } catch (t: Throwable) {
            String(bytes, Charsets.UTF_8)
        }
    }
}
