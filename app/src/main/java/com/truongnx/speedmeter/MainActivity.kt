package com.truongnx.speedmeter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.truongnx.speedmeter.core.IndicatorPrefs
import com.truongnx.speedmeter.overlay.OverlayService

class MainActivity : AppCompatActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                ensureBatteryOptPermissionThen {
                    startOverlayService()
                }
            } else {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show()
            }
        }

    private val batteryOptLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(
                    this,
                    "Battery optimization not disabled — overlay may stop unexpectedly",
                    Toast.LENGTH_LONG
                ).show()
            }
            startOverlayService() // continue anyway
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        applyWindowInsets()

        // Check for last crash
        val crashPrefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val lastCrash = crashPrefs.getString("last_crash", null)
        if (lastCrash != null) {
            // Log it to Logcat
            android.util.Log.e("LastCrash", lastCrash)

            // Optional: show a simple dialog in dev builds
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Last crash report")
                .setMessage(lastCrash.take(4000)) // avoid insanely long dialog
                .setPositiveButton("OK") { _, _ -> }
                .setNegativeButton("Clear") { _, _ ->
                    crashPrefs.edit().remove("last_crash").apply()
                }
                .show()
        }


        val startButton = findViewById<Button>(R.id.btnStart)
        val stopButton = findViewById<Button>(R.id.btnStop)
        val intervalEdit = findViewById<EditText>(R.id.edtInterval)
        val settingsButton = findViewById<Button>(R.id.btnSettings)

        // Show what is actually saved rather than a hardcoded placeholder.
        intervalEdit.setText(
            IndicatorPrefs.get(this)
                .getLong(IndicatorPrefs.KEY_INTERVAL, IndicatorPrefs.DEF_INTERVAL_MS)
                .toString()
        )

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        startButton.setOnClickListener {
            // 1) read interval from field
            val raw = intervalEdit.text.toString()
            val intervalMs = (raw.toLongOrNull() ?: IndicatorPrefs.DEF_INTERVAL_MS)
                .coerceIn(100L, 10000L)   // between 100ms and 10s

            // 2) save to prefs
            IndicatorPrefs.get(this)
                .edit()
                .putLong(IndicatorPrefs.KEY_INTERVAL, intervalMs)
                .apply()

            Toast.makeText(this, "Interval: ${intervalMs}ms", Toast.LENGTH_SHORT).show()

            // 3) permissions + start
            ensureOverlayPermissionThen {
                ensureBatteryOptPermissionThen {
                    startOverlayService()
                }
            }
        }

        stopButton.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Stopped", Toast.LENGTH_SHORT).show()
        }
    }

    /** Edge-to-edge is enforced at targetSdk 36, so inset the bar and content ourselves. */
    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.mainRoot)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val content = findViewById<View>(R.id.rootLayout)
        val toolbarHeight = toolbar.layoutParams.height
        // Keep the layout's own 24dp padding; only add the system bar on top of it.
        val basePadding = content.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )
            toolbar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            toolbar.layoutParams = toolbar.layoutParams.also { it.height = toolbarHeight + bars.top }
            content.updatePadding(bottom = basePadding + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun ensureOverlayPermissionThen(onGranted: () -> Unit) {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            Toast.makeText(this, "Please enable 'Appear on top'", Toast.LENGTH_LONG).show()
            overlaySettingsLauncher.launch(intent)
        } else onGranted()
    }

    private fun ensureBatteryOptPermissionThen(onGranted: () -> Unit) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                Toast.makeText(this, "Please allow 'Ignore battery optimizations'", Toast.LENGTH_LONG).show()
                batteryOptLauncher.launch(intent)
                return
            }
        }
        onGranted()
    }

    private fun startOverlayService() {
        startService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }
}
