package com.truongnx.speedmeter.core

import android.net.TrafficStats
import kotlin.math.max

data class SpeedSnapshot(
    val downBytesPerSec: Long, val upBytesPerSec: Long
)

class SpeedMeter {
    private var lastRx = TrafficStats.getTotalRxBytes()
    private var lastTx = TrafficStats.getTotalTxBytes()
    private var lastTime = System.currentTimeMillis()

    fun sample(): SpeedSnapshot {
        val nowRx = TrafficStats.getTotalRxBytes()
        val nowTx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()
        val dt = max(1, now - lastTime)

        val dRx = (nowRx - lastRx).coerceAtLeast(0)
        val dTx = (nowTx - lastTx).coerceAtLeast(0)

        lastRx = nowRx
        lastTx = nowTx
        lastTime = now

        val down = dRx * 1000L / dt
        val up = dTx * 1000L / dt
        return SpeedSnapshot(down, up)
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
