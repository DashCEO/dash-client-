@echo off
setlocal
set "DASH_RTX_PROFILE=1"
set "DASH_STABLE_PROFILE=1"
if /i "%~1"=="--vulkan" set "DASH_STABLE_PROFILE="
call "%~dp0DashClient.bat"
exit /b %ERRORLEVEL%
