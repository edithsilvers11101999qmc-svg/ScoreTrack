package com.scoretrack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorHexTest {

    @Test
    fun `parses six digit hex`() {
        assertEquals(0xFF2F5FA8L, ColorHex.parse("#2F5FA8"))
        assertEquals(0xFF2F5FA8L, ColorHex.parse("2f5fa8"))
    }

    @Test
    fun `parses three and eight digit hex`() {
        assertEquals(0xFFFF0000L, ColorHex.parse("#F00"))
        assertEquals(0x802F5FA8L, ColorHex.parse("#802F5FA8"))
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(ColorHex.parse(""))
        assertNull(ColorHex.parse("#12"))
        assertNull(ColorHex.parse("#GGGGGG"))
        assertNull(ColorHex.parse("hello"))
    }

    @Test
    fun `normalize returns canonical uppercase hex`() {
        assertEquals("#2F5FA8", ColorHex.normalize("#2f5fa8"))
        assertEquals("#FF0000", ColorHex.normalize("#f00"))
        assertNull(ColorHex.normalize("nope"))
    }

    @Test
    fun `dark color gets light foreground`() {
        val onDark = ColorHex.onColor(0xFF1B4F72L)
        assertTrue(ColorHex.luminance(onDark) > 0.5)
        val onLight = ColorHex.onColor(0xFFFFF4C2L)
        assertTrue(ColorHex.luminance(onLight) < 0.5)
    }

    @Test
    fun `blend moves toward target`() {
        val blended = ColorHex.blend(0xFF000000L, 0xFFFFFFFFL, 0.5)
        val value = (blended shr 16 and 0xFF).toInt()
        assertTrue(value in 120..136)
    }
}
