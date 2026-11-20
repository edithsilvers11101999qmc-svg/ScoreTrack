package com.scoretrack.domain.analytics

import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import kotlin.math.abs
import kotlin.math.sqrt

data class SubjectStat(
    val subject: Subject,
    val count: Int,
    val current: Double?,
    val currentRate: Double?,
    val best: Double?,
    val bestRate: Double?,
    val average: Double?,
    val averageRate: Double?,
    val latestDelta: Double?,
    val latestRateDelta: Double?,
    val stability: Double?,
    val slopePerExam: Double?,
    val trend: Trend,
) {
    val hasEnoughData: Boolean get() = count >= 2
    val rateText: String get() = averageRate?.let { com.scoretrack.core.formatRate(it) } ?: "—"
}

enum class Trend(val label: String) {
    RISING("持续上升"),
    FALLING("持续下降"),
    FLUCTUATING("波动明显"),
    STABLE("基本稳定"),
    INSUFFICIENT("数据不足"),
}

data class OverallStat(
    val completeExams: Int,
    val totalExams: Int,
    val latest: Double?,
    val best: Double?,
    val average: Double?,
    val latestDelta: Double?,
    val stability: Double?,
    val slopePerExam: Double?,
    val trend: Trend,
) {
    val hasEnoughData: Boolean get() = completeExams >= 2
}

data class AnalysisResult(
    val exams: List<ExamRecord>,
    val overall: OverallStat,
    val subjects: List<SubjectStat>,
    val strengths: List<SubjectStat>,
    val weaknesses: List<SubjectStat>,
    val notes: List<String>,
) {
    val hasAnyData: Boolean get() = exams.isNotEmpty()
    val hasEnoughData: Boolean get() = overall.completeExams >= 2

    fun subjectStat(subject: Subject): SubjectStat? = subjects.firstOrNull { it.subject == subject }

    /** 各科得分率，按从高到低排序（用于优势/薄弱判断，避免直接比较原始分） */
    val rankedByRate: List<SubjectStat>
        get() = subjects.filter { it.averageRate != null }.sortedByDescending { it.averageRate }
}

object AnalyticsEngine {

    private const val TREND_THRESHOLD = 0.015
    private const val VOLATILITY_THRESHOLD = 0.06

    fun analyze(exams: List<ExamRecord>): AnalysisResult {
        val ordered = exams.sortedWith(compareBy({ it.date }, { it.createdAt }))
        val notes = mutableListOf<String>()

        val complete = ordered.filter { it.total != null }
        if (ordered.isEmpty()) {
            notes += "暂无考试记录，录入成绩后才能生成分析。"
        } else if (complete.isEmpty()) {
            notes += "现有考试记录六科成绩不完整，暂不计算总分统计。"
        } else if (complete.size < 2) {
            notes += "目前只有 1 次完整考试，趋势与稳定性需要至少 2 次完整考试才能判断。"
        }
        if (ordered.any { !it.isComplete }) {
            notes += "有 ${ordered.count { !it.isComplete }} 次考试存在缺失科目，这些记录不参与总分计算，也不会被当作 0 分。"
        }

        val totals = complete.mapNotNull { it.total }
        val overall = OverallStat(
            completeExams = complete.size,
            totalExams = ordered.size,
            latest = complete.lastOrNull()?.total,
            best = totals.maxOrNull(),
            average = totals.takeIf { it.isNotEmpty() }?.average(),
            latestDelta = if (totals.size >= 2) totals.last() - totals[totals.size - 2] else null,
            stability = totals.takeIf { it.size >= 2 }?.let { stdDev(it) },
            slopePerExam = totals.takeIf { it.size >= 2 }?.let { slope(it) },
            trend = trendOf(totals, TREND_THRESHOLD * Subject.TOTAL_MAX_SCORE, VOLATILITY_THRESHOLD * Subject.TOTAL_MAX_SCORE),
        )

        val subjects = Subject.displayOrder().map { subject -> subjectStat(subject, ordered) }

        val ranked = subjects.filter { it.averageRate != null }
        val ratedCount = ranked.size
        val strengths: List<SubjectStat>
        val weaknesses: List<SubjectStat>
        if (ratedCount >= 3) {
            strengths = ranked.sortedByDescending { it.averageRate }.take(2)
            weaknesses = ranked.sortedBy { it.averageRate }.take(2)
        } else if (ratedCount > 0) {
            strengths = ranked.sortedByDescending { it.averageRate }.take(1)
            weaknesses = ranked.sortedBy { it.averageRate }.take(1)
        } else {
            strengths = emptyList()
            weaknesses = emptyList()
        }

        // 若某科成绩次数过少，明确提示，避免过度解读
        val sparse = subjects.filter { it.count in 1..2 }
        if (sparse.isNotEmpty()) {
            notes += "以下科目有效成绩少于 3 次，统计结论仅供参考：" +
                sparse.joinToString("、") { it.subject.cnName }
        }

        return AnalysisResult(
            exams = ordered,
            overall = overall,
            subjects = subjects,
            strengths = strengths,
            weaknesses = weaknesses,
            notes = notes,
        )
    }

