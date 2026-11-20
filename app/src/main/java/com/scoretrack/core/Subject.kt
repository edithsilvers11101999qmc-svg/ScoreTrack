package com.scoretrack.core

import java.util.Locale

/**
 * 科目定义。
 *
 * - **原有六科**（语文、数学、英语、物理、化学、地理）构成 750 分总分体系，
 *   由 [displayOrder] 返回，全部既有统计/图表/导出仍以这六科为准，规则不做任何改动。
 * - **新高考 3+1+2 选科**（V4）：首选 物理/历史，再选 生物/地理/政治/化学，
 *   因此额外增加 生物、政治、历史 三科，用于选科组合与选考成绩统计；
 *   它们不计入 750 分总分，只进入选考科目视图（见 [allSubjects]）。
 * - 化学、地理、生物、政治在多数省份为赋分科目，[isScaled] 仅用于展示与原始分录入，
 *   不影响既有 750 分总分的计算方式。
 */
enum class Subject(
    val code: String,
    val cnName: String,
    val maxScore: Double,
    val isScaled: Boolean,
    val shortName: String,
) {
    CHINESE("chinese", "语文", 150.0, false, "语"),
    MATH("math", "数学", 150.0, false, "数"),
    ENGLISH("english", "英语", 150.0, false, "英"),
    PHYSICS("physics", "物理", 100.0, false, "物"),
    CHEMISTRY("chemistry", "化学", 100.0, true, "化"),
    GEOGRAPHY("geography", "地理", 100.0, true, "地"),
    BIOLOGY("biology", "生物", 100.0, true, "生"),
    POLITICS("politics", "政治", 100.0, true, "政"),
    HISTORY("history", "历史", 100.0, false, "史");

    /** 得分率，0.0 ~ 1.0 */
    fun rateOf(score: Double): Double = (score / maxScore).coerceIn(0.0, 1.0)

    /** 分数格式化：整数不带小数点，非整数保留一位小数 */
    fun format(score: Double?): String = score?.let { formatScore(it) } ?: "—"

    companion object {
        const val TOTAL_MAX_SCORE: Double = 750.0

        fun fromCode(code: String?): Subject? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        fun ordered(): List<Subject> = entries.toList()

        /** 750 分总分体系的六科（既有规则，顺序不可变）。 */
        fun displayOrder(): List<Subject> = listOf(CHINESE, MATH, ENGLISH, PHYSICS, CHEMISTRY, GEOGRAPHY)

        /** 全部可选科目（六科 + 生物 / 政治 / 历史），用于成绩录入与选考统计。 */
        fun allSubjects(): List<Subject> =
            listOf(CHINESE, MATH, ENGLISH, PHYSICS, CHEMISTRY, BIOLOGY, GEOGRAPHY, POLITICS, HISTORY)

        /** 3+1+2 首选科目：物理 / 历史 二选一。 */
        fun firstChoiceOptions(): List<Subject> = listOf(PHYSICS, HISTORY)

        /** 3+1+2 再选科目：生物 / 地理 / 政治 / 化学 四选二。 */
        fun secondChoiceOptions(): List<Subject> = listOf(BIOLOGY, GEOGRAPHY, POLITICS, CHEMISTRY)

        /** 选考科目（首选 + 再选共 5 门）。 */
        fun electiveSubjects(): List<Subject> = firstChoiceOptions() + secondChoiceOptions()

        /** 是否属于 3+1+2 选考科目。 */
        fun isElective(subject: Subject): Boolean = subject in electiveSubjects()
    }
}

fun formatScore(value: Double): String {
    val rounded = if (kotlin.math.abs(value - value.toLong()) < 0.001) value.toLong().toDouble() else value
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.CHINA, "%.1f", rounded)
    }
}

fun formatRate(rate: Double): String = String.format(Locale.CHINA, "%.1f%%", rate * 100.0)

fun formatDelta(delta: Double): String {
    val sign = if (delta > 0) "+" else ""
    return "$sign${formatScore(kotlin.math.round(delta * 10) / 10.0)}"
}
