package com.scoretrack.ocr

import com.scoretrack.core.FieldEvidence
import com.scoretrack.core.Subject
import com.scoretrack.core.Student
import kotlin.math.abs

enum class ColumnRole { NAME, CLASS, NUMBER, SUBJECT, TOTAL, OTHER }

data class ColumnBinding(
    val col: Int,
    val role: ColumnRole,
    val subject: Subject? = null,
    /** 对化学/地理：该列是否为赋分列 */
    val scaled: Boolean = false,
    /** 该列是否明确标注为原始分（卷面分） */
    val raw: Boolean = false,
    val header: String = "",
)

enum class WarningKind {
    HEADER_NOT_FOUND, SCALED_AMBIGUOUS, LOW_CONFIDENCE, OUT_OF_RANGE,
    MISSING_SCORE, STUDENT_NOT_FOUND, STUDENT_AMBIGUOUS, DUPLICATE_COLUMN,
    NO_TABLE, PARSER_FALLBACK, UNKNOWN_HEADER,
}

data class ParseWarning(val kind: WarningKind, val message: String, val rowIndex: Int? = null)

data class RecognizedRow(
    val rowIndex: Int,
    val name: String,
    val className: String,
    val studentNumber: String,
    val scores: Map<Subject, Double?>,
    val rawScores: Map<Subject, Double?>,
    val confidences: Map<Subject, Float>,
    val evidence: Map<Subject, FieldEvidence>,
    val warnings: List<ParseWarning> = emptyList(),
)

data class SheetParseResult(
    val table: TableData?,
    val headerRow: Int,
    val subHeaderRow: Int,
    val bindings: List<ColumnBinding>,
    val rows: List<RecognizedRow>,
    val warnings: List<ParseWarning>,
    val matchedRowIndex: Int? = null,
    val candidates: List<Int> = emptyList(),
    val matchMessage: String? = null,
    val pageIndex: Int = 0,
    val sourceLabel: String = "",
)

/**
 * 表头定位 -> 列语义绑定 -> 数据行解析 -> 目标学生匹配。
 * 所有判断都基于可见文字，不做上下文猜测；不确定时输出警告交给用户确认。
 */
object SheetReconstructor {

    private val nameKeys = listOf("姓名", "学生姓名", "考生姓名", "名字")
    private val classKeys = listOf("班级", "班别", "所在班级")
    private val numberKeys = listOf("学号", "考号", "准考证号", "编号", "座号", "考籍号")
    private val totalKeys = listOf("总分", "合计", "总计", "总成绩")
    private val scaledKeys = listOf("赋分", "等级分", "转换分", "折算分", "赋")
    private val rawKeys = listOf("原始", "卷面", "裸分")

    private val subjectKeys: List<Pair<Subject, List<String>>> = listOf(
        Subject.CHINESE to listOf("语文", "语"),
        Subject.MATH to listOf("数学", "数"),
        Subject.ENGLISH to listOf("英语", "外语", "英文", "英"),
        Subject.PHYSICS to listOf("物理", "物"),
        Subject.CHEMISTRY to listOf("化学", "化"),
        Subject.GEOGRAPHY to listOf("地理", "地"),
        // V4：3+1+2 选考科目（生物/政治为赋分，历史为原始分）
        Subject.BIOLOGY to listOf("生物", "生"),
        Subject.POLITICS to listOf("政治", "政"),
        Subject.HISTORY to listOf("历史", "史"),
    )

    /** 单字简称只在完全相等时匹配，避免「考生姓名」被识别成「生物」。 */
    private fun matchesSubjectKey(text: String): Boolean = subjectKeys.any { (_, keys) ->
        keys.any { key -> text == key || (key.length > 1 && text.contains(key)) }
    }

