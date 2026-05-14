package com.truongnx.speedmeter.overlay

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.truongnx.speedmeter.R
import com.truongnx.speedmeter.core.SpeedMeter
import com.truongnx.speedmeter.core.SpeedSnapshot
import com.truongnx.speedmeter.core.getActiveNetType
import kotlinx.coroutines.*

class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var wm: WindowManager
    private var rootView: View? = null
    private var txt: TextView? = null
    private val speed = SpeedMeter()
    private var lastSnap: SpeedSnapshot? = null

    private val prefs by lazy {
        getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
    }

    val windowMs = 1000L
    private var sumDown = 0L
    private var sumUp = 0L
    private var count = 0

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        initOverlay()
        startLoop()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        rootView?.let { wm.removeView(it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val channelId = "net_speed_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Network Speed", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle("Network speed monitor running")
            .build()
        startForeground(1, notif)
    }

    private fun initOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_speed, null)
        txt = view.findViewById(R.id.txtSpeed)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("pos_x", 0)
            y = prefs.getInt("pos_y", 0)
        }

        view.setOnTouchListener(Dragger(params, wm, prefs))
        wm.addView(view, params)
        rootView = view
    }

    private fun startLoop() {
        scope.launch {
            try {
                while (isActive) {
                    val netType = getActiveNetType(this@OverlayService).label
                    val snap = speed.sample()
                    val avg = lastSnap?.let {
                        SpeedSnapshot(
                            (it.downBytesPerSec + snap.downBytesPerSec) / 2,
                            (it.upBytesPerSec + snap.upBytesPerSec) / 2
                        )
                    } ?: snap
                    lastSnap = snap
                    withContext(Dispatchers.Main) {
                        txt?.text = "$netType ${SpeedMeter.humanBytesPerSec(avg.downBytesPerSec)} ↓ " +
                                "${SpeedMeter.humanBytesPerSec(avg.upBytesPerSec)} ↑"
                    }

                    val intervalMs = prefs.getLong("update_interval_ms", 1000L)
                        .coerceIn(100L, 10000L)
                    delay(intervalMs)
                }
            } catch (e: Exception) {
                Log.e("Overlay Service", "Crashed", e)
            }
        }
    }
}

private class Dragger(
    private val params: WindowManager.LayoutParams,
    private val wm: WindowManager,
    private val prefs: SharedPreferences
) : View.OnTouchListener {

    private var startX = 0
    private var startY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var moved = false

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = params.x
                startY = params.y
                touchStartX = event.rawX
                touchStartY = event.rawY
                moved = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - touchStartX).toInt()
                val dy = (event.rawY - touchStartY).toInt()
                if (dx * dx + dy * dy > 9) moved = true
                params.x = startX + dx
                params.y = startY + dy
                wm.updateViewLayout(v, params)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (moved) {
                    prefs.edit()
                        .putInt("pos_x", params.x)
                        .putInt("pos_y", params.y)
                        .apply()
                }
                return true
            }
        }
        return false
    }
}
