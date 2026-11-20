package com.scoretrack.domain.analytics

import com.scoretrack.core.ExamRecord
import com.scoretrack.core.Subject
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEngineTest {

    private fun exam(
        id: String,
        name: String,
        date: String,
        chinese: Double?,
        math: Double?,
        english: Double?,
        physics: Double?,
        chemistry: Double?,
        geography: Double?,
    ) = ExamRecord(
        id = id,
        studentId = "s1",
        name = name,
        date = LocalDate.parse(date),
        scores = mapOf(
            Subject.CHINESE to chinese,
            Subject.MATH to math,
            Subject.ENGLISH to english,
            Subject.PHYSICS to physics,
            Subject.CHEMISTRY to chemistry,
            Subject.GEOGRAPHY to geography,
        ),
    )

    @Test
    fun `total only counts complete exams`() {
        val complete = exam("e1", "期中", "2026-09-10", 120.0, 130.0, 125.0, 80.0, 85.0, 80.0)
        val partial = exam("e2", "月考", "2026-10-10", 118.0, null, 122.0, 78.0, 84.0, 79.0)
        assertEquals(620.0, complete.total)
        assertNull(partial.total)

        val result = AnalyticsEngine.analyze(listOf(complete, partial))
        assertEquals(1, result.overall.completeExams)
        assertEquals(2, result.overall.totalExams)
        assertEquals(620.0, result.overall.latest)
    }

    @Test
    fun `rates are normalized by full score`() {
        val e = exam("e1", "期中", "2026-09-10", 120.0, 90.0, 135.0, 80.0, 90.0, 70.0)
        val result = AnalyticsEngine.analyze(listOf(e))
        val chinese = result.subjectStat(Subject.CHINESE)!!
        val physics = result.subjectStat(Subject.PHYSICS)!!
        assertEquals(0.8, chinese.currentRate!!, 0.0001)
        assertEquals(0.8, physics.currentRate!!, 0.0001)
        assertEquals(0.8, chinese.rateText.let { it.removeSuffix("%").toDouble() / 100 }, 0.0001)
        assertEquals(0.9, result.subjectStat(Subject.ENGLISH)!!.currentRate!!, 0.0001)
    }

    @Test
    fun `strengths are ranked by rate not raw score`() {
        // 语文 120/150 = 80%，物理 60/100 = 60%：虽然语文分更高，但 90/100 的化学更强
        val e = exam("e1", "期中", "2026-09-10", 120.0, 100.0, 90.0, 60.0, 95.0, 50.0)
        val result = AnalyticsEngine.analyze(listOf(e))
        val best = result.strengths.first()
        assertEquals(Subject.CHEMISTRY, best.subject)
        val worst = result.weaknesses.first()
        assertEquals(Subject.GEOGRAPHY, worst.subject)
    }

    @Test
    fun `single exam is not reported as rising trend`() {
        val e = exam("e1", "期中", "2026-09-10", 120.0, 130.0, 125.0, 80.0, 85.0, 80.0)
        val result = AnalyticsEngine.analyze(listOf(e))
        assertEquals(Trend.INSUFFICIENT, result.overall.trend)
        assertTrue(result.notes.isNotEmpty())
    }

    @Test
    fun `rising scores produce rising trend`() {
        val first = exam("e1", "第一次", "2026-09-10", 100.0, 100.0, 100.0, 60.0, 60.0, 60.0)
        val second = exam("e2", "第二次", "2026-10-10", 110.0, 115.0, 110.0, 70.0, 70.0, 70.0)
        val third = exam("e3", "第三次", "2026-11-10", 120.0, 125.0, 120.0, 80.0, 80.0, 80.0)
        val result = AnalyticsEngine.analyze(listOf(first, second, third))
        assertEquals(Trend.RISING, result.overall.trend)
        assertEquals(605.0, result.overall.latest)
        assertEquals(605.0, result.overall.best)
        assertNotNull(result.overall.latestDelta)
        assertTrue(result.overall.latestDelta!! > 0)
    }
}
