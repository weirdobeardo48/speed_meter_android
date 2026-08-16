package com.truongnx.speedmeter.core

/** Which band a speed reading falls into. */
enum class SpeedLevel { LOW, NORMAL, HIGH }

/**
 * Coloring rule for one traffic direction. Thresholds are BYTES PER SECOND;
 * a threshold of 0 disables that rule.
 */
data class DirectionRule(
    val lowBytesPerSec: Long,
    val highBytesPerSec: Long,
    val lowColor: Int,
    val normalColor: Int,
    val highColor: Int,
)

data class IndicatorConfig(
    val down: DirectionRule,
    val up: DirectionRule,
    val netLabelColor: Int,
)

object SpeedIndicator {

    /**
     * Boundary semantics:
     *  - `bps < low` is LOW; exactly [low] is NORMAL.
     *  - `bps > high` is HIGH; exactly [high] is NORMAL.
     *  - `low == 0` disables the low rule, `high == 0` disables the high rule. That is how
     *    upload ships with its low rule off without needing a separate flag.
     *  - 0 B/s is deliberately NOT special-cased: with the default low of 10 KB/s an idle
     *    link renders in the low color.
     *  - LOW is tested first, so a misconfigured `low >= high` degrades to "everything below
     *    low is LOW" instead of being ambiguous.
     */
    fun levelFor(bps: Long, low: Long, high: Long): SpeedLevel = when {
        low > 0L && bps < low -> SpeedLevel.LOW
        high > 0L && bps > high -> SpeedLevel.HIGH
        else -> SpeedLevel.NORMAL
    }

    fun colorFor(bps: Long, rule: DirectionRule): Int =
        when (levelFor(bps, rule.lowBytesPerSec, rule.highBytesPerSec)) {
            SpeedLevel.LOW -> rule.lowColor
            SpeedLevel.HIGH -> rule.highColor
            SpeedLevel.NORMAL -> rule.normalColor
        }
}
