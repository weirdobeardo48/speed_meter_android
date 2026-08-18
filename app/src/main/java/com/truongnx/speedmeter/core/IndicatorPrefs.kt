package com.truongnx.speedmeter.core

import android.content.Context
import android.content.SharedPreferences

/**
 * The single place that knows the overlay pref key names and their defaults.
 *
 * Thresholds are stored as canonical bytes/sec Longs; the settings screen converts to and
 * from whatever unit the user picked and remembers that choice in a parallel `*_unit` key.
 * Colours are stored as packed ARGB Ints — never as hex strings, so nothing has to be
 * parsed on the render path.
 */
object IndicatorPrefs {

    const val NAME = "overlay_prefs"

    const val KEY_INTERVAL = "update_interval_ms"

    const val KEY_DOWN_LOW = "thr_down_low_bps"
    const val KEY_DOWN_HIGH = "thr_down_high_bps"
    const val KEY_UP_LOW = "thr_up_low_bps"
    const val KEY_UP_HIGH = "thr_up_high_bps"

    const val KEY_COLOR_DOWN_LOW = "color_down_low"
    const val KEY_COLOR_DOWN_NORMAL = "color_down_normal"
    const val KEY_COLOR_DOWN_HIGH = "color_down_high"
    const val KEY_COLOR_UP_LOW = "color_up_low"
    const val KEY_COLOR_UP_NORMAL = "color_up_normal"
    const val KEY_COLOR_UP_HIGH = "color_up_high"
    const val KEY_COLOR_NET_LABEL = "color_net_label"

    /** Suffix for the display-unit companion of a threshold key. Ignored by the service. */
    const val SUFFIX_UNIT = "_unit"

    const val KB = 1024L
    const val MB = 1024L * 1024L

    /** Index into these matches the unit spinner in the settings screen. */
    val UNIT_MULTIPLIERS = longArrayOf(1L, KB, MB)
    val UNIT_LABELS = arrayOf("B/s", "KB/s", "MB/s")
    const val UNIT_B = 0
    const val UNIT_KB = 1
    const val UNIT_MB = 2

    const val DEF_INTERVAL_MS = 1000L

    const val DEF_DOWN_LOW = 10L * KB          // 10 KB/s
    const val DEF_DOWN_HIGH = 10L * MB         // 10 MB/s
    const val DEF_UP_LOW = 0L                  // disabled
    const val DEF_UP_HIGH = 5L * MB            // 5 MB/s

    const val DEF_DOWN_LOW_UNIT = UNIT_KB
    const val DEF_DOWN_HIGH_UNIT = UNIT_MB
    const val DEF_UP_LOW_UNIT = UNIT_KB
    const val DEF_UP_HIGH_UNIT = UNIT_MB

    val DEF_COLOR_DOWN_LOW = 0xFFFF5252.toInt()     // red
    val DEF_COLOR_DOWN_NORMAL = 0xFFFFFFFF.toInt()  // white
    val DEF_COLOR_DOWN_HIGH = 0xFFFFB74D.toInt()    // orange
    val DEF_COLOR_UP_LOW = 0xFFFF5252.toInt()       // red
    val DEF_COLOR_UP_NORMAL = 0xFFFFFFFF.toInt()    // white
    val DEF_COLOR_UP_HIGH = 0xFFFF7043.toInt()      // deep orange
    val DEF_COLOR_NET_LABEL = 0xFFFFFFFF.toInt()    // white

    /** Used as the service's initial value, before the first load. */
    val DEFAULT_CONFIG = IndicatorConfig(
        down = DirectionRule(
            DEF_DOWN_LOW, DEF_DOWN_HIGH,
            DEF_COLOR_DOWN_LOW, DEF_COLOR_DOWN_NORMAL, DEF_COLOR_DOWN_HIGH
        ),
        up = DirectionRule(
            DEF_UP_LOW, DEF_UP_HIGH,
            DEF_COLOR_UP_LOW, DEF_COLOR_UP_NORMAL, DEF_COLOR_UP_HIGH
        ),
        netLabelColor = DEF_COLOR_NET_LABEL,
    )

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(p: SharedPreferences): IndicatorConfig = IndicatorConfig(
        down = DirectionRule(
            lowBytesPerSec = p.getLong(KEY_DOWN_LOW, DEF_DOWN_LOW),
            highBytesPerSec = p.getLong(KEY_DOWN_HIGH, DEF_DOWN_HIGH),
            lowColor = p.getInt(KEY_COLOR_DOWN_LOW, DEF_COLOR_DOWN_LOW),
            normalColor = p.getInt(KEY_COLOR_DOWN_NORMAL, DEF_COLOR_DOWN_NORMAL),
            highColor = p.getInt(KEY_COLOR_DOWN_HIGH, DEF_COLOR_DOWN_HIGH),
        ),
        up = DirectionRule(
            lowBytesPerSec = p.getLong(KEY_UP_LOW, DEF_UP_LOW),
            highBytesPerSec = p.getLong(KEY_UP_HIGH, DEF_UP_HIGH),
            lowColor = p.getInt(KEY_COLOR_UP_LOW, DEF_COLOR_UP_LOW),
            normalColor = p.getInt(KEY_COLOR_UP_NORMAL, DEF_COLOR_UP_NORMAL),
            highColor = p.getInt(KEY_COLOR_UP_HIGH, DEF_COLOR_UP_HIGH),
        ),
        netLabelColor = p.getInt(KEY_COLOR_NET_LABEL, DEF_COLOR_NET_LABEL),
    )
}