    fun parse(
        page: OcrPage,
        table: TableData?,
        student: Student?,
        pageIndex: Int = 0,
        sourceLabel: String = "",
    ): SheetParseResult {
        val warnings = ArrayList<ParseWarning>()
        if (table == null) {
            warnings.add(ParseWarning(WarningKind.NO_TABLE, "未能识别出表格结构，请重新拍摄或换用文件导入"))
            return SheetParseResult(null, -1, -1, emptyList(), emptyList(), warnings, pageIndex = pageIndex, sourceLabel = sourceLabel)
        }
        val headerRow = findHeaderRow(table)
        if (headerRow < 0) {
            warnings.add(ParseWarning(WarningKind.HEADER_NOT_FOUND, "未找到包含科目名称的表头行，无法确定六科成绩所在列"))
            return SheetParseResult(table, -1, -1, emptyList(), emptyList(), warnings, pageIndex = pageIndex, sourceLabel = sourceLabel)
        }
        val subHeaderRow = if (looksLikeSubHeader(table, headerRow + 1)) headerRow + 1 else -1
        val bindings = buildBindings(table, headerRow, subHeaderRow, warnings)

        val rows = ArrayList<RecognizedRow>()
        val dataStart = maxOf(headerRow, subHeaderRow) + 1
        for (r in dataStart until table.rowCount) {
            val rowCells = table.rowCells(r)
            if (rowCells.isEmpty()) continue
            val joined = rowCells.joinToString(" ") { it.text }
            if (joined.isBlank()) continue
            if (isSummaryRow(joined)) continue
            val row = parseRow(table, r, bindings, warnings) ?: continue
            // 过滤表格下方的说明行（如「制表：教务处」）：没有任何身份信息也没有任何科目成绩
            val hasIdentity = row.name.isNotBlank() || row.className.isNotBlank() || row.studentNumber.isNotBlank()
            val hasScore = row.scores.values.any { it != null } || row.rawScores.values.any { it != null }
            if (!hasIdentity && !hasScore) continue
            rows.add(row)
        }
        if (rows.isEmpty()) {
            warnings.add(ParseWarning(WarningKind.STUDENT_NOT_FOUND, "表头下方没有解析到学生成绩行"))
        }

        var matched: Int? = null
        var candidates: List<Int> = emptyList()
        var message: String? = null
        if (student != null) {
            val result = matchStudent(rows, student, warnings)
            matched = result.rowIndex
            candidates = result.candidates
            message = result.message
        }
        // 只把「将要写入」的那一行（匹配行，或待用户选择的行）的警告提升到页面级，
        // 避免其他学生行/说明行的噪声警告干扰复核。
        val focusRows = when {
            matched != null -> listOfNotNull(rows.getOrNull(matched))
            candidates.isNotEmpty() -> candidates.mapNotNull { rows.getOrNull(it) }
            else -> emptyList()
        }
        for (row in focusRows) {
            warnings.addAll(row.warnings.filterNot { w -> warnings.any { it.kind == w.kind && it.message == w.message } })
        }
        return SheetParseResult(
            table = table,
            headerRow = headerRow,
            subHeaderRow = subHeaderRow,
            bindings = bindings,
            rows = rows,
            warnings = warnings,
            matchedRowIndex = matched,
            candidates = candidates,
            matchMessage = message,
            pageIndex = pageIndex,
            sourceLabel = sourceLabel,
        )
    }

    private fun isSummaryRow(text: String): Boolean {
        val t = text.replace(" ", "")
        return t.contains("平均") || t.contains("合计") || t.contains("总计") ||
            t.contains("最高分") || t.contains("最低分") || t.contains("及格率") || t.contains("优秀率")
    }

    private fun findHeaderRow(table: TableData): Int {
        var bestRow = -1
        var bestScore = 0
        val limit = minOf(table.rowCount, 10)
        for (r in 0 until limit) {
            var score = 0
            for (cell in table.rowCells(r)) {
                val text = normalize(cell.text)
                if (matchesSubjectKey(text)) score += 2
                if (nameKeys.any { text.contains(it) }) score += 1
                if (totalKeys.any { text.contains(it) }) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                bestRow = r
            }
        }
        return if (bestScore >= 3) bestRow else -1
    }

