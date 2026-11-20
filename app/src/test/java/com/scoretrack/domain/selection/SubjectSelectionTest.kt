package com.scoretrack.domain.selection

import com.scoretrack.core.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectSelectionTest {

    private val first = Subject.firstChoiceOptions()
    private val second = Subject.secondChoiceOptions()

    @Test
    fun `首选只有物理与历史`() {
        assertEquals(2, first.size)
        assertTrue(first.contains(Subject.PHYSICS))
        assertTrue(first.contains(Subject.HISTORY))
    }

    @Test
    fun `再选只有生物地理政治化学`() {
        assertEquals(4, second.size)
        assertEquals(
            setOf(Subject.BIOLOGY, Subject.GEOGRAPHY, Subject.POLITICS, Subject.CHEMISTRY),
            second.toSet(),
        )
    }

    @Test
    fun `十二种合法组合全部通过校验且互不重复`() {
        val combos = SelectionRules.allCombinations()
        assertEquals(12, combos.size)
        assertEquals(12, combos.map { it.key }.toSet().size)
        combos.forEach { combo ->
            val result = SelectionRules.validate(combo.first, listOf(combo.second1, combo.second2))
            assertTrue("组合 ${combo.label} 应合法", result.ok)
            assertTrue(combo.label.contains("+"))
        }
        // 2 首选 × C(4,2) = 12
        assertEquals(12, first.size * 6)
    }

    @Test
    fun `全部十二种组合都能生成完整选科对象`() {
        SelectionRules.allCombinations().forEach { combo ->
            val selection = SubjectSelection("s1", combo.first, listOf(combo.second1, combo.second2))
            assertTrue(selection.isComplete)
            assertEquals(3, selection.selectedSubjects.size)
            assertEquals(0, selection.missingCount)
            assertEquals(combo.label, selection.comboLabel)
        }
    }

    @Test
    fun `缺少首选不合法`() {
        val result = SelectionRules.validate(null, listOf(Subject.BIOLOGY, Subject.CHEMISTRY))
        assertFalse(result.ok)
        assertEquals(1, result.missing)
        assertNotNull(result.message)
    }

    @Test
    fun `首选不能是再选科目`() {
        val result = SelectionRules.validate(Subject.BIOLOGY, listOf(Subject.CHEMISTRY, Subject.GEOGRAPHY))
        assertFalse(result.ok)
        assertEquals(1, result.missing)
    }

    @Test
    fun `再选数量不足不合法`() {
        val result = SelectionRules.validate(Subject.PHYSICS, listOf(Subject.BIOLOGY))
        assertFalse(result.ok)
        assertEquals(1, result.missing)
    }

    @Test
    fun `再选超过两门不合法`() {
        val result = SelectionRules.validate(
            Subject.PHYSICS,
            listOf(Subject.BIOLOGY, Subject.CHEMISTRY, Subject.GEOGRAPHY),
        )
        assertFalse(result.ok)
    }

    @Test
    fun `再选重复不合法`() {
        val result = SelectionRules.validate(Subject.PHYSICS, listOf(Subject.BIOLOGY, Subject.BIOLOGY))
        assertFalse(result.ok)
    }

    @Test
    fun `再选混入首选科目不合法`() {
        val result = SelectionRules.validate(Subject.PHYSICS, listOf(Subject.HISTORY, Subject.BIOLOGY))
        assertFalse(result.ok)
        val result2 = SelectionRules.validate(Subject.PHYSICS, listOf(Subject.PHYSICS, Subject.BIOLOGY))
        assertFalse(result2.ok)
    }

    @Test
    fun `未完成选科时 selectedSubjects 为空且提示剩余数量`() {
        val selection = SubjectSelection("s1", Subject.PHYSICS, listOf(Subject.BIOLOGY))
        assertFalse(selection.isComplete)
        assertTrue(selection.selectedSubjects.isEmpty())
        assertEquals(1, selection.missingCount)
        assertEquals("尚未完成选科", selection.comboLabel)
    }

    @Test
    fun `选考总分按三科求和且缺失不按零计算`() {
        val selection = SubjectSelection("s1", Subject.PHYSICS, listOf(Subject.CHEMISTRY, Subject.BIOLOGY))
        val full = mapOf<Subject, Double?>(
            Subject.CHINESE to 120.0,
            Subject.MATH to 130.0,
            Subject.ENGLISH to 125.0,
            Subject.PHYSICS to 88.0,
            Subject.CHEMISTRY to 92.0,
            Subject.BIOLOGY to 90.0,
            Subject.GEOGRAPHY to 70.0,
        )
        val total = ElectiveTotalCalculator.total(full, selection)
        assertEquals(270.0, total.total!!, 0.001)
        assertEquals(300.0, total.maxTotal, 0.001)
        assertTrue(total.missing.isEmpty())

        // 缺一门选考科目 → 不计算总分（绝不按 0 分补）
        val partial = full - Subject.BIOLOGY
        val partialTotal = ElectiveTotalCalculator.total(partial, selection)
        assertNull(partialTotal.total)
        assertEquals(listOf(Subject.BIOLOGY), partialTotal.missing)

        // 未选科目（地理）不该参与选考总分
        val withGeo = ElectiveTotalCalculator.total(full + (Subject.GEOGRAPHY to 100.0), selection)
        assertEquals(270.0, withGeo.total!!, 0.001)
    }

    @Test
    fun `未完成选科时选考总分不可用`() {
        val selection = SubjectSelection("s1", Subject.PHYSICS, emptyList())
        val total = ElectiveTotalCalculator.total(
            mapOf(Subject.PHYSICS to 90.0, Subject.BIOLOGY to 90.0, Subject.CHEMISTRY to 90.0),
            selection,
        )
        assertNull(total.total)
    }

    @Test
    fun `赋分与原始分标记符合3+1+2`() {
        assertTrue(Subject.PHYSICS.isScaled.not())
        assertTrue(Subject.HISTORY.isScaled.not())
        assertTrue(Subject.BIOLOGY.isScaled)
        assertTrue(Subject.CHEMISTRY.isScaled)
        assertTrue(Subject.GEOGRAPHY.isScaled)
        assertTrue(Subject.POLITICS.isScaled)
        // 原有六科顺序与满分不变
        assertEquals(6, Subject.displayOrder().size)
        assertEquals(750.0, Subject.TOTAL_MAX_SCORE, 0.001)
        assertEquals(9, Subject.allSubjects().size)
    }
}