    fun subjectStat(subject: Subject, exams: List<ExamRecord>): SubjectStat {
        val values = exams.mapNotNull { it.scoreOf(subject) }
        val rates = values.map { subject.rateOf(it) }
        val count = values.size
        val current = values.lastOrNull()
        val best = values.maxOrNull()
        val average = values.takeIf { it.isNotEmpty() }?.average()
        val delta = if (values.size >= 2) values.last() - values[values.size - 2] else null
        val rateDelta = if (rates.size >= 2) rates.last() - rates[rates.size - 2] else null
        val stability = rates.takeIf { it.size >= 2 }?.let { stdDev(it) }
        val slope = rates.takeIf { it.size >= 2 }?.let { slope(it) }
        return SubjectStat(
            subject = subject,
            count = count,
            current = current,
            currentRate = current?.let { subject.rateOf(it) },
            best = best,
            bestRate = best?.let { subject.rateOf(it) },
            average = average,
            averageRate = average?.let { subject.rateOf(it) },
            latestDelta = delta,
            latestRateDelta = rateDelta,
            stability = stability,
            slopePerExam = slope,
            trend = trendOf(rates, TREND_THRESHOLD, VOLATILITY_THRESHOLD),
        )
    }

    /** values 为按时间排序的序列（原始分或得分率均可） */
    fun trendOf(values: List<Double>, trendThreshold: Double, volatilityThreshold: Double): Trend {
        if (values.size < 2) return Trend.INSUFFICIENT
        val slopeValue = slope(values)
        // 用“偏离趋势线的残差”衡量波动，避免把稳定上升误判为波动
        val deviation = residualStdDev(values, slopeValue)
        return when {
            deviation > volatilityThreshold -> Trend.FLUCTUATING
            slopeValue > trendThreshold -> Trend.RISING
            slopeValue < -trendThreshold -> Trend.FALLING
            else -> Trend.STABLE
        }
    }

    /** 相对于线性趋势的残差标准差 */
    fun residualStdDev(values: List<Double>, slopeValue: Double): Double {
        if (values.size < 2) return 0.0
        val intercept = values.average() - slopeValue * (values.size - 1) / 2.0
        val residuals = values.mapIndexed { index, value -> value - (intercept + slopeValue * index) }
        return stdDev(residuals)
    }

    fun mean(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.average()

    fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val m = values.average()
        return sqrt(values.sumOf { (it - m) * (it - m) } / values.size)
    }

    /** 最小二乘斜率（每个“考试间隔”的变化量） */
    fun slope(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val n = values.size
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var num = 0.0
        var den = 0.0
        values.forEachIndexed { index, y ->
            val dx = index - meanX
            num += dx * (y - meanY)
            den += dx * dx
        }
        return if (abs(den) < 1e-9) 0.0 else num / den
    }
}
