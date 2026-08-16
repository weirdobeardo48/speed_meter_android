package com.truongnx.speedmeter.core

import org.junit.Assert.assertEquals
import org.junit.Test

private const val KB = 1024L
private const val MB = 1024L * 1024L

private const val LOW_COLOR = 0x11111111
private const val NORMAL_COLOR = 0x22222222
private const val HIGH_COLOR = 0x33333333

/** Mirrors the shipped download defaults: low 10 KB/s, high 10 MB/s. */
private val downRule = DirectionRule(
    lowBytesPerSec = 10 * KB,
    highBytesPerSec = 10 * MB,
    lowColor = LOW_COLOR,
    normalColor = NORMAL_COLOR,
    highColor = HIGH_COLOR,
)

/** Mirrors the shipped upload defaults: low disabled, high 5 MB/s. */
private val upRule = downRule.copy(lowBytesPerSec = 0L, highBytesPerSec = 5 * MB)

class SpeedIndicatorTest {

    @Test
    fun idleIsLow() {
        // Explicit product decision: 0 B/s is below the low threshold, so it reads as LOW.
        assertEquals(SpeedLevel.LOW, level(0, downRule))
        assertEquals(LOW_COLOR, SpeedIndicator.colorFor(0, downRule))
    }

    @Test
    fun justBelowLowIsLow() {
        assertEquals(SpeedLevel.LOW, level(10 * KB - 1, downRule))
    }

    @Test
    fun exactlyLowIsNormal() {
        assertEquals(SpeedLevel.NORMAL, level(10 * KB, downRule))
    }

    @Test
    fun midRangeIsNormal() {
        assertEquals(SpeedLevel.NORMAL, level(MB, downRule))
        assertEquals(NORMAL_COLOR, SpeedIndicator.colorFor(MB, downRule))
    }

    @Test
    fun exactlyHighIsNormal() {
        assertEquals(SpeedLevel.NORMAL, level(10 * MB, downRule))
    }

    @Test
    fun justAboveHighIsHigh() {
        assertEquals(SpeedLevel.HIGH, level(10 * MB + 1, downRule))
        assertEquals(HIGH_COLOR, SpeedIndicator.colorFor(10 * MB + 1, downRule))
    }

    @Test
    fun zeroLowDisablesTheLowRule() {
        // The shipped upload default: idle upload must stay NORMAL, not red.
        assertEquals(SpeedLevel.NORMAL, level(0, upRule))
        assertEquals(SpeedLevel.NORMAL, level(1, upRule))
    }

    @Test
    fun zeroHighDisablesTheHighRule() {
        val noHigh = downRule.copy(highBytesPerSec = 0L)
        assertEquals(SpeedLevel.NORMAL, level(100 * MB, noHigh))
    }

    @Test
    fun bothRulesDisabledIsAlwaysNormal() {
        val off = downRule.copy(lowBytesPerSec = 0L, highBytesPerSec = 0L)
        assertEquals(SpeedLevel.NORMAL, level(0, off))
        assertEquals(SpeedLevel.NORMAL, level(100 * MB, off))
    }

    @Test
    fun lowWinsWhenThresholdsAreInverted() {
        // Misconfigured low >= high must stay deterministic rather than ambiguous.
        val inverted = downRule.copy(lowBytesPerSec = 10 * MB, highBytesPerSec = MB)
        assertEquals(SpeedLevel.LOW, level(5 * MB, inverted))
        assertEquals(SpeedLevel.HIGH, level(20 * MB, inverted))
    }

    @Test
    fun uploadUsesItsOwnHighThreshold() {
        assertEquals(SpeedLevel.NORMAL, level(5 * MB, upRule))
        assertEquals(SpeedLevel.HIGH, level(5 * MB + 1, upRule))
    }

    private fun level(bps: Long, rule: DirectionRule) =
        SpeedIndicator.levelFor(bps, rule.lowBytesPerSec, rule.highBytesPerSec)
}
