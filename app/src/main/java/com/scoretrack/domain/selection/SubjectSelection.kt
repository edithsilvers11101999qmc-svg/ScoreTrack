package com.scoretrack.domain.selection

import com.scoretrack.core.Subject
import com.scoretrack.core.formatScore

/**
 * 新高考 3+1+2 选科模型。
 *
 * - 首选：物理 / 历史（二选一，必选）
 * - 再选：生物 / 地理 / 政治 / 化学（四选二，不能多选也不能少选）
 * - 合计 2 × C(4,2) = 12 种合法组合
 */
data class SubjectSelection(
    val studentId: String,
    val first: Subject? = null,
    val second: List<Subject> = emptyList(),
) {
    /** 再选科目去重后的有序列表（最多 2 门）。 */
    val secondChoices: List<Subject>
        get() = Subject.secondChoiceOptions().filter { it in second }

    val isComplete: Boolean
        get() = first != null && first in Subject.firstChoiceOptions() && secondChoices.size == 2

    /** 已选科目（首选 + 再选），仅完整时返回 3 门。 */
    val selectedSubjects: List<Subject>
        get() = if (isComplete) listOfNotNull(first) + secondChoices else emptyList()

    /** 「物理+化学+生物」形式的组合文案；未完成时返回提示文案。 */
    val comboLabel: String
        get() = if (isComplete) selectedSubjects.joinToString("+") { it.cnName } else "尚未完成选科"

    val missingCount: Int
        get() {
            val firstMissing = if (first in Subject.firstChoiceOptions()) 0 else 1
            return firstMissing + (2 - secondChoices.size).coerceAtLeast(0)
        }

    companion object {
        fun empty(studentId: String): SubjectSelection = SubjectSelection(studentId)
    }
}

/** 校验结果：ok=false 时 [message] 说明具体原因，[missing] 表示还差几门。 */
data class SelectionValidation(
    val ok: Boolean,
    val message: String?,
    val missing: Int,
)

object SelectionRules {

    val FIRST: List<Subject> = Subject.firstChoiceOptions()
    val SECOND: List<Subject> = Subject.secondChoiceOptions()

    /** 校验一组选择是否合法（重复、越界、数量错误都会被拒绝）。 */
    fun validate(first: Subject?, second: List<Subject>): SelectionValidation {
        if (first == null) {
            val missingSecond = (2 - second.distinct().count { it in SECOND }).coerceAtLeast(0)
            return SelectionValidation(false, "请选择首选科目（物理或历史）", 1 + missingSecond)
        }
        if (first !in FIRST) {
            val missingSecond = (2 - second.distinct().count { it in SECOND }).coerceAtLeast(0)
            return SelectionValidation(false, "首选科目只能是物理或历史，当前为「${first.cnName}」", 1 + missingSecond)
        }
        if (second.size > 2) {
            return SelectionValidation(false, "再选科目最多只能选择 2 门，当前选择了 ${second.size} 门", 0)
        }
        val distinct = second.distinct()
        if (distinct.size != second.size) {
            return SelectionValidation(false, "再选科目不能重复选择", 2 - distinct.size)
        }
        val invalid = distinct.filter { it !in SECOND }
        if (invalid.isNotEmpty()) {
            return SelectionValidation(
                false,
                "再选科目只能从生物、地理、政治、化学中选择，非法科目：" + invalid.joinToString("、") { it.cnName },
                0,
            )
        }
        val missing = (2 - distinct.size).coerceAtLeast(0)
        if (missing > 0) {
            return SelectionValidation(false, "还需要选择 $missing 门再选科目", missing)
        }
        return SelectionValidation(true, null, 0)
    }

    /** 12 种合法组合。 */
    fun allCombinations(): List<SelectionCombination> =
        FIRST.flatMap { first ->
            SECOND.indices.flatMap { i ->
                (i + 1 until SECOND.size).map { j ->
                    SelectionCombination(first, SECOND[i], SECOND[j])
                }
            }
        }
}

data class SelectionCombination(
    val first: Subject,
    val second1: Subject,
    val second2: Subject,
) {
    val label: String get() = "${first.cnName}+${second1.cnName}+${second2.cnName}"
    val subjects: List<Subject> get() = listOf(first, second1, second2)
    val key: String get() = subjects.joinToString("+") { it.code }
}

/**
 * 选考总分（独立统计，不改变 750 分总分体系）。
 *
 * 默认按原始分累加，满分 3 × 100 = 300；不同省份的赋分/等级分规则可通过
 * [ElectiveScoreRule] 扩展，界面与统计都通过 [ElectiveTotalCalculator] 取数，
 * 后续添加新规则不需要改动数据库或 UI。
 */
interface ElectiveScoreRule {
    val id: String
    val label: String
    val description: String

    /** 把某科成绩转换为计入选考总分的分数。 */
    fun convert(subject: Subject, score: Double): Double
}

/** 默认规则：直接使用已录入的正式成绩（赋分科目即赋分后的分数）。 */
object RawScoreRule : ElectiveScoreRule {
    override val id = "raw"
    override val label = "原始分累加（默认）"
    override val description = "直接使用各科已录入的正式成绩相加，满分 300 分；不做任何换算。"
    override fun convert(subject: Subject, score: Double): Double = score
}

object ElectiveScoreRules {
    private val rules: MutableMap<String, ElectiveScoreRule> = linkedMapOf(RawScoreRule.id to RawScoreRule)

    fun all(): List<ElectiveScoreRule> = rules.values.toList()

    fun default(): ElectiveScoreRule = RawScoreRule

    fun byId(id: String?): ElectiveScoreRule = rules[id] ?: RawScoreRule

    /** 预留：注册新的省级赋分规则（示例：按排名比例换算）。 */
    fun register(rule: ElectiveScoreRule) {
        rules[rule.id] = rule
    }
}

data class ElectiveTotal(
    val total: Double?,
    val maxTotal: Double,
    val missing: List<Subject>,
) {
    val complete: Boolean get() = total != null
    val text: String get() = total?.let { "${formatScore(it)} / ${formatScore(maxTotal)}" } ?: "暂不计算"
}

object ElectiveTotalCalculator {

    fun total(
        scores: Map<Subject, Double?>,
        selection: SubjectSelection,
        rule: ElectiveScoreRule = ElectiveScoreRules.default(),
    ): ElectiveTotal {
        val subjects = selection.selectedSubjects
        if (subjects.isEmpty()) {
            return ElectiveTotal(null, 0.0, emptyList())
        }
        val missing = subjects.filter { scores[it] == null }
        val max = subjects.sumOf { it.maxScore }
        if (missing.isNotEmpty()) {
            return ElectiveTotal(null, max, missing)
        }
        val sum = subjects.sumOf { subject ->
            rule.convert(subject, scores[subject] ?: 0.0)
        }
        return ElectiveTotal(sum, max, emptyList())
    }
}
