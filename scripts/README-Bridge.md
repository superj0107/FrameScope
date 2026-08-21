# FrameScope Bridge

FrameScope Bridge 是 FrameScope 内置的 Shell 权限桥接服务。它不需要安装 Shizuku；首次通过 ADB 启动后，FrameScope 就可以读取 SurfaceFlinger 的真实呈现帧率。

## 启动

推荐直接双击仓库根目录的 **`启动FrameScope桥接服务.bat`**。停止时双击 **`停止FrameScope桥接服务.bat`**。启动器会自动识别唯一的已授权 ADB 手表；连接多个设备时会提示输入序列号，运行结束后按任意键关闭窗口。重复启动也安全：脚本会先停止旧 Bridge 并清理临时通信文件，避免 Android 8.1 上多个 Bridge 同时抢同一个请求。

下面是需要手动使用 PowerShell 时的方式：

在仓库根目录打开 PowerShell：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\start-framescope-bridge.ps1 -Serial <手表序列号>
```

如果只有一台已授权设备，也可以省略 `-Serial`：

```powershell
.\scripts\start-framescope-bridge.ps1
```

然后打开 FrameScope，在“权限设置”中确认 **FrameScope Bridge** 显示“已运行（Shell 权限）”。电脑可以断开，App 会通过共享存储中的本地请求通道继续读取数据。

## 手表重启后

通过 ADB 启动的进程不会保证跨重启保留。手表重启后重新运行启动脚本即可，不需要重新安装 APK 或安装 Shizuku。

## 停止

```powershell
.\scripts\stop-framescope-bridge.ps1 -Serial <手表序列号>
```

## 原理

启动脚本使用 `app_process` 从 FrameScope APK 中加载 `ShellBridgeMain`。因为启动者是 `adb shell`，该进程拥有 Shell UID；FrameScope 主 App 仍然保持普通 App UID，双方通过共享存储中的请求/响应文件通信，不依赖网络或 Unix Socket 权限。FPS 监测会在每次切换前台应用时自动探测可用后端：优先使用有效的 `SurfaceFlinger --latency` 数据，且使用最近 1 秒滚动窗口；若当前 ROM 返回无效数据，则使用前台应用的 `gfxinfo framestats` 完成帧统计。针对部分 Android 8.1 厂商固件，程序不会调用会阻塞的 `--latency-clear`。静止页面没有新帧时 FPS 显示 0，属于真实状态。
