@echo off
setlocal
title Dash Client Launcher
pushd "%~dp0" || (
    echo [ERROR] Could not open the Dash Client project folder.
    pause
    exit /b 1
)

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java 25 not found in "%JAVA_HOME%".
    set "DASH_EXIT_CODE=1"
    goto finish
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
