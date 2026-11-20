package com.scoretrack.ai

import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import com.scoretrack.core.Student
import com.scoretrack.core.formatRate
import com.scoretrack.core.formatScore
import com.scoretrack.domain.analytics.AnalysisResult
import com.scoretrack.domain.selection.ElectiveTotal
import com.scoretrack.domain.selection.SubjectSelection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AiExamSnapshot(
    val name: String,
    val date: String,
    /** 科目中文名 -> 正式成绩（化学/地理为赋分成绩） */
    val scores: Map<String, Double>,
    /** 科目中文名 -> 得分率（0~1，保留 4 位小数） */
    val rates: Map<String, Double>,
    /** 赋分科目的原始分（如果用户录入过） */
    val rawScores: Map<String, Double> = emptyMap(),
    val total: Double? = null,
    val complete: Boolean = true,
    val note: String = "",
)

@Serializable
data class AiSnapshot(
    val version: Int = 1,
    val student: String,
    val className: String,
    val studentNumber: String,
    val generatedAt: String,
    val subjectMax: Map<String, Double>,
    val scaledSubjects: List<String>,
    val exams: List<AiExamSnapshot>,
    val statsText: String,
    val warnings: List<String> = emptyList(),
    /** 3+1+2 选科：首选科目（物理/历史） */
    val firstChoice: String? = null,
    /** 3+1+2 选科：再选科目（两门） */
    val secondChoices: List<String> = emptyList(),
    /** 完整选科组合，例如「物理+化学+生物」 */
    val selectionCombo: String? = null,
    /** 选考三科（首选 + 再选） */
    val electiveSubjects: List<String> = emptyList(),
    /** 选考总分（三科，满分 300），数据不全时为空 */
    val electiveTotal: Double? = null,
)

object AiJson {
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
}

object SnapshotBuilder {

    fun build(
        student: Student,
        analysis: AnalysisResult,
        selection: SubjectSelection = SubjectSelection.empty(student.id),
        elective: ElectiveTotal? = null,
    ): AiSnapshot {
        val exams = analysis.exams.map { exam ->
            AiExamSnapshot(
                name = exam.name,
                date = exam.dateText,
                scores = exam.scores.entries
                    .filter { it.value != null }
                    .associate { it.key.cnName to (it.value ?: 0.0) },
                rates = exam.scores.entries
                    .filter { it.value != null }
                    .associate { it.key.cnName to round4(it.key.rateOf(it.value ?: 0.0)) },
                rawScores = exam.rawScores.entries
                    .filter { it.value != null && it.key.isScaled }
                    .associate { it.key.cnName to (it.value ?: 0.0) },
                total = exam.total,
                complete = exam.isComplete,
                note = exam.note,
            )
        }
        return AiSnapshot(
            student = student.name,
            className = student.className,
            studentNumber = student.studentNumber,
            generatedAt = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            subjectMax = Subject.displayOrder().associate { it.cnName to it.maxScore },
            scaledSubjects = Subject.displayOrder().filter { it.isScaled }.map { it.cnName },
            exams = exams,
            statsText = buildStatsText(analysis),
            warnings = analysis.notes,
            firstChoice = selection.first?.cnName,
            secondChoices = selection.secondChoices.map { it.cnName },
            selectionCombo = if (selection.isComplete) selection.comboLabel else null,
            electiveSubjects = selection.selectedSubjects.map { it.cnName },
            electiveTotal = elective?.total,
        )
    }

    private fun round4(value: Double): Double = kotlin.math.round(value * 10000.0) / 10000.0