    private fun looksLikeSubHeader(table: TableData, row: Int): Boolean {
        if (row >= table.rowCount) return false
        val cells = table.rowCells(row)
        if (cells.isEmpty()) return false
        var markers = 0
        var subjects = 0
        for (cell in cells) {
            val t = normalize(cell.text)
            if (t.length > 10) return false
            if (scaledKeys.any { t.contains(it) } || rawKeys.any { t.contains(it) }) markers++
            if (matchesSubjectKey(t)) subjects++
        }
        return markers >= 1 && subjects <= 2
    }

    private fun buildBindings(
        table: TableData,
        headerRow: Int,
        subHeaderRow: Int,
        warnings: MutableList<ParseWarning>,
    ): List<ColumnBinding> {
        val bindings = ArrayList<ColumnBinding>()
        for (c in 0 until table.colCount) {
            val top = table.cellAt(headerRow, c)?.text ?: ""
            val sub = if (subHeaderRow >= 0) table.cellAt(subHeaderRow, c)?.text ?: "" else ""
            val header = (top + " " + sub).trim()
            val n = normalize(header)
            var binding = ColumnBinding(c, ColumnRole.OTHER, header = header)
            when {
                n.isEmpty() -> Unit
                nameKeys.any { n.contains(it) } -> binding = binding.copy(role = ColumnRole.NAME)
                numberKeys.any { n.contains(it) } -> binding = binding.copy(role = ColumnRole.NUMBER)
                classKeys.any { n.contains(it) } -> binding = binding.copy(role = ColumnRole.CLASS)
                totalKeys.any { n.contains(it) } -> binding = binding.copy(role = ColumnRole.TOTAL)
                else -> {
                    val subject = subjectKeys.firstOrNull { (_, keys) ->
                        keys.any { key -> n == key || (key.length > 1 && n.contains(key)) }
                    }?.first
                    if (subject != null) {
                        val isRaw = rawKeys.any { n.contains(it) }
                        val isScaled = scaledKeys.any { n.contains(it) } && !isRaw
                        binding = binding.copy(role = ColumnRole.SUBJECT, subject = subject, scaled = isScaled, raw = isRaw)
                    }
                }
            }
            bindings.add(binding)
        }

        val bySubject = bindings.filter { it.role == ColumnRole.SUBJECT && it.subject != null }
            .groupBy { it.subject!! }
        for ((subject, cols) in bySubject) {
            if (cols.size >= 2) {
                if (cols.none { it.scaled }) {
                    warnings.add(
                        ParseWarning(
                            WarningKind.SCALED_AMBIGUOUS,
                            "${subject.cnName}存在 ${cols.size} 列且未标注赋分，请人工确认哪一列是赋分成绩",
                        )
                    )
                }
            } else if (subject.isScaled && !cols.first().scaled) {
                warnings.add(
                    ParseWarning(
                        WarningKind.SCALED_AMBIGUOUS,
                        "${subject.cnName}为赋分科目，但表头未明确标注“赋分”，请确认该列是否为赋分成绩",
                    )
                )
            }
        }
        return bindings
    }

