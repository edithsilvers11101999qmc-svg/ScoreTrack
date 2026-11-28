package com.scoretrack.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试倒计时日期计算规则的单元测试。
 *
 * 覆盖：跨月、跨年、闰年、月底、考试当天（有 / 无具体时间、已开考、不足 1 分钟）、
 * 已过期、时区无关的自然日计算。
 */
class CountdownMathTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0, second: Int = 0): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone)

    @Test
    fun upcoming_countsNaturalDays() {
        val now = at(2026, 11, 15)
        val result = CountdownMath.evaluate(LocalDate.of(2026, 12, 15), null, now)
        assertEquals(CountdownPhase.UPCOMING, result.phase)
        assertEquals(30L, result.days)
        assertEquals("30", result.headlineValue)
        assertEquals("天", result.headlineUnit)
        assertFalse(result.isFinished)
    }

    @Test
    fun upcoming_acrossMonthEnd() {
        val now = at(2026, 1, 31, 23, 30)
        val result = CountdownMath.evaluate(LocalDate.of(2026, 2, 1), null, now)
        assertEquals(CountdownPhase.UPCOMING, result.phase)
        assertEquals(1L, result.days)
    }

    @Test
    fun upcoming_acrossYearEnd() {
        val now = at(2026, 12, 31, 22, 0)
        val result = CountdownMath.evaluate(LocalDate.of(2027, 1, 1), null, now)
        assertEquals(CountdownPhase.UPCOMING, result.phase)
        assertEquals(1L, result.days)
    }

    @Test
    fun upcoming_leapYearFeb29() {
        val now = at(2028, 2, 28, 12, 0)
        val result = CountdownMath.evaluate(LocalDate.of(2028, 2, 29), null, now)
        assertEquals(CountdownPhase.UPCOMING, result.phase)
        assertEquals(1L, result.days)

        val afterLeapDay = CountdownMath.evaluate(LocalDate.of(2028, 3, 1), null, now)
        assertEquals(2L, afterLeapDay.days)

        // 非闰年：2 月 28 日到 3 月 1 日只有 1 天
        val nonLeap = CountdownMath.evaluate(LocalDate.of(2027, 3, 1), null, at(2027, 2, 28, 12, 0))
        assertEquals(1L, nonLeap.days)
    }

    @Test
    fun upcoming_gaokaoDistance() {
        val now = at(2026, 11, 15, 20, 30)
        val result = CountdownMath.evaluate(LocalDate.of(2027, 6, 7), null, now)
        assertEquals(204L, result.days)
    }

    @Test
    fun today_withoutTime_showsTodayExam() {
        val now = at(2026, 11, 15, 6, 0)
        val result = CountdownMath.evaluate(LocalDate.of(2026, 11, 15), null, now)
        assertEquals(CountdownPhase.TODAY_NO_TIME, result.phase)
        assertTrue(result.isToday)
        assertFalse(result.isFinished)
        assertEquals("今天就是考试日", result.statusText)
    }

    @Test
    fun today_beforeStart_showsRemainingHoursAndMinutes() {
        val now = at(2026, 11, 15, 13, 45)
        // 16:00 开考
        val result = CountdownMath.evaluate(LocalDate.of(2026, 11, 15), 16 * 60, now)
        assertEquals(CountdownPhase.TODAY_UPCOMING, result.phase)
        assertEquals(2L, result.hours)
        assertEquals(15L, result.minutes)
        assertEquals("2 小时 15 分钟", result.remainingTimeText)
        assertTrue(result.isToday)
    }

    @Test
    fun today_lessThanOneMinute_showsOneMinute() {
        val now = at(2026, 11, 15, 15, 59, 30)
        val result = CountdownMath.evaluate(LocalDate.of(2026, 11, 15), 16 * 60, now)
        assertEquals(CountdownPhase.TODAY_UPCOMING, result.phase)
        assertEquals(0L, result.hours)
        assertEquals(1L, result.minutes)
        assertEquals("1 分钟", result.remainingTimeText)
    }

    @Test
    fun today_afterStart_showsOngoingInsteadOfNegative() {
        val now = at(2026, 11, 15, 16, 5)
        val result = CountdownMath.evaluate(LocalDate.of(2026, 11, 15), 16 * 60, now)
        assertEquals(CountdownPhase.TODAY_ONGOING, result.phase)
        assertEquals(0L, result.hours)
        assertEquals(0L, result.minutes)
        assertEquals("考试正在进行", result.statusText)
        assertFalse(result.isFinished)
    }

    @Test
    fun today_startedAtMidnight_isOngoingNotFinished() {
        val now = at(2026, 11, 15, 0, 1)
        val result = CountdownMath.evaluate(LocalDate.of(2026, 11, 15), 0, now)
        assertEquals(CountdownPhase.TODAY_ONGOING, result.phase)
        assertFalse(result.isFinished)
    }

    @Test
    fun yesterday_isFinishedWithDaysPast() {
        val now = at(2026, 11, 15, 8, 0)
        val result = CountdownMath.evaluate(LocalDate.of(2026, 11, 14), 16 * 60, now)
        assertEquals(CountdownPhase.FINISHED, result.phase)
        assertEquals(1L, result.daysPast)
        assertTrue(result.isFinished)
        assertFalse(result.isToday)
        assertEquals("考试已结束", result.statusText)
    }

    @Test
    fun isFinished_onlyForPastCalendarDays() {
        val now = at(2026, 11, 15, 23, 59)
        assertFalse(CountdownMath.isFinished(LocalDate.of(2026, 11, 15), now))
        assertTrue(CountdownMath.isFinished(LocalDate.of(2026, 11, 14), now))
        assertFalse(CountdownMath.isFinished(LocalDate.of(2026, 11, 16), now))
    }

    @Test
    fun startAt_usesLocalZoneAndMinutes() {
        val start = CountdownMath.startAt(LocalDate.of(2026, 11, 15), 8 * 60 + 30, zone)
        assertEquals(2026, start.year)
        assertEquals(8, start.hour)
        assertEquals(30, start.minute)
        assertEquals(zone, start.zone)
    }

    @Test
    fun timeHelpers_roundTrip() {
        assertEquals(570, CountdownMath.toMinutes(9, 30))
        assertEquals(9, CountdownMath.hourOf(570))
        assertEquals(30, CountdownMath.minuteOf(570))
        assertEquals("09:30", CountdownMath.formatTime(570))
        assertEquals("00:00", CountdownMath.formatTime(0))
        assertEquals("未设置", CountdownMath.formatTime(null))
    }

    @Test
    fun naturalDayCount_isIndependentOfTimeOfDay() {
        val morning = CountdownMath.evaluate(LocalDate.of(2026, 12, 1), null, at(2026, 11, 15, 0, 1))
        val night = CountdownMath.evaluate(LocalDate.of(2026, 12, 1), null, at(2026, 11, 15, 23, 58))
        assertEquals(16L, morning.days)
        assertEquals(16L, night.days)
    }
}
