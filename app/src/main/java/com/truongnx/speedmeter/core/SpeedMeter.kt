package com.truongnx.speedmeter.core

import android.net.TrafficStats
import kotlin.math.max

data class SpeedSnapshot(
    val downBytesPerSec: Long, val upBytesPerSec: Long
)

class SpeedMeter {
    private val WINDOW_MS = 500L

    private var lastRx = TrafficStats.getTotalRxBytes()
    private var lastTx = TrafficStats.getTotalTxBytes()
    private var windowStart = System.currentTimeMillis()
    private var lastSnapshot = SpeedSnapshot(0L, 0L)

    fun sample(): SpeedSnapshot {
        val nowRx = TrafficStats.getTotalRxBytes()
        val nowTx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()

        // TrafficStats returns UNSUPPORTED on some devices/contexts
        if (nowRx < 0 || nowTx < 0) return lastSnapshot

        val dt = now - windowStart
        // Wait for at least WINDOW_MS so the kernel has had time to flush counters
        if (dt < WINDOW_MS) return lastSnapshot

        val dRx = (nowRx - lastRx).coerceAtLeast(0)
        val dTx = (nowTx - lastTx).coerceAtLeast(0)

        lastRx = nowRx
        lastTx = nowTx
        windowStart = now

        lastSnapshot = SpeedSnapshot(dRx * 1000L / dt, dTx * 1000L / dt)
        return lastSnapshot
    }

    companion object {
        fun humanBytesPerSec(bps: Long): String {
            val kb = 1024.0
            val mb = kb * 1024
            val gb = mb * 1024
            return when {
                bps >= gb -> String.format("%.2f GB/s", bps / gb)
                bps >= mb -> String.format("%.2f MB/s", bps / mb)
                bps >= kb -> String.format("%.0f KB/s", bps / kb)
                else -> "$bps B/s"
            }
        }
    }
}