    private fun parseRow(
        table: TableData,
        r: Int,
        bindings: List<ColumnBinding>,
        warnings: MutableList<ParseWarning>,
    ): RecognizedRow? {
        val nameBinding = bindings.firstOrNull { it.role == ColumnRole.NAME }
        val classBinding = bindings.firstOrNull { it.role == ColumnRole.CLASS }
        val numberBinding = bindings.firstOrNull { it.role == ColumnRole.NUMBER }

        var name = nameBinding?.let { table.cellAt(r, it.col)?.text?.trim() } ?: ""
        if (name.isBlank()) {
            name = table.rowCells(r).firstOrNull { cell ->
                val t = cell.text.trim()
                t.length in 2..4 && t.all { ch -> ch in '\u4e00'..'\u9fa5' }
            }?.text ?: ""
        }
        name = cleanName(name)
        if (name.isBlank()) return null

        val classRaw = classBinding?.let { table.cellAt(r, it.col)?.text }
            ?: table.rowCells(r).firstOrNull { c -> c.text.contains("班") }?.text ?: ""
        val className = cleanClass(classRaw)
        val studentNumber = (numberBinding?.let { table.cellAt(r, it.col)?.text } ?: "")
            .filter { it.isDigit() }.take(20)

        val scores = LinkedHashMap<Subject, Double?>()
        val rawScores = LinkedHashMap<Subject, Double?>()
        val confidences = LinkedHashMap<Subject, Float>()
        val evidence = LinkedHashMap<Subject, FieldEvidence>()
        val rowWarnings = ArrayList<ParseWarning>()

        for (subject in Subject.displayOrder()) {
            val cols = bindings.filter { it.role == ColumnRole.SUBJECT && it.subject == subject }
            val scaledCol = cols.firstOrNull { it.scaled }
                ?: cols.firstOrNull { !it.raw }
                ?: cols.firstOrNull()
            val rawCol = cols.firstOrNull { it.raw }

            val cell = scaledCol?.let { table.cellAt(r, it.col) }
            if (cell != null && cell.text.isNotBlank()) {
                val value = parseScore(cell.text, subject.maxScore, subject, rowWarnings, r)
                scores[subject] = value
                cell.confidence?.let { confidences[subject] = it }
                evidence[subject] = FieldEvidence(
                    rawText = cell.text,
                    confidence = cell.confidence,
                    left = cell.box.left.toInt(),
                    top = cell.box.top.toInt(),
                    right = cell.box.right.toInt(),
                    bottom = cell.box.bottom.toInt(),
                )
                if (value == null) {
                    rowWarnings.add(
                        ParseWarning(WarningKind.MISSING_SCORE, "${subject.cnName}成绩无法解析：“${cell.text}”", r)
                    )
                } else if (cell.confidence != null && cell.confidence!! < 0.55f) {
                    rowWarnings.add(
                        ParseWarning(WarningKind.LOW_CONFIDENCE, "${subject.cnName}识别置信度较低（${"%.2f".format(cell.confidence)}），请核对", r)
                    )
                }
            } else {
                scores[subject] = null
                rowWarnings.add(ParseWarning(WarningKind.MISSING_SCORE, "${subject.cnName}成绩缺失", r))
            }

            if (subject.isScaled && rawCol != null) {
                val rawCell = table.cellAt(r, rawCol.col)
                if (rawCell != null && rawCell.text.isNotBlank()) {
                    rawScores[subject] = parseScore(rawCell.text, subject.maxScore, subject, rowWarnings, r)
                }
            }
        }
        return RecognizedRow(
            rowIndex = r,
            name = name,
            className = className,
            studentNumber = studentNumber,
            scores = scores,
            rawScores = rawScores,
            confidences = confidences,
            evidence = evidence,
            warnings = rowWarnings,
        )
    }

    fun parseScore(text: String, max: Double, subject: Subject, warnings: MutableList<ParseWarning>, row: Int): Double? {
        val cleaned = normalizeNumber(text)
        val match = Regex("(\\d{1,3}(?:\\.\\d{1,2})?)").find(cleaned) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        if (value < 0 || value > max) {
            warnings.add(
                ParseWarning(WarningKind.OUT_OF_RANGE, "${subject.cnName}成绩 $value 超出 0~${max.toInt()} 范围", row)
            )
            return null
        }
        return value
    }

