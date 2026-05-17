# Net Speed Meter

A lightweight Android utility that shows a floating network speed overlay on top of all apps.

The overlay displays real-time download and upload speeds along with the active connection type (Wi-Fi, Mobile, Ethernet, or VPN). It is draggable, persists its position across sessions, and restarts automatically after reboot.

---

## Features

- Floating overlay visible over any app
- Real-time download (`↓`) and upload (`↑`) speeds
- Network type indicator: `W` Wi-Fi · `M` Mobile · `E` Ethernet · `VPN` · `NIL` none
- Configurable update interval (100 – 10 000 ms)
- Drag to reposition; position saved automatically
- Auto-starts after device reboot
- Crash log captured to SharedPreferences and shown on next launch

---

## Requirements

- Android 7.0+ (API 24)
- Permissions: Draw over other apps, Post notifications (Android 13+), Battery optimization exemption

---

## Build & Install

```bash
# Debug build
./gradlew assembleDebug

# Build and install on connected device/emulator
./gradlew installDebug
```

---

## Usage

1. Launch **Net Speed Meter**.
2. Grant all requested permissions (overlay, notifications, battery optimization).
3. Set the desired update interval in milliseconds (default: 1000 ms).
4. Tap **Start** — a small overlay appears on screen.
5. Drag the overlay to any position; it stays there across app restarts.
6. Return to the app and tap **Stop** to dismiss the overlay.

The service is declared `START_STICKY` and will restart after reboot automatically (requires overlay permission still granted).

---

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the floating overlay over other apps |
| `FOREGROUND_SERVICE` | Keep the speed-polling service alive |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required by Android 14+ for special-use foreground services |
| `ACCESS_NETWORK_STATE` | Detect active network type (Wi-Fi / Mobile / etc.) |
| `POST_NOTIFICATIONS` | Show the persistent foreground service notification (Android 13+) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent the OS from killing the service in the background |
| `RECEIVE_BOOT_COMPLETED` | Restart the overlay service after reboot |

---

## Architecture

Single-module app written in Kotlin. No MVVM — it is a small utility.

| Component | Role |
|---|---|
| `MainActivity` | Permission flows, interval input, starts/stops `OverlayService` |
| `OverlayService` | Foreground service; manages the overlay view and coroutine polling loop |
| `SpeedMeter` | Reads `TrafficStats`, diffs Rx/Tx bytes, returns `SpeedSnapshot` (bytes/sec) |
| `NetworkType` | Detects active transport via `ConnectivityManager`; returns a short label |
| `BootReceiver` | `BroadcastReceiver` — restarts the service after `BOOT_COMPLETED` |
| `CrashApp` | `Application` subclass — installs a global exception handler that persists stack traces |

**Data flow:**
```
MainActivity → overlay_prefs (update_interval_ms)
                    ↓
OverlayService (coroutine loop, every N ms)
  ├── SpeedMeter.sample()       → SpeedSnapshot(downBytesPerSec, upBytesPerSec)
  └── getActiveNetType()        → NetType label
                    ↓
       TextView: "W 5.23 MB/s ↓ 1.45 MB/s ↑"
```

**SharedPreferences:**
- `overlay_prefs` — `update_interval_ms`, `pos_x_frac`, `pos_y_frac`
- `crash_prefs` — `last_crash`

---

## Tech Stack

| | |
|---|---|
| Language | Kotlin 2.0.21 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Build tools | AGP 8.13.0 |
| Async | kotlinx-coroutines-android 1.8.1 |
| UI | XML layouts, Material 3 (DayNight theme) |
| No Compose | Plain `View`-based UI |
