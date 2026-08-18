package com.truongnx.speedmeter.core

/**
 * Hex colour parsing without `android.graphics.Color`, so it is unit-testable on the JVM
 * and so callers get a null instead of an exception on bad input.
 */
object HexColor {

    /** Accepts #RGB, #RRGGBB and #AARRGGBB (the leading # is optional). Null if invalid. */
    fun parse(input: String): Int? {
        val s = input.trim().removePrefix("#")
        if (s.isEmpty()) return null
        if (!s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val hex = when (s.length) {
            3 -> "FF" + s.flatMap { listOf(it, it) }.joinToString("")
            6 -> "FF$s"
            8 -> s
            else -> return null
        }
        // Parse as Long first: 0xFF...... overflows a signed Int.
        return hex.toLong(16).toInt()
    }

    fun format(color: Int): String = String.format("#%08X", color)
}
