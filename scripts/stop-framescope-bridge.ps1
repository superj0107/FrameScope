param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,
    [string]$AdbPath = "adb"
)

$adbCommand = Get-Command $AdbPath -ErrorAction SilentlyContinue
if ($null -eq $adbCommand) {
    throw "adb was not found. Add Android SDK platform-tools to PATH or use -AdbPath to specify adb.exe."
}

$remoteCommand = 'pkill -f com.framescope.app.bridge.ShellBridgeMain'
& $AdbPath -s $Serial shell $remoteCommand
Write-Host "FrameScope Bridge stop command sent."
