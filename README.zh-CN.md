# FrameScope

[English](README.md) · 中文说明

![FrameScope Banner](docs/assets/banner.png)

> FrameScope 是一款无需 Root 的 Android 性能悬浮窗工具，支持实时帧率（FPS）监测、温度诊断，以及通过 Shizuku 提供的高级游戏优化功能。

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
- 基础监测只需要“显示在其他应用上层”权限，不安装 Shizuku 也可以打开 App、查看 RAM/CPU/存储/Ping 等信息
- 外部应用的真实 FPS 读取、游戏模式和深度优化需要 [Shizuku](https://github.com/RikkaApps/Shizuku)
- Android 11 及以上可通过无线调试激活 Shizuku，也可以使用 USB ADB 激活
- Root 设备可配合 Sui 模块使用

## 基础监测与深度优化

FrameScope 将功能分为两部分：

- **基础监测：** 不依赖 Shizuku，可查看 CPU、RAM、存储、Ping、基础温度状态，并打开悬浮窗。
- **深度优化：** 需要 Shizuku 权限，用于读取其他应用的 SurfaceFlinger FPS、游戏模式、后台应用挂起、刷新率锁定、固定性能模式和 Vivo 硬件优化。

没有 Shizuku 时，App 仍然可以正常打开；只是详细多传感器温度、外部应用真实 FPS 和高级系统修改功能会显示为不可用。

## Shizuku 安装与启动教程

下面以已经连接 ADB 的手表为例。首次配置需要电脑，完成后 FrameScope 在 Shizuku 运行期间不需要一直连接电脑。

### 1. 安装 Shizuku

从 [Shizuku 官方 Releases](https://github.com/RikkaApps/Shizuku/releases) 下载最新 APK，然后在电脑 PowerShell 中执行：

```powershell
adb devices
adb -s <设备序列号> install -r <Shizuku APK 文件路径>
```

如果 `adb devices` 看不到设备，请先打开手表的开发者选项和 USB 调试。

### 2. 启动 Shizuku 服务

安装完成后执行：

```powershell
adb -s <设备序列号> shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```

也可以先打开 Shizuku，再按照 Shizuku 页面中的“通过 ADB 启动”提示执行命令。

### 3. 授予 FrameScope 权限

1. 打开 Shizuku，确认状态显示“正在运行”。
2. 打开 FrameScope → **权限设置**。
3. 点击 Shizuku 权限旁的授权按钮，并在弹窗中允许。
4. 返回仪表盘，即可使用外部应用 FPS、游戏模式和深度优化。

### 4. 手表重启后的处理

通过 ADB 启动的 Shizuku 通常不会跨重启保留。手表重启后，再执行第 2 步即可；FrameScope 本身不需要重新安装。

### 常见问题

- **点击“打开 Shizuku”没有反应：** 通常是 Shizuku 尚未安装。FrameScope 不能把 shell 权限服务直接塞进普通 APK。
- **提示找不到 `start.sh`：** 先打开一次 Shizuku，再重新执行启动命令；不同 Shizuku 版本也可能在 App 内显示专用命令。
- **Shizuku 已运行但 FrameScope 未连接：** 打开 Shizuku 的已授权应用列表，确认已允许 FrameScope，然后重启 FrameScope。
- **Android 11 及以上无电脑启动：** 在开发者选项中开启无线调试并完成配对，再在 Shizuku 中选择“通过无线调试启动”。

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

FrameScope 通过 Shizuku 提供的特权 IPC 调用，在 Android 系统合成器层直接读取 `SurfaceFlinger --latency` 数据来测量帧率。对于没有提供新版 `--timestats` 字段的 Android 8.1 设备，FrameScope 会读取前台图层的已显示帧时间戳。由于数据直接来自显示管线，不会增加被测应用渲染线程或 GPU 管线的额外负载。

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
- 基础悬浮窗只需要“显示在其他应用上层”权限；读取外部应用真实 FPS 需要 Shizuku

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
| Shizuku 特权 API | 高精度 FPS 测量、温度诊断、ART 内存整理以及 OriginOS 电竞硬件引擎 |

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

- [Shizuku](https://github.com/RikkaApps/Shizuku)（作者：[RikkaW](https://github.com/RikkaApps)）：提供 Rootless 系统访问能力的特权 API 桥接工具。没有 Shizuku，FrameScope 无法实现这些功能。

## 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE)。
