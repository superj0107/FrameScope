# FrameScope releases

`FrameScope_v0.1.0-debug.apk` is the debug build tested on the target Android 8.1 watch. It includes the embedded FrameScope Bridge, so Shizuku is optional.

Install it with Android's package installer or ADB:

```bash
adb install FrameScope_v0.1.0-debug.apk
```

After installation, double-click `启动FrameScope桥接服务.bat` in the repository root, or run `../scripts/start-framescope-bridge.ps1 -Serial <serial>` from Windows PowerShell. See `../scripts/README-Bridge.md` for the complete setup guide.

Then enable the overlay permission and start the FPS overlay from the app. Shizuku is optional when the embedded Bridge is running.
