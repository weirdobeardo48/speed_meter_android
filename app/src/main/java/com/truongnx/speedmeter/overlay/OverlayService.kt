package com.truongnx.speedmeter.overlay

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.*
import android.util.Log
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.truongnx.speedmeter.R
import com.truongnx.speedmeter.core.SpeedMeter
import com.truongnx.speedmeter.core.getActiveNetType
import kotlinx.coroutines.*

class OverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private val screenSize = Point()
    private var rootView: View? = null
    private var txt: TextView? = null
    private val speed = SpeedMeter()

    private val prefs by lazy {
        getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
    }

    private val rotationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshScreenSize()
            repositionFromPrefs()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        initOverlay()
        startLoop()
        registerReceiver(rotationReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(rotationReceiver)
        scope.cancel()
        rootView?.let { wm.removeView(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) =
        START_STICKY

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

    private fun refreshScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            wm.currentWindowMetrics.bounds.let { screenSize.set(it.width(), it.height()) }
        else
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getSize(screenSize)
    }

    private fun repositionFromPrefs() {
        val view = rootView ?: return
        val xFrac = prefs.getFloat("pos_x_frac", 0f)
        val yFrac = prefs.getFloat("pos_y_frac", 0f)
        params.x = (xFrac * (screenSize.x - view.width)).toInt().coerceAtLeast(0)
        params.y = (yFrac * (screenSize.y - view.height)).toInt().coerceAtLeast(0)
        wm.updateViewLayout(view, params)
    }

    private fun initOverlay() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_speed, null)
        txt = view.findViewById(R.id.txtSpeed)

        refreshScreenSize()

        val xFrac = prefs.getFloat("pos_x_frac", 0f)
        val yFrac = prefs.getFloat("pos_y_frac", 0f)

        params = WindowManager.LayoutParams(
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
            x = (xFrac * screenSize.x).toInt()
            y = (yFrac * screenSize.y).toInt()
        }

        view.setOnTouchListener(Dragger(params, wm, prefs, screenSize))
        wm.addView(view, params)
        rootView = view

        // Fine-tune once the view is measured so we can subtract its actual size
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                repositionFromPrefs()
            }
        })
    }

    private fun startLoop() {
        scope.launch {
            while (isActive) {
                try {
                    val netType = getActiveNetType(this@OverlayService).label
                    val snap = speed.sample()
                    withContext(Dispatchers.Main) {
                        txt?.text = "$netType ${SpeedMeter.humanBytesPerSec(snap.downBytesPerSec)} ↓ " +
                                "${SpeedMeter.humanBytesPerSec(snap.upBytesPerSec)} ↑"
                    }
                } catch (e: Exception) {
                    Log.e("OverlayService", "Loop error, retrying", e)
                    delay(2000)
                }

                val intervalMs = prefs.getLong("update_interval_ms", 1000L)
                    .coerceIn(100L, 10000L)
                delay(intervalMs)
            }
        }
    }
}

private class Dragger(
    private val params: WindowManager.LayoutParams,
    private val wm: WindowManager,
    private val prefs: SharedPreferences,
    private val screenSize: Point
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
                params.x = (startX + dx).coerceIn(0, screenSize.x - v.width)
                params.y = (startY + dy).coerceIn(0, screenSize.y - v.height)
                wm.updateViewLayout(v, params)
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (moved) {
                    val xRange = (screenSize.x - v.width).toFloat().coerceAtLeast(1f)
                    val yRange = (screenSize.y - v.height).toFloat().coerceAtLeast(1f)
                    prefs.edit()
                        .putFloat("pos_x_frac", params.x / xRange)
                        .putFloat("pos_y_frac", params.y / yRange)
                        .apply()
                }
                return true
            }
        }
        return false
    }
}
