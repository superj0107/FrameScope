# FrameScope

[中文说明](README.zh-CN.md) · English

![FrameScope Banner](docs/assets/banner.png)

> Rootless Android performance overlay featuring live FPS metering, thermal diagnostics, and privileged gaming optimization via the built-in FrameScope Bridge.

> **Fork note:** FrameScope is a derivative of the MIT-licensed [FrameX-Android](https://github.com/MaheshSharan/FrameX-Android). This fork renames the application and adds an Android 8.1-compatible foreground-layer FPS reader based on `SurfaceFlinger --latency`.

<p align="center">
  <a href="https://github.com/superj0107/FrameScope/releases/latest">
    <img src="https://img.shields.io/github/downloads/superj0107/FrameScope/total?style=for-the-badge&logo=android&label=Total%20Downloads&color=4CAF50" alt="Total Downloads"/>
  </a>
  <a href="https://github.com/superj0107/FrameScope/releases/tag/v0.1.0">
    <img src="https://img.shields.io/badge/Version-0.1.0-orange?style=for-the-badge&logo=github" alt="Version"/>
  </a>
  <a href="https://developer.android.com/about/versions/oreo">
    <img src="https://img.shields.io/badge/API-26%2B-brightgreen?style=for-the-badge&logo=android" alt="Min API"/>
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License"/>
  </a>
</p>

---
>  [!NOTE]
> **Gaming Performance Mode** is optimized specifically for **Android 16** and **Vivo OriginOS/FuntouchOS** devices (featuring hardware Mode 4 1080p @ 120Hz lock and 4 OEM Whitelists engine). For Vivo devices, enable **Vivo T3 Ultra Hardware Optimizations** in Settings (About) to unlock maximum gaming performance. Behavior on other Android skins may vary.

> [!IMPORTANT]
> **Encountering "Parse Failed" or "Unsupported Hardware"?**
>
> If thermal monitoring displays **"Parse Failed"** or **"Unsupported Hardware"** on your device:
> 1. Clone the repository and install the **Debug APK** (`./gradlew installDebug`). *(Release builds strip diagnostic logs via ProGuard).*
> 2. Open the **Thermal Diagnostics** screen in FrameScope.
> 3. Run the following ADB commands to capture complete diagnostics:
>    ```bash
>    # 1. Capture FrameScope internal thermal logs
>    adb logcat -d | grep -E "ThermalMonitor|CmdRunner"
>
>    # 2. Capture raw system thermal HAL dump
>    adb shell dumpsys thermalservice
>
>    # 3. Capture sysfs thermal zones (if dumpsys is empty/HAL Ready: false)
>    adb shell "for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type 2>/dev/null):\$(cat \$z/temp 2>/dev/null)\"; done"
>    ```
>    *(On Windows PowerShell for Step 1: `adb logcat -d | Select-String -Pattern "ThermalMonitor|CmdRunner"`)*
> 4. Open a GitHub Issue attaching the outputs above along with your **device model**, **SoC**, and **Android/ROM version**.

---
## What it does

FrameScope displays a fully customizable, low-overhead overlay on top of any application or full-screen title.

| Category | Telemetry & Features |
| :--- | :--- |
| **Real-Time Telemetry** | **FPS** · **CPU Frequencies** · **Thermals** *(CPU/GPU/Skin/NPU/Battery)* · **RAM Usage** · **Network Speed** · **Ping** |
| **System Optimization** | • **Crash-Safe Snapshots:** Automatic pre-game setting capture & auto-revert on exit<br>• **Process Management:** Background app suspension & Doze CPU whitelisting<br>• **Emergency Safety:** One-swipe reset slider to purge overrides back to stock OS defaults |
| **OEM Hardware Locks** | • **Vivo OriginOS Engine:** Forces hardware Mode 4 ($1080 \times 2400$ @ 120Hz) lock<br>• **Triple Whitelisting:** Automatic injection across 4 native OriginOS game-booster daemons |

---

## Requirements

- Android 8.0 (API 26) or higher
- English and Simplified Chinese in-app UI; the initial language follows the system language and can be changed in **About & Legal → Language**
- Basic monitoring works without the bridge after granting the draw-over-other-apps permission
- External-app FPS access, Gaming Mode, and deep optimization require the built-in FrameScope Bridge or optional [Shizuku](https://github.com/RikkaApps/Shizuku)
- The Bridge is started once through USB ADB; no computer connection is needed while it remains alive
- Works with the Sui module on rooted devices as an alternative backend

## Basic Monitoring vs. Deep Optimization

FrameScope separates its features into two levels:

- **Basic monitoring:** works without a privileged backend and provides CPU, RAM, storage, Ping, basic thermal status, and the overlay.
- **Deep optimization:** uses the FrameScope Bridge (or Shizuku fallback) for external-app SurfaceFlinger FPS access, Gaming Mode, background package suspension, refresh-rate locking, fixed performance mode, and Vivo hardware optimization.

FrameScope still opens normally without the Bridge. Detailed multi-sensor temperatures, external-app FPS access, and privileged system changes remain unavailable until the Bridge or Shizuku is running.

## FrameScope Bridge setup (recommended)

The Bridge is already inside the FrameScope APK. It is not a second app and does not require Shizuku. Because Android cannot let a normal app raise its own UID to `shell`, the Bridge must be started once by ADB.

### 1. Install FrameScope

```powershell
adb -s <device-serial> install -r <path-to-FrameScope.apk>
```

### 2. Start the embedded Bridge

The easiest way is to double-click **`启动FrameScope桥接服务.bat`** in the repository root. It automatically selects the only connected ADB watch; if multiple devices are connected, it asks for the watch serial number.

To stop the service, double-click **`停止FrameScope桥接服务.bat`**.

If Windows blocks the batch file, you can run the underlying PowerShell script manually from the repository root:

From the repository root in PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\start-framescope-bridge.ps1 -Serial <device-serial>
```

The same command is available in the app under **Permissions → Copy ADB Command**. Open FrameScope and confirm that **FrameScope Bridge** shows **RUNNING (SHELL)**. The computer can now be disconnected; the app talks to the Bridge through a local shared-storage mailbox.

### 3. After reboot

Run the start script again. Reinstalling FrameScope or installing Shizuku is not necessary. The click-to-run launchers are in the repository root, and the underlying PowerShell scripts are in [`scripts/`](scripts/). All of these files are included in the GitHub repository. See [scripts/README-Bridge.md](scripts/README-Bridge.md) for troubleshooting.

### Optional Shizuku fallback

Shizuku is still supported for users who already have it installed. Start Shizuku normally, grant FrameScope access, and FrameScope will use it when the embedded Bridge is unavailable.

---

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/assets/ui/onboard1.jpeg" width="150"/><br/><sub><b>Onboarding 1</b></sub></td>
    <td align="center"><img src="docs/assets/ui/onboard3.jpeg" width="150"/><br/><sub><b>Onboarding 2</b></sub></td>
    <td align="center"><img src="docs/assets/ui/dash-no-setup.jpeg" width="150"/><br/><sub><b>Dashboard (No Setup)</b></sub></td>
    <td align="center"><img src="docs/assets/ui/dash-setup-done.jpeg" width="150"/><br/><sub><b>Dashboard (Running)</b></sub></td>
    <td align="center"><img src="docs/assets/ui/setup_system.jpeg" width="150"/><br/><sub><b>System Setup</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/ui/overlay_config.jpeg" width="150"/><br/><sub><b>Overlay Config</b></sub></td>
    <td align="center"><img src="docs/assets/ui/appearance_settings.jpeg" width="150"/><br/><sub><b>Appearance Settings</b></sub></td>
    <td align="center"><img src="docs/assets/ui/perfomance.jpeg" width="150"/><br/><sub><b>Performance Mode</b></sub></td>
    <td align="center"><img src="docs/assets/ui/thermal.jpeg" width="150"/><br/><sub><b>Thermal Diagnostics</b></sub></td>
    <td align="center"><img src="docs/assets/ui/about-legal.jpeg" width="150"/><br/><sub><b>About & Legal</b></sub></td>
  </tr>
</table>

---

## How FPS is measured

FrameScope measures frame rates from the Android rendering pipeline using privileged IPC calls. It uses `SurfaceFlinger --latency` where the device exposes valid presentation timestamps; on the tested Android 8.1 firmware, where that command returns `0 0 0`, it automatically falls back to `dumpsys gfxinfo <foreground-package> framestats` and counts completed frames in a rolling one-second window. A static page can correctly show **0 FPS** because no new application frame was rendered; swipe, animate, or play video to measure active rendering. The monitor does not cap the value at 60.

![FPS Architecture Diagram](docs/assets/fps_measurement_architecture.svg)

---

## Build

Standard Android Gradle project. Requires **JDK 17** and **Android SDK 34**.

```bash
git clone https://github.com/superj0107/FrameScope.git
cd FrameScope-Android
./gradlew assembleDebug
```

## Download the tested APK

The repository includes the debug APK tested on an Android 8.1 watch:

- [FrameScope_v0.1.0-debug.apk](releases/FrameScope_v0.1.0-debug.apk)
- Package: `com.framescope.app`
- Minimum Android version: Android 8.0 (API 26)
- The basic overlay can start with the draw-over-other-apps permission; external-app FPS readings require FrameScope Bridge or Shizuku.


---

## Permissions

| Permission | Why |
|---|---|
| Draw over other apps | Display the overlay on top of games |
| Foreground service | Keep the overlay alive while the screen is on |
| Wake lock | Prevent CPU sleep during an active session |
| PACKAGE_USAGE_STATS | Identify which game is in the foreground |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Survive aggressive OEM background-kill policies |
| Receive boot completed | Auto-restart overlay after reboot if it was active |
| Internet | Ping measurement to `8.8.8.8` (Google Public DNS) only |
| Kill background processes | Used to purge cached background apps during Gaming Mode activation |
| Access notification policy | Required to toggle Do Not Disturb mode automatically |
| Modify system settings | Required to deploy per-game brightness, volume, and rotation overrides |
| Foreground service (Special Use) | Ensures Gaming Mode stays active on Android 14+ |
| Post Notifications (Android 13+) | Display session status & quick-control notification controls |
| QUERY_ALL_PACKAGES | Load installed games and apps for Game Launcher optimization |
| FrameScope Bridge / Shizuku | High-precision FPS metering (`SurfaceFlinger`), multi-sensor thermal diagnostics, ART RAM heap compaction, and OriginOS Esports hardware engine |

---

## Security & Privacy

- **No data collection** — nothing is sent anywhere
- **No analytics** — no Firebase, no Crashlytics, no tracking SDKs
- **No accounts** — FrameScope has no sign-in or user identity
- **No ads** — ever
- All data stays on-device

[Full Privacy Policy](https://superj0107.github.io/FrameScope/privacy-policy)

---

## Known Limitations

A small number of metrics depend on data some device vendors don't expose. See [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md) for confirmed cases and affected devices.

---

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) by [RikkaW](https://github.com/RikkaApps) — optional compatibility backend for privileged system access.

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
