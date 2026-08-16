package com.truongnx.speedmeter.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {

    @Test
    fun parsesShortForm() {
        assertEquals(0xFFFFFFFF.toInt(), HexColor.parse("#FFF"))
        assertEquals(0xFF00FF00.toInt(), HexColor.parse("#0F0"))
    }

    @Test
    fun parsesSixDigitFormAsOpaque() {
        assertEquals(0xFFFF5252.toInt(), HexColor.parse("#FF5252"))
        assertEquals(0xFFFF5252.toInt(), HexColor.parse("ff5252"))
    }

    @Test
    fun parsesEightDigitFormWithAlpha() {
        assertEquals(0x80323232.toInt(), HexColor.parse("#80323232"))
    }

    @Test
    fun rejectsMalformedInput() {
        assertNull(HexColor.parse(""))
        assertNull(HexColor.parse("#"))
        assertNull(HexColor.parse("#12345"))
        assertNull(HexColor.parse("#GGGGGG"))
        assertNull(HexColor.parse("red"))
    }

    @Test
    fun formatRoundTrips() {
        val color = 0xFFFFB74D.toInt()
        assertEquals("#FFFFB74D", HexColor.format(color))
        assertEquals(color, HexColor.parse(HexColor.format(color)))
    }
}
