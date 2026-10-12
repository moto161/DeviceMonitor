@echo off
chcp 65001 >nul
setlocal
title Device Monitor - Windows Agent Installer (v3)

set "SCRIPT_DIR=%~dp0"

echo ============================================================
echo   Device Monitor  -  Windows Agent  Installer  (v3)
echo   PowerShell architecture - no Python required
echo ============================================================
echo.

net session >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Administrator privileges required.
    echo         Right-click this file -^> "Run as administrator".
    echo.
    pause
    exit /b 1
)

where powershell >nul 2>&1
if errorlevel 1 (
    echo [ERROR] powershell.exe not found in PATH.
    echo.
    pause
    exit /b 1
)

echo [INFO] Launching install.ps1 ...
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%install.ps1" %*
set "RC=%ERRORLEVEL%"

echo.
if "%RC%"=="0" (
    echo [DONE] Installer finished successfully.
) else (
    echo [DONE] Installer finished with exit code %RC%.
)
echo.
pause
exit /b %RC%
