package com.truongnx.speedmeter

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.truongnx.speedmeter.core.HexColor
import com.truongnx.speedmeter.core.IndicatorPrefs
import java.util.Locale
import kotlin.math.floor

/**
 * Thresholds and colors for the overlay indicator.
 *
 * Nothing is written until Save: the service reloads its config from a prefs-change
 * listener, so persisting on every keystroke would push half-typed values straight onto
 * the screen and would make the cross-field (low < high) check impossible to express.
 */
class SettingsActivity : AppCompatActivity() {

    private val prefs by lazy { IndicatorPrefs.get(this) }

    private val presets = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt(), 0xFFFF5252.toInt(), 0xFFFF7043.toInt(),
        0xFFFFB74D.toInt(), 0xFFFFEE58.toInt(), 0xFF66BB6A.toInt(), 0xFF29B6F6.toInt(),
        0xFFAB47BC.toInt()
    )

    private inner class ThresholdSlot(
        rootId: Int,
        val key: String,
        val defaultBps: Long,
        val defaultUnit: Int,
        labelRes: Int
    ) {
        private val root: View = findViewById(rootId)
        val edit: EditText = root.findViewById(R.id.edtThreshold)
        val spinner: Spinner = root.findViewById(R.id.spnUnit)

        init {
            root.findViewById<TextView>(R.id.txtThresholdLabel).setText(labelRes)
            spinner.adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                IndicatorPrefs.UNIT_LABELS
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        fun populate(bps: Long, unitIndex: Int) {
            val unit = unitIndex.coerceIn(0, IndicatorPrefs.UNIT_LABELS.lastIndex)
            spinner.setSelection(unit)
            edit.error = null
            edit.setText(formatValue(bps.toDouble() / IndicatorPrefs.UNIT_MULTIPLIERS[unit]))
        }

        /** Bytes/sec, or null if the field is unusable (an error is set on it). */
        fun readBytesPerSec(): Long? {
            val value = edit.text.toString().trim().toDoubleOrNull()
            if (value == null || value < 0.0) {
                edit.error = getString(R.string.err_empty)
                return null
            }
            edit.error = null
            val mult = IndicatorPrefs.UNIT_MULTIPLIERS[spinner.selectedItemPosition]
            val bytes = value * mult
            return if (bytes >= Long.MAX_VALUE / 2.0) Long.MAX_VALUE / 2 else bytes.toLong()
        }
    }

    private inner class ColorSlot(
        rootId: Int,
        val key: String,
        val defaultColor: Int,
        labelRes: Int
    ) {
        private val root: View = findViewById(rootId)
        private val swatchRow: LinearLayout = root.findViewById(R.id.swatchRow)
        val edtHex: EditText = root.findViewById(R.id.edtHex)
        private val preview: View = root.findViewById(R.id.viewPreview)

        /** Last value that parsed cleanly, so this never holds garbage. */
        var color: Int = defaultColor
            private set

        init {
            root.findViewById<TextView>(R.id.txtColorLabel).setText(labelRes)

            val size = dp(36)
            val margin = dp(4)
            presets.forEach { preset ->
                val v = View(this@SettingsActivity)
                v.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = margin }
                v.tag = preset
                v.setOnClickListener { apply(preset, updateHexField = true) }
                swatchRow.addView(v)
            }

            edtHex.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val parsed = HexColor.parse(s?.toString().orEmpty())
                    if (parsed == null) {
                        edtHex.error = getString(R.string.err_bad_hex)
                    } else {
                        edtHex.error = null
                        // updateHexField = false: never rewrite the field the user is typing in.
                        apply(parsed, updateHexField = false)
                    }
                }
            })
        }

        fun populate(value: Int) = apply(value, updateHexField = true)

        fun hasError(): Boolean = HexColor.parse(edtHex.text.toString()) == null

        private fun apply(value: Int, updateHexField: Boolean) {
            color = value
            preview.background = swatchDrawable(value, selected = false)
            for (i in 0 until swatchRow.childCount) {
                val v = swatchRow.getChildAt(i)
                val tag = v.tag as Int
                v.background = swatchDrawable(tag, selected = tag == value)
            }
            if (updateHexField) {
                val text = HexColor.format(value)
                if (edtHex.text.toString() != text) edtHex.setText(text)
            }
        }
    }

    private lateinit var thresholds: List<ThresholdSlot>
    private lateinit var colors: List<ColorSlot>
    private lateinit var downLow: ThresholdSlot
    private lateinit var downHigh: ThresholdSlot
    private lateinit var upLow: ThresholdSlot
    private lateinit var upHigh: ThresholdSlot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        title = getString(R.string.title_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        applyWindowInsets()

        downLow = ThresholdSlot(
            R.id.rowDownLow, IndicatorPrefs.KEY_DOWN_LOW,
            IndicatorPrefs.DEF_DOWN_LOW, IndicatorPrefs.DEF_DOWN_LOW_UNIT, R.string.label_down_low
        )
        downHigh = ThresholdSlot(
            R.id.rowDownHigh, IndicatorPrefs.KEY_DOWN_HIGH,
            IndicatorPrefs.DEF_DOWN_HIGH, IndicatorPrefs.DEF_DOWN_HIGH_UNIT, R.string.label_down_high
        )
        upLow = ThresholdSlot(
            R.id.rowUpLow, IndicatorPrefs.KEY_UP_LOW,
            IndicatorPrefs.DEF_UP_LOW, IndicatorPrefs.DEF_UP_LOW_UNIT, R.string.label_up_low
        )
        upHigh = ThresholdSlot(
            R.id.rowUpHigh, IndicatorPrefs.KEY_UP_HIGH,
            IndicatorPrefs.DEF_UP_HIGH, IndicatorPrefs.DEF_UP_HIGH_UNIT, R.string.label_up_high
        )
        thresholds = listOf(downLow, downHigh, upLow, upHigh)

        colors = listOf(
            ColorSlot(
                R.id.rowColorDownLow, IndicatorPrefs.KEY_COLOR_DOWN_LOW,
                IndicatorPrefs.DEF_COLOR_DOWN_LOW, R.string.label_color_down_low
            ),
            ColorSlot(
                R.id.rowColorDownNormal, IndicatorPrefs.KEY_COLOR_DOWN_NORMAL,
                IndicatorPrefs.DEF_COLOR_DOWN_NORMAL, R.string.label_color_down_normal
            ),
            ColorSlot(
                R.id.rowColorDownHigh, IndicatorPrefs.KEY_COLOR_DOWN_HIGH,
                IndicatorPrefs.DEF_COLOR_DOWN_HIGH, R.string.label_color_down_high
            ),
            ColorSlot(
                R.id.rowColorUpLow, IndicatorPrefs.KEY_COLOR_UP_LOW,
                IndicatorPrefs.DEF_COLOR_UP_LOW, R.string.label_color_up_low
            ),
            ColorSlot(
                R.id.rowColorUpNormal, IndicatorPrefs.KEY_COLOR_UP_NORMAL,
                IndicatorPrefs.DEF_COLOR_UP_NORMAL, R.string.label_color_up_normal
            ),
            ColorSlot(
                R.id.rowColorUpHigh, IndicatorPrefs.KEY_COLOR_UP_HIGH,
                IndicatorPrefs.DEF_COLOR_UP_HIGH, R.string.label_color_up_high
            ),
            ColorSlot(
                R.id.rowColorNetLabel, IndicatorPrefs.KEY_COLOR_NET_LABEL,
                IndicatorPrefs.DEF_COLOR_NET_LABEL, R.string.label_color_net_label
            ),
        )

        populateFromPrefs()

        findViewById<Button>(R.id.btnSave).setOnClickListener { save() }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.confirm_reset)
                .setPositiveButton(R.string.action_reset) { _, _ -> populateDefaults() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * targetSdk 36 means the window is edge-to-edge on Android 15+, so nothing is inset for
     * us. Pad the toolbar for the status bar and the scroll area for the nav bar / keyboard.
     */
    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.settingsRoot)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val scroll = findViewById<View>(R.id.settingsScroll)
        val toolbarHeight = toolbar.layoutParams.height

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )
            toolbar.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            toolbar.layoutParams = toolbar.layoutParams.also { it.height = toolbarHeight + bars.top }
            scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun populateFromPrefs() {
        thresholds.forEach {
            it.populate(
                prefs.getLong(it.key, it.defaultBps),
                prefs.getInt(it.key + IndicatorPrefs.SUFFIX_UNIT, it.defaultUnit)
            )
        }
        colors.forEach { it.populate(prefs.getInt(it.key, it.defaultColor)) }
    }

    /** Loads defaults into the widgets only — Save stays the sole writer of prefs. */
    private fun populateDefaults() {
        thresholds.forEach { it.populate(it.defaultBps, it.defaultUnit) }
        colors.forEach { it.populate(it.defaultColor) }
        Toast.makeText(this, R.string.msg_reset, Toast.LENGTH_SHORT).show()
    }

    private fun save() {
        val values = HashMap<String, Long>()
        var firstBad: EditText? = null

        for (slot in thresholds) {
            val bps = slot.readBytesPerSec()
            if (bps == null) {
                if (firstBad == null) firstBad = slot.edit
            } else {
                values[slot.key] = bps
            }
        }
        for (slot in colors) {
            if (slot.hasError()) {
                slot.edtHex.error = getString(R.string.err_bad_hex)
                if (firstBad == null) firstBad = slot.edtHex
            }
        }

        // Only meaningful when both rules are enabled; low == 0 or high == 0 means "off".
        firstBad = checkOrder(downLow, downHigh, values) ?: firstBad
        firstBad = checkOrder(upLow, upHigh, values) ?: firstBad

        if (firstBad != null) {
            firstBad.requestFocus()
            return
        }

        prefs.edit().apply {
            for (slot in thresholds) {
                putLong(slot.key, values.getValue(slot.key))
                putInt(slot.key + IndicatorPrefs.SUFFIX_UNIT, slot.spinner.selectedItemPosition)
            }
            for (slot in colors) putInt(slot.key, slot.color)
        }.apply()

        Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun checkOrder(
        low: ThresholdSlot,
        high: ThresholdSlot,
        values: Map<String, Long>
    ): EditText? {
        val l = values[low.key] ?: return null
        val h = values[high.key] ?: return null
        if (l > 0L && h > 0L && l >= h) {
            low.edit.error = getString(R.string.err_low_ge_high)
            return low.edit
        }
        return null
    }

    private fun swatchDrawable(color: Int, selected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(6).toFloat()
        setColor(color)
        setStroke(
            dp(if (selected) 3 else 1),
            if (selected) 0xFF000000.toInt() else 0x40000000
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatValue(value: Double): String =
        if (value == floor(value) && !value.isInfinite()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
