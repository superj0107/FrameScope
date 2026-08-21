param(
    [string]$Serial = "",
    [string]$AdbPath = "adb"
)

$adbCommand = Get-Command $AdbPath -ErrorAction SilentlyContinue
if ($null -eq $adbCommand) {
    throw "adb was not found. Add Android SDK platform-tools to PATH or use -AdbPath to specify adb.exe."
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $deviceRows = @(& $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" })
    if ($deviceRows.Count -eq 0) {
        throw "No authorized ADB device found. Connect the watch and allow USB debugging."
    }
    if ($deviceRows.Count -gt 1) {
        throw "Multiple ADB devices found. Use -Serial to specify the watch serial."
    }
    $Serial = ($deviceRows[0] -split "\s+")[0]
}

$cleanupCommand = 'pkill -f com.framescope.app.bridge.ShellBridgeMain >/dev/null 2>&1; rm -f /sdcard/Android/data/com.framescope.app/files/bridge/request_* /sdcard/Android/data/com.framescope.app/files/bridge/response_* /sdcard/Android/data/com.framescope.app/files/bridge/*.tmp'
$remoteCommand = 'CLASSPATH=$(pm path com.framescope.app | cut -d: -f2); export CLASSPATH; app_process /system/bin com.framescope.app.bridge.ShellBridgeMain >/dev/null 2>&1 &'
Write-Host "Starting FrameScope Bridge: $Serial"
& $AdbPath -s $Serial shell $cleanupCommand
& $AdbPath -s $Serial shell $remoteCommand
if ($LASTEXITCODE -ne 0) {
    throw "Start failed. Confirm that FrameScope is installed and its package name is com.framescope.app."
}

Write-Host "Bridge start command sent. Open FrameScope and check that FrameScope Bridge is connected."
