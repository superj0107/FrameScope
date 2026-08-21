@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

where adb >nul 2>&1
if errorlevel 1 (
    echo adb was not found. Install Android SDK Platform-Tools and add adb to PATH.
    goto :finish
)

set "SERIAL="
set /a DEVICE_COUNT=0
for /f "skip=1 tokens=1,2" %%A in ('adb devices 2^>nul') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "FOUND_SERIAL=%%A"
    )
)

if "!DEVICE_COUNT!"=="0" (
    echo No authorized ADB device found.
    goto :finish
)

if "!DEVICE_COUNT!"=="1" set "SERIAL=!FOUND_SERIAL!"
if "!DEVICE_COUNT!" GTR "1" set /p "SERIAL=Multiple devices found. Enter the watch serial: "
if not defined SERIAL (
    echo No serial was entered.
    goto :finish
)

adb -s "!SERIAL!" get-state 2>nul | findstr /x /c:"device" >nul
if errorlevel 1 (
    echo Device !SERIAL! is not available. Check the serial and ADB authorization.
    goto :finish
)

echo Stopping FrameScope Bridge: !SERIAL!
call powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-framescope-bridge.ps1" -Serial "!SERIAL!"
if errorlevel 1 echo Stop failed. See the error above.

:finish
echo.
pause
endlocal
