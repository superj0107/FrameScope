# FrameScope

[English](README.md) · 中文说明

![FrameScope Banner](docs/assets/banner.png)

> FrameScope 是一款无需 Root 的 Android 性能悬浮窗工具，支持实时帧率（FPS）监测、温度诊断，以及通过内置 FrameScope 桥接服务提供的高级游戏优化功能。

> **项目说明：** FrameScope 基于采用 MIT 许可证的 [FrameX-Android](https://github.com/MaheshSharan/FrameX-Android) 开发。本项目重新命名了应用，并加入了兼容 Android 8.1 的前台图层 FPS 读取功能，底层使用 `SurfaceFlinger --latency` 获取帧时间戳。

<p align="center">
  <a href="https://github.com/superj0107/FrameScope/releases/latest">
    <img src="https://img.shields.io/github/downloads/superj0107/FrameScope/total?style=for-the-badge&logo=android&label=Total%20Downloads&color=4CAF50" alt="总下载量"/>
  </a>
  <a href="https://github.com/superj0107/FrameScope/releases/tag/v0.1.0">
    <img src="https://img.shields.io/badge/Version-0.1.0-orange?style=for-the-badge&logo=github" alt="版本"/>
  </a>
  <a href="https://developer.android.com/about/versions/oreo">
    <img src="https://img.shields.io/badge/API-26%2B-brightgreen?style=for-the-badge&logo=android" alt="最低 API"/>
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 版本"/>
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="许可证"/>
  </a>
</p>

---

> [!NOTE]
> **游戏性能模式**主要针对 **Android 16** 和 **Vivo OriginOS/FuntouchOS** 设备进行了优化，包括硬件 Mode 4 的 1080p @ 120Hz 锁定和 OEM 游戏加速白名单机制。在 Vivo 设备上，可在“设置 → 关于”中开启 **Vivo T3 Ultra 硬件优化**以获得最大游戏性能。其他 Android 系统上的行为可能有所不同。

> [!IMPORTANT]
> **遇到“解析失败（Parse Failed）”或“不支持的硬件（Unsupported Hardware）”怎么办？**
>
> 如果温度监测显示上述提示：
> 1. 克隆仓库并安装 **Debug APK**（`./gradlew installDebug`）。*Release 构建会通过 ProGuard 移除诊断日志。*
> 2. 打开 FrameScope 的 **温度诊断**页面。
> 3. 执行以下 ADB 命令收集完整诊断信息：
>    ```bash
>    # 1. 获取 FrameScope 内部温度日志
>    adb logcat -d | grep -E "ThermalMonitor|CmdRunner"
>
>    # 2. 获取系统温度 HAL 信息
>    adb shell dumpsys thermalservice
>
>    # 3. 获取 sysfs 温度区域信息（当 dumpsys 为空或 HAL Ready: false 时）
>    adb shell "for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type 2>/dev/null):\$(cat \$z/temp 2>/dev/null)\"; done"
>    ```
>    Windows PowerShell 中，第一条命令可使用：`adb logcat -d | Select-String -Pattern "ThermalMonitor|CmdRunner"`
> 4. 在 GitHub 提交 Issue，并附上以上输出、设备型号、SoC 型号以及 Android/ROM 版本。

## 功能

FrameScope 可在任意应用或全屏游戏上方显示可自定义、低开销的性能悬浮窗。

| 分类 | 监测与功能 |
| :--- | :--- |
| **实时监测** | **FPS** · **CPU 频率** · **温度**（CPU/GPU/机身/NPU/电池）· **RAM 使用量** · **网络速度** · **Ping** |
| **系统优化** | **防崩溃设置快照：** 游戏启动前自动保存设置并在退出后恢复；<br>**进程管理：** 挂起后台应用并将 CPU 加入 Doze 白名单；<br>**紧急恢复：** 通过滑动操作清除覆盖设置，恢复系统默认值 |
| **厂商硬件锁定** | **Vivo OriginOS 引擎：** 强制锁定硬件 Mode 4（$1080 \times 2400$ @ 120Hz）；<br>**多重白名单：** 自动注入 OriginOS 原生游戏加速服务 |

## 使用要求

- Android 8.0（API 26）或更高版本
- App 内支持 English 和简体中文；首次启动会跟随系统语言，也可以在 **关于与法律信息 → 语言** 中手动切换
- 基础监测只需要“显示在其他应用上层”权限，不启动桥接服务也可以打开 App、查看 RAM/CPU/存储/Ping 等信息
- 外部应用的真实 FPS 读取、游戏模式和深度优化需要内置 FrameScope 桥接服务，也兼容 [Shizuku](https://github.com/RikkaApps/Shizuku)
- 桥接服务首次通过 USB ADB 启动，启动后不需要电脑一直连接
- Root 设备也可以配合 Sui 模块使用

## 基础监测与深度优化

FrameScope 将功能分为两部分：

- **基础监测：** 不依赖高权限后端，可查看 CPU、RAM、存储、Ping、基础温度状态，并打开悬浮窗。
- **深度优化：** 默认使用 FrameScope 桥接服务（也兼容 Shizuku），用于读取其他应用的 SurfaceFlinger FPS、游戏模式、后台应用挂起、刷新率锁定、固定性能模式和 Vivo 硬件优化。

没有桥接服务时，App 仍然可以正常打开；只是详细多传感器温度、外部应用真实 FPS 和高级系统修改功能会显示为不可用。

## FrameScope 桥接服务教程（推荐）

桥接服务已经内置在 FrameScope APK 中，不是第二个 App，也不需要安装 Shizuku。由于 Android 不允许普通 App 把自身 UID 提升为 `shell`，所以必须首次通过 ADB 启动一次。

### 1. 安装 FrameScope

```powershell
adb -s <设备序列号> install -r <FrameScope APK 文件路径>
```

### 2. 启动内置桥接服务

最简单的方法：直接双击仓库根目录的 **`启动FrameScope桥接服务.bat`**。它会自动找到唯一已连接的手表；如果连接了多个设备，会让你输入手表序列号。

停止服务时，双击 **`停止FrameScope桥接服务.bat`** 即可。

如果 Windows 阻止双击脚本，也可以在仓库根目录打开 PowerShell 手动运行：

在仓库根目录打开 PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\start-framescope-bridge.ps1 -Serial <设备序列号>
```

也可以在 App 的 **权限设置 → 复制 ADB 命令** 中复制启动命令。打开 FrameScope，确认 **FrameScope 桥接服务** 显示“已运行（Shell 权限）”。此时可以断开电脑，App 会通过共享存储中的本地请求通道继续读取数据。

### 3. 手表重启后

重新运行一次启动脚本即可，不需要重新安装 FrameScope，也不需要安装 Shizuku。启动器位于仓库根目录，底层 PowerShell 脚本位于 [`scripts/`](scripts/)。更多说明见 [scripts/README-Bridge.md](scripts/README-Bridge.md)。这些文件都会随项目一起发布到 GitHub。

### 可选 Shizuku 后备方案

已经安装 Shizuku 的用户仍然可以正常使用。启动 Shizuku 并授权 FrameScope 后，如果内置桥接服务不可用，FrameScope 会自动使用 Shizuku。

## 截图

<table>
  <tr>
    <td align="center"><img src="docs/assets/ui/onboard1.jpeg" width="150"/><br/><sub><b>引导页 1</b></sub></td>
    <td align="center"><img src="docs/assets/ui/onboard3.jpeg" width="150"/><br/><sub><b>引导页 2</b></sub></td>
    <td align="center"><img src="docs/assets/ui/dash-no-setup.jpeg" width="150"/><br/><sub><b>未完成设置的仪表盘</b></sub></td>
    <td align="center"><img src="docs/assets/ui/dash-setup-done.jpeg" width="150"/><br/><sub><b>运行中的仪表盘</b></sub></td>
    <td align="center"><img src="docs/assets/ui/setup_system.jpeg" width="150"/><br/><sub><b>系统设置</b></sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/ui/overlay_config.jpeg" width="150"/><br/><sub><b>悬浮窗配置</b></sub></td>
    <td align="center"><img src="docs/assets/ui/appearance_settings.jpeg" width="150"/><br/><sub><b>外观设置</b></sub></td>
    <td align="center"><img src="docs/assets/ui/perfomance.jpeg" width="150"/><br/><sub><b>性能模式</b></sub></td>
    <td align="center"><img src="docs/assets/ui/thermal.jpeg" width="150"/><br/><sub><b>温度诊断</b></sub></td>
    <td align="center"><img src="docs/assets/ui/about-legal.jpeg" width="150"/><br/><sub><b>关于与法律信息</b></sub></td>
  </tr>
</table>

## FPS 测量原理

FrameScope 通过 FrameScope Bridge 或 Shizuku 提供的特权调用读取 Android 渲染管线数据：设备能提供有效呈现时间戳时使用 `SurfaceFlinger --latency`；在实测 Android 8.1 固件上，如果该命令返回 `0 0 0`，会自动切换到 `dumpsys gfxinfo <前台应用包名> framestats`，统计最近 1 秒内真正完成的帧数。静止页面没有新渲染帧时显示 **0 FPS** 是正常的；滑动页面、播放视频或运行动画后再观察。数值不会被强制限制为 60。

![FPS 测量架构图](docs/assets/fps_measurement_architecture.svg)

## 构建项目

这是一个标准的 Android Gradle 项目，需要 **JDK 17** 和 **Android SDK 34**。

```bash
git clone https://github.com/superj0107/FrameScope.git
cd FrameScope-Android
./gradlew assembleDebug
```

## 下载已测试 APK

仓库中包含已在 Android 8.1 手表上测试的 Debug APK：

- [FrameScope_v0.1.0-debug.apk](releases/FrameScope_v0.1.0-debug.apk)
- [GitHub Release v0.1.0](https://github.com/superj0107/FrameScope/releases/tag/v0.1.0)
- 包名：`com.framescope.app`
- 最低 Android 版本：Android 8.0（API 26）
- 基础悬浮窗只需要“显示在其他应用上层”权限；读取外部应用真实 FPS 需要 FrameScope 桥接服务或 Shizuku

## 权限说明

| 权限 | 用途 |
|---|---|
| 显示在其他应用上层 | 在游戏上方显示性能悬浮窗 |
| 前台服务 | 屏幕亮起时保持悬浮窗运行 |
| 唤醒锁 | 活动会话期间防止 CPU 休眠 |
| `PACKAGE_USAGE_STATS` | 判断当前前台运行的游戏或应用 |
| 忽略电池优化 | 避免厂商后台清理策略终止服务 |
| 开机启动 | 在重启后自动恢复已启用的悬浮窗 |
| 网络 | 仅向 `8.8.8.8`（Google 公共 DNS）进行 Ping 测量 |
| 终止后台进程 | 开启游戏模式时清理后台应用缓存进程 |
| 通知策略访问 | 自动切换勿扰模式 |
| 修改系统设置 | 应用每个游戏的亮度、音量和旋转设置 |
| 前台服务（Special Use） | 确保 Android 14 及以上的游戏模式持续运行 |
| 发送通知 | 显示会话状态及快捷控制按钮 |
| `QUERY_ALL_PACKAGES` | 加载已安装的游戏和应用 |
| FrameScope 桥接服务 / Shizuku | 高精度 FPS 测量、温度诊断、ART 内存整理以及 OriginOS 电竞硬件引擎 |

## 安全与隐私

- **不收集数据**：不会向外部发送数据
- **无分析服务**：不包含 Firebase、Crashlytics 或其他跟踪 SDK
- **无需账号**：FrameScope 不提供登录功能，也不收集用户身份
- **无广告**
- 所有数据均保留在设备本地

[查看完整隐私政策](https://superj0107.github.io/FrameScope/privacy-policy)

## 已知限制

少量指标依赖设备厂商是否公开相应数据。已确认的情况和受影响设备请参阅 [KNOWN_LIMITATIONS.md](KNOWN_LIMITATIONS.md)。

## 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku)（作者：[RikkaW](https://github.com/RikkaApps)）：作为可选的高权限后备方案。

## 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE)。