    fun normalizeNumber(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch in '０'..'９' -> sb.append('0' + (ch - '０'))
                ch == 'O' || ch == 'o' || ch == '〇' -> sb.append('0')
                ch == 'l' || ch == 'I' || ch == '|' -> sb.append('1')
                ch == '，' || ch == '。' || ch == '·' -> sb.append('.')
                ch.isDigit() || ch == '.' -> sb.append(ch)
                else -> sb.append(' ')
            }
        }
        return sb.toString()
    }

    private fun cleanName(raw: String): String {
        var name = raw.replace(" ", "").replace("\n", "").trim()
        for (suffix in listOf("同学", "（", "(", "：", ":")) {
            val i = name.indexOf(suffix)
            if (i > 1) name = name.substring(0, i)
        }
        return name.filter { it in '\u4e00'..'\u9fa5' || it == '·' || it in 'A'..'Z' || it in 'a'..'z' }
    }

    private fun cleanClass(raw: String): String {
        val t = raw.replace(" ", "").trim()
        val match = Regex("([^\\s]{0,6}班)").find(t)
        return match?.groupValues?.get(1) ?: t.take(12)
    }

    fun normalize(text: String): String = text.replace(" ", "").replace("\n", "")
        .replace("（", "(").replace("）", ")")

    // ---------------------------------------------------------------- 学生匹配

    data class MatchResult(
        val rowIndex: Int?,
        val candidates: List<Int>,
        val message: String?,
    )

    fun matchStudent(rows: List<RecognizedRow>, student: Student, warnings: MutableList<ParseWarning>): MatchResult {
        val target = cleanName(student.name)
        if (target.isEmpty()) return MatchResult(null, emptyList(), "目标学生姓名为空")

        val exact = rows.filter { cleanName(it.name) == target }
        val pool: List<RecognizedRow> = if (exact.isEmpty()) {
            val fuzzy = rows.filter {
                val n = cleanName(it.name)
                n.isNotEmpty() && (n.contains(target) || target.contains(n) ||
                    (abs(n.length - target.length) <= 1 && levenshtein(n, target) <= 1))
            }
            if (fuzzy.isEmpty()) {
                warnings.add(ParseWarning(WarningKind.STUDENT_NOT_FOUND, "成绩单中未找到学生“${student.name}”"))
                return MatchResult(null, emptyList(), "未找到学生“${student.name}”")
            }
            fuzzy
        } else {
            exact
        }

        if (pool.size == 1) {
            val row = pool.first()
            if (student.studentNumber.isNotBlank() && row.studentNumber.isNotBlank() &&
                student.studentNumber != row.studentNumber
            ) {
                warnings.add(
                    ParseWarning(
                        WarningKind.STUDENT_AMBIGUOUS,
                        "姓名匹配到第 ${row.rowIndex + 1} 行，但学号（${row.studentNumber}）与档案（${student.studentNumber}）不一致，请核对",
                    )
                )
            }
            return MatchResult(rows.indexOf(row), emptyList(), null)
        }

        var narrowed = pool
        if (student.studentNumber.isNotBlank()) {
            val byNumber = pool.filter { it.studentNumber.isNotBlank() && it.studentNumber == student.studentNumber }
            if (byNumber.size == 1) return MatchResult(rows.indexOf(byNumber.first()), emptyList(), null)
            if (byNumber.size > 1) narrowed = byNumber
        }
        if (student.className.isNotBlank()) {
            val byClass = narrowed.filter { it.className.isNotBlank() && it.className == student.className }
            if (byClass.size == 1) return MatchResult(rows.indexOf(byClass.first()), emptyList(), null)
            if (byClass.size > 1) narrowed = byClass
        }
        warnings.add(
            ParseWarning(WarningKind.STUDENT_AMBIGUOUS, "成绩单中有 ${narrowed.size} 条同名记录，无法自动确定，请人工选择")
        )
        return MatchResult(null, narrowed.map { rows.indexOf(it) }, "同名记录需人工确认")
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }
}
