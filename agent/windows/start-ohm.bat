@echo off
chcp 65001 >nul
title OpenHardwareMonitor Daemon

:loop
echo [%date% %time%] Starting OpenHardwareMonitor...
start "" /min "D:\监测软件\DeviceMonitor\agent\windows\openhardwaremonitor\extracted\OpenHardwareMonitor\OpenHardwareMonitor.exe"
echo [%date% %time%] OHM started, waiting...

:wait
timeout /t 10 /nobreak >nul
tasklist /fi "imagename eq OpenHardwareMonitor.exe" 2>nul | find "OpenHardwareMonitor.exe" >nul
if errorlevel 1 (
    echo [%date% %time%] OHM exited, restarting...
    goto loop
)
goto wait
