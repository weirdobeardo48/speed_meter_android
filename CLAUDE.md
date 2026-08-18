# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run a single unit test class
./gradlew test --tests "com.truongnx.speedmeter.ExampleUnitTest"
```

## Architecture

Single-module Android app (minSdk 24, targetSdk 36) written in Kotlin. No MVVM/ViewModel — it's a small utility app.

**Core flow:**
1. `MainActivity` — handles runtime permissions (overlay, notifications, battery optimization), reads the update interval from UI, stores it to `overlay_prefs` SharedPreferences, then starts/stops `OverlayService`.
2. `OverlayService` (foreground `Service`) — adds a floating row of three `TextView`s (`txtNet`, `txtDown`, `txtUp`) over all apps via `WindowManager`, runs a coroutine loop that samples network speed, tints each direction, and updates the views. The update interval is read from `overlay_prefs` each tick; the indicator config is cached and refreshed via an `OnSharedPreferenceChangeListener`, so both take effect without restarting.
3. `SpeedMeter` — reads `TrafficStats.getTotalRxBytes/TxBytes`, diffs against last sample, returns a `SpeedSnapshot(downBytesPerSec, upBytesPerSec)`. Returns the previous snapshot until a 500 ms window has elapsed.
4. `NetworkType.kt` — detects active transport (Wi-Fi/Mobile/Ethernet/VPN) via `ConnectivityManager` and returns a short label shown in the overlay.
5. `SpeedIndicator.kt` — pure (no `android.*` imports, so it is JVM-unit-testable) mapping from a bytes/sec `Long` + a `DirectionRule` to a color. `bps < low` → low color, `bps > high` → high color, else normal; a threshold of `0` disables that rule. `HexColor.kt` is likewise pure and replaces `Color.parseColor` so bad input returns null instead of throwing.
6. `IndicatorPrefs.kt` — the only place pref key names and defaults live; `load()` builds the `IndicatorConfig` the service renders from.
7. `SettingsActivity` — edits thresholds (number + B/s·KB/s·MB/s unit spinner) and colors (preset swatch row + hex field). Save is the only writer of prefs, in one batch, so the service reloads once.
8. `BootReceiver` — restarts `OverlayService` after boot if overlay permission is still granted.
9. `CrashApp` (Application subclass) — installs a global uncaught-exception handler that persists the stacktrace to `crash_prefs`. `MainActivity` reads and displays it on next launch.

**SharedPreferences keys:**
- `overlay_prefs` → `update_interval_ms` (Long), `pos_x_frac` / `pos_y_frac` (Float, overlay drag position)
- `overlay_prefs` → thresholds `thr_{down,up}_{low,high}_bps` (Long, canonical bytes/sec) each with a display-only `…_unit` (Int, index into `B/s`,`KB/s`,`MB/s`)
- `overlay_prefs` → colors `color_{down,up}_{low,normal,high}` and `color_net_label` (Int, packed ARGB — never hex strings, so nothing is parsed on the render path)
- `crash_prefs` → `last_crash` (String)

**Permissions required at runtime:** `SYSTEM_ALERT_WINDOW` (overlay), `POST_NOTIFICATIONS` (Android 13+), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Key tech

- Kotlin coroutines (`kotlinx-coroutines-android 1.8.1`) for the polling loop in `OverlayService`
- No Jetpack Compose — layouts are XML (`main_activity.xml`, `overlay_speed.xml`, `settings_activity.xml` plus the `row_threshold.xml` / `row_color_picker.xml` includes)
- No `androidx.preference`; settings are plain `findViewById` widgets over raw `SharedPreferences`
- Build requires JDK 17+ (AGP 8.13); the shell default here is JDK 11, so prefix commands with `JAVA_HOME=/home/truongnx/dev/tools/jdk-21.0.2`
- AGP 8.13.0 / Kotlin 2.0.21
