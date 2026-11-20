package com.scoretrack.domain.goal

import kotlin.math.round

/**
 * 大学目标完成度（纯函数，便于单元测试）。
 * - 完成度 = 最近一次考试总分 / 目标总分 × 100%，保留 1 位小数，**不封顶**；
 * - 剩余分数 = max(0, 目标总分 - 最近总分)；
 * - 无考试数据时 percent 为 null（界面显示“等待录入首次考试成绩”）。
 */
data class GoalProgress(
    val latestTotal: Double?,
    val targetScore: Double,
    val percent: Double?,
    val remaining: Double,
    val achieved: Boolean,
) {
    val hasData: Boolean get() = percent != null
    val percentText: String
        get() = percent?.let { String.format(java.util.Locale.CHINA, "%.1f%%", it) } ?: "—"
    val remainingText: String
        get() = if (achieved) "目标已达成" else "还差 ${remaining.toInt()} 分"
}

fun computeGoalProgress(latestTotal: Double?, targetScore: Double): GoalProgress {
    val target = if (targetScore > 0) targetScore else 0.0
    if (latestTotal == null || target <= 0.0) {
        return GoalProgress(
            latestTotal = latestTotal,
            targetScore = target,
            percent = null,
            remaining = if (target > 0) target else 0.0,
            achieved = false,
        )
    }
    val raw = latestTotal / target * 100.0
    val percent = round(raw * 10) / 10.0
    return GoalProgress(
        latestTotal = latestTotal,
        targetScore = target,
        percent = percent,
        remaining = (target - latestTotal).coerceAtLeast(0.0),
        achieved = raw >= 100.0,
    )
}

/** 进度条填充比例（0..1），超过 100% 时填满。 */
fun GoalProgress.barFraction(): Float =
    ((percent ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
