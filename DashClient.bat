@echo off
setlocal
title Dash Client Launcher
pushd "%~dp0" || (
    echo [ERROR] Could not open the Dash Client project folder.
    pause
    exit /b 1
)

if not exist "%~dp0gradlew.bat" (
    echo [ERROR] Gradle wrapper not found next to DashClient.bat.
    set "DASH_EXIT_CODE=1"
    goto finish
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0RunDashClient.ps1"
set "DASH_EXIT_CODE=%ERRORLEVEL%"
if not "%DASH_EXIT_CODE%"=="0" echo [ERROR] Launcher exited with code %DASH_EXIT_CODE%.

:finish
popd
pause
exit /b %DASH_EXIT_CODE%