    private fun buildStatsText(analysis: AnalysisResult): String {
        val sb = StringBuilder()
        val overall = analysis.overall
        sb.appendLine("完整考试次数：${overall.completeExams}；考试总次数：${overall.totalExams}")
        sb.appendLine("总分平均：${overall.average?.let { formatScore(kotlin.math.round(it * 10) / 10.0) } ?: "数据不足"}")
        sb.appendLine("总分最高：${overall.best?.let { formatScore(it) } ?: "数据不足"}")
        sb.appendLine("最近总分：${overall.latest?.let { formatScore(it) } ?: "数据不足"}")
        sb.appendLine("最近一次总分变化：${overall.latestDelta?.let { com.scoretrack.core.formatDelta(it) } ?: "数据不足"}")
        sb.appendLine("总分标准差：${overall.stability?.let { formatScore(kotlin.math.round(it * 10) / 10.0) } ?: "数据不足"}")
        sb.appendLine("总分趋势：" + overall.trend.label)
        analysis.subjects.forEach { stat ->
            sb.appendLine(
                "[" + stat.subject.cnName + "] 次数=" + stat.count +
                    " 当前=" + (stat.current?.let { formatScore(it) } ?: "缺失") +
                    " 最高=" + (stat.best?.let { formatScore(it) } ?: "缺失") +
                    " 平均=" + (stat.average?.let { formatScore(kotlin.math.round(it * 10) / 10.0) } ?: "缺失") +
                    " 平均得分率=" + (stat.averageRate?.let { formatRate(it) } ?: "缺失") +
                    " 最近变化=" + (stat.latestDelta?.let { com.scoretrack.core.formatDelta(it) } ?: "数据不足") +
                    " 趋势=" + stat.trend.label +
                    " 得分率标准差=" + (stat.stability?.let { formatRate(it) } ?: "数据不足")
            )
        }
        return sb.toString().trim()
    }
}

object PromptBuilder {

    const val SYSTEM_PROMPT = """你是经验丰富的高中班主任与成绩分析教师，负责根据真实考试数据撰写学情分析报告。

必须遵守的规则：
1. 只使用用户消息中提供的成绩数据，严禁编造、推测或补全任何未给出的分数、排名或事实。
2. 语文、数学、英语、物理是原始分，化学、地理是赋分成绩（正式成绩即赋分值）。满分分别为 150/150/150/100/100/100，总分满分 750。
3. 判断科目强弱必须使用得分率（成绩 ÷ 满分），不能直接比较原始分（例如物理 90 分与语文 120 分不可直接比较）。
3.1 学生采用新高考 3+1+2 选科。必须围绕其「选考三科」（首选 1 门 + 再选 2 门）展开分析：优先分析这三科的当前成绩、历史变化与得分率。未被选考的科目只能作为历史背景，绝不能当作学生当前的选考科目或高考计分科目来分析。
3.2 不得仅凭选科组合推断任何大学的录取资格或专业限制；如涉及，必须说明需要具体高校与专业的招生要求作为依据。
4. 数据不足时必须明确说明。只有 1 次考试时不要说"持续上升"；缺失科目不要当作 0 分。
5. 每条结论都要引用具体数据（分数、得分率、变化值、考试名称），建议要具体可执行。
6. 不要输出与学习无关的个人信息，不要输出 API Key、URL 等技术细节。
7. 使用简体中文，输出 Markdown，不要使用代码块。

报告必须包含以下二级标题（保持标题文字）：
## A. 整体成绩分析
## B. 六科逐科分析
## C. 优势科目与薄弱科目
## D. 成绩变化分析
## E. 后续学习建议

其中 B 部分每科使用三级标题（### 语文 等），并覆盖：当前成绩、历史变化、历史最高、最近一次变化、相对薄弱程度（按得分率）、后续值得关注的方面。"""

    fun userPrompt(snapshot: AiSnapshot, analysis: AnalysisResult): String {
        val json = AiJson.json.encodeToString(snapshot)
        val range = if (analysis.exams.isEmpty()) {
            "无考试数据"
        } else {
            val first = analysis.exams.first().dateText
            val last = analysis.exams.last().dateText
            "$first ~ $last ，共 ${analysis.exams.size} 次考试"
        }
        return buildString {
            appendLine("学生：${snapshot.student}" + (if (snapshot.className.isNotBlank()) "（班级：${snapshot.className}）" else ""))
            if (snapshot.selectionCombo != null) {
                appendLine("选科（3+1+2）：${snapshot.selectionCombo}（首选：${snapshot.firstChoice}；再选：${snapshot.secondChoices.joinToString("、")}）")
                appendLine("选考三科：${snapshot.electiveSubjects.joinToString("、")}" + (snapshot.electiveTotal?.let { "；最近选考总分 $it / 300" } ?: ""))
                appendLine("请重点分析以上选考三科；其他科目仅作历史参考，不得作为其当前选考科目。")
            } else {
                appendLine("选科：尚未完成（请只基于已有成绩分析，不要假定其选考科目）")
            }
            appendLine("分析范围：$range")
            appendLine("考试数据（JSON，按时间从早到晚排序，scores 为正式成绩，rates 为得分率）：")
            appendLine("```json")
            appendLine(json)
            appendLine("```")
            appendLine("请严格依据以上数据生成报告。如果数据不足以支撑某个结论，请直接说明“数据不足”，不要编造。")
        }
    }
}
