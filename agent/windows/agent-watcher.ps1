# ============================================
# Agent 监控脚本 - 确保device-agent始终运行
# 由Windows计划任务每分钟调用一次
# ============================================

$agentDir = $PSScriptRoot
$agentScript = Join-Path $agentDir "device-agent.ps1"
$logFile = Join-Path $agentDir "agent-watcher.log"

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $logLine = "[$timestamp] [$Level] $Message"
    Add-Content -Path $logFile -Value $logLine -Encoding UTF8
}

# 检查agent进程是否在运行
$agentProcess = Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" | Where-Object { $_.CommandLine -like "*device-agent.ps1*" }

if ($agentProcess) {
    # agent正在运行，不做任何事
    exit 0
} else {
    # agent未运行，启动它
    Write-Log "Agent未运行，正在启动..." "WARN"

    # 2026-10-12 改造：原 vbs 链路（wscript → start-hidden.vbs → powershell）在某次系统变更后
    # 静默失效（进程秒退且不执行脚本），改为直接拉起计划任务 DeviceAgent
    # （该任务由 install.ps1 注册，SYSTEM 身份 + 开机自启 + 隐藏窗口，效果等同且更可靠）。
    $agentTask = Get-ScheduledTask -TaskName 'DeviceAgent' -ErrorAction SilentlyContinue
    if ($agentTask) {
        Start-ScheduledTask -TaskName 'DeviceAgent'
    } else {
        # 任务不存在时的兜底：走旧 vbs 方式（会有窗口闪烁风险，但保证功能不挂）
        Write-Log "计划任务 DeviceAgent 不存在，回退到 start-hidden.vbs 方式" "WARN"
        $launcher = Join-Path $agentDir "start-hidden.vbs"
        if (Test-Path $launcher) {
            Start-Process -FilePath "wscript.exe" -ArgumentList "`"$launcher`"" -WindowStyle Hidden
        } else {
            Write-Log "start-hidden.vbs 不存在，回退到 Start-Process 方式" "WARN"
            Start-Process -FilePath "powershell.exe" -ArgumentList "-ExecutionPolicy Bypass -File `"$agentScript`"" -WindowStyle Hidden
        }
    }
    Start-Sleep -Seconds 3

    # 验证是否启动成功
    $checkProcess = Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" | Where-Object { $_.CommandLine -like "*device-agent.ps1*" }
    if ($checkProcess) {
        Write-Log "Agent启动成功，PID=$($checkProcess.ProcessId)" "INFO"
    } else {
        Write-Log "Agent启动失败" "ERROR"
    }
}
