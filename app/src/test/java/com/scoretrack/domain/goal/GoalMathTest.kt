package com.scoretrack.domain.goal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMathTest {

    @Test
    fun `no exam data yields null percent`() {
        val progress = computeGoalProgress(null, 600.0)
        assertNull(progress.percent)
        assertEquals("—", progress.percentText)
        assertEquals(600.0, progress.remaining, 0.001)
        assertFalse(progress.achieved)
    }

    @Test
    fun `percent is not capped at 100`() {
        val progress = computeGoalProgress(660.0, 600.0)
        assertEquals(110.0, progress.percent!!, 0.001)
        assertEquals("110.0%", progress.percentText)
        assertTrue(progress.achieved)
        assertEquals(0.0, progress.remaining, 0.001)
        assertEquals(1f, progress.barFraction())
    }

    @Test
    fun `remaining score is target minus latest`() {
        val progress = computeGoalProgress(580.0, 650.0)
        assertEquals(70.0, progress.remaining, 0.001)
        assertEquals("还差 70 分", progress.remainingText)
        assertEquals(580f / 650f, progress.barFraction(), 0.0005f)
        assertFalse(progress.achieved)
    }

    @Test
    fun `zero target does not crash`() {
        val progress = computeGoalProgress(600.0, 0.0)
        assertNull(progress.percent)
        assertEquals(0f, progress.barFraction())
        assertFalse(progress.achieved)
    }
}
