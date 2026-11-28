package com.scoretrack.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 倒计时所处阶段。
 *
 * 所有判断都以「设备当前时区」下的自然日为准，避免跨时区 / 夏令时带来的偏差。
 */
enum class CountdownPhase {
    /** 考试日期在未来：按自然日显示「还剩 N 天」。 */
    UPCOMING,

    /** 考试就是今天，且设置了具体时间、当前还没到开考时间：显示剩余小时 / 分钟。 */
    TODAY_UPCOMING,

    /** 考试就是今天，已过开考时间：显示「考试进行中」，不出现负数。 */
    TODAY_ONGOING,

    /** 考试就是今天，但没有设置具体时间：显示「今天考试」。 */
    TODAY_NO_TIME,

    /** 考试日期已经过去：归入历史，不再倒计时。 */
    FINISHED,
}

/**
 * 一次倒计时计算结果（纯数据，便于单元测试）。
 */
data class Countdown(
    val phase: CountdownPhase,
    val days: Long = 0L,
    val hours: Long = 0L,
    val minutes: Long = 0L,
    val daysPast: Long = 0L,
) {
    val isFinished: Boolean get() = phase == CountdownPhase.FINISHED

    val isToday: Boolean
        get() = phase == CountdownPhase.TODAY_UPCOMING ||
            phase == CountdownPhase.TODAY_ONGOING ||
            phase == CountdownPhase.TODAY_NO_TIME

    /** 卡片上的大号数字。 */
    val headlineValue: String
        get() = when (phase) {
            CountdownPhase.UPCOMING -> days.toString()
            CountdownPhase.TODAY_UPCOMING -> hours.toString().padStart(2, '0')
            else -> ""
        }

    val headlineUnit: String
        get() = when (phase) {
            CountdownPhase.UPCOMING -> "天"
            CountdownPhase.TODAY_UPCOMING -> "小时"
            else -> ""
        }

    /** 卡片副标题。 */
    val statusText: String
        get() = when (phase) {
            CountdownPhase.UPCOMING -> "距离考试还有"
            CountdownPhase.TODAY_UPCOMING -> "距离考试还有"
            CountdownPhase.TODAY_NO_TIME -> "今天就是考试日"
            CountdownPhase.TODAY_ONGOING -> "考试正在进行"
            CountdownPhase.FINISHED -> "考试已结束"
        }

    /** 当天设置了具体时间时的分钟描述，例如「2 小时 15 分钟」。 */
    val remainingTimeText: String
        get() = when {
            phase != CountdownPhase.TODAY_UPCOMING -> ""
            hours > 0 && minutes > 0 -> "$hours 小时 $minutes 分钟"
            hours > 0 -> "$hours 小时"
            else -> "$minutes 分钟"
        }
}

/**
 * 考试倒计时的日期计算规则（与界面无关，可独立测试）。
 *
 * - 以自然日为单位的剩余天数：`ChronoUnit.DAYS`（跨月、跨年、闰年、夏令时都按日历日计算）；
 * - 当天且设置了时间时，用带时区的 [ZonedDateTime] 求差得到小时 / 分钟；
 * - 只有「考试日期早于今天」才算结束，考试当天永远不会提前归入历史，也不会出现负数。
 */
object CountdownMath {

    fun evaluate(date: LocalDate, timeMinutes: Int?, now: ZonedDateTime): Countdown {
        val today = now.toLocalDate()
        return when {
            date.isBefore(today) -> Countdown(
                phase = CountdownPhase.FINISHED,
                daysPast = ChronoUnit.DAYS.between(date, today),
            )

            date.isAfter(today) -> Countdown(
                phase = CountdownPhase.UPCOMING,
                days = ChronoUnit.DAYS.between(today, date),
            )

            else -> {
                if (timeMinutes == null) {
                    Countdown(phase = CountdownPhase.TODAY_NO_TIME)
                } else {
                    val start = startAt(date, timeMinutes, now.zone)
                    if (!now.isBefore(start)) {
                        Countdown(phase = CountdownPhase.TODAY_ONGOING)
                    } else {
                        val totalMinutes = Duration.between(now, start).toMinutes().coerceAtLeast(0)
                        val h = totalMinutes / 60
                        val m = totalMinutes % 60
                        // 不足 1 分钟时按 1 分钟展示，避免出现「还剩 0 分钟」
                        val mm = if (h == 0L && m == 0L) 1L else m
                        Countdown(
                            phase = CountdownPhase.TODAY_UPCOMING,
                            hours = h,
                            minutes = mm,
                        )
                    }
                }
            }
        }
    }

    /** 是否已经结束（只有日期早于今天才算，考试当天始终算「今天」）。 */
    fun isFinished(date: LocalDate, now: ZonedDateTime): Boolean =
        date.isBefore(now.toLocalDate())

    /** 开考时刻：日期 + 当天分钟数，使用当前时区。 */
    fun startAt(date: LocalDate, timeMinutes: Int, zone: ZoneId): ZonedDateTime =
        date.atStartOfDay(zone).plusMinutes(timeMinutes.toLong())

    fun toMinutes(hour: Int, minute: Int): Int = hour * 60 + minute

    fun hourOf(timeMinutes: Int): Int = timeMinutes / 60

    fun minuteOf(timeMinutes: Int): Int = timeMinutes % 60

    fun formatTime(timeMinutes: Int?): String =
        if (timeMinutes == null) "未设置" else "%02d:%02d".format(hourOf(timeMinutes), minuteOf(timeMinutes))
}
