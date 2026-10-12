# ============================================================
#  设备监控 Windows Agent 安装 / 卸载 脚本  (v3 · PowerShell 架构)
#  适用于 device-agent.ps1 + agent-watcher.ps1 架构（无需 Python）
#  必须以「管理员身份」运行
# ============================================================
[CmdletBinding()]
param(
    [string]$ServerUrl,
    [string]$DeviceId,
    [string]$DeviceName,
    [string]$DeviceType = 'pc',
    [int]$Interval = 30,
    [string]$ApiKey,
    [string]$TaskName = 'DeviceAgentWatcher',
    [switch]$Uninstall,
    [switch]$KeepConfig
)

$ErrorActionPreference = 'Stop'
$AGENT_DIR  = $PSScriptRoot
$AGENT_PS1  = Join-Path $AGENT_DIR 'device-agent.ps1'
$WATCH_PS1  = Join-Path $AGENT_DIR 'agent-watcher.ps1'
$AGENT_LOG  = Join-Path $AGENT_DIR 'agent.log'
$OHM_EXE    = Join-Path $AGENT_DIR 'openhardwaremonitor\extracted\OpenHardwareMonitor\OpenHardwareMonitor.exe'

$script:Steps = 0
$script:Fails = 0

function Write-Banner {
    Write-Host ''
    Write-Host ('=' * 62) -ForegroundColor Cyan
    Write-Host '  设备监控 Windows Agent  安装 / 卸载  (v3 PowerShell)' -ForegroundColor Cyan
    Write-Host ('=' * 62) -ForegroundColor Cyan
    Write-Host ''
}

function Write-Step {
    param([string]$Text)
    $script:Steps++
    Write-Host ''
    Write-Host ("[$script:Steps] $Text") -ForegroundColor Yellow
    Write-Host ('-' * 62) -ForegroundColor Green
}

function Write-Ok   { param([string]$m) Write-Host "  [OK]   $m" -ForegroundColor Green }
function Write-Warn { param([string]$m) Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Write-Err  { param([string]$m) Write-Host "  [FAIL] $m" -ForegroundColor Red; $script:Fails++ }

# ------------------------------------------------------------
# 0. 环境检查
# ------------------------------------------------------------
Write-Banner
Write-Step '环境检查'

if ($PSVersionTable.PSVersion.Major -lt 5) {
    Write-Err "PowerShell 版本过低 ($($PSVersionTable.PSVersion))，需要 5.0 以上"
    exit 1
}
Write-Ok "PowerShell $($PSVersionTable.PSVersion)"

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
          ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if ($isAdmin) {
    Write-Ok '管理员权限'
} else {
    Write-Err '未以管理员身份运行，无法注册计划任务。请右键「以管理员身份运行」'
    exit 1
}

foreach ($f in @($AGENT_PS1, $WATCH_PS1)) {
    if (Test-Path -LiteralPath $f) { Write-Ok "存在 $(Split-Path $f -Leaf)" }
    else { Write-Err "缺失 $f"; }
}
if (Test-Path -LiteralPath $OHM_EXE) { Write-Ok 'OpenHardwareMonitor 组件就绪' }
else { Write-Warn "OHM 缺失：$OHM_EXE  （温度采集将不可用，但 CPU/内存/磁盘仍可上报）" }

# ------------------------------------------------------------
# 1. 编码修复：中文路径脚本必须带 UTF-8 BOM
#    PowerShell 5.1 读取无 BOM 的 .ps1 会按 GBK 解码，
#    脚本内中文路径全部乱码 → 静默空转。此项为强制修复。
# ------------------------------------------------------------
Write-Step '脚本编码检查与修复 (UTF-8 BOM)'

foreach ($f in @($AGENT_PS1, $WATCH_PS1, $PSCommandPath)) {
    if (-not $f -or -not (Test-Path -LiteralPath $f)) { continue }
    $bytes = [System.IO.File]::ReadAllBytes($f)
    $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    if ($hasBom) {
        Write-Ok "$(Split-Path $f -Leaf) 已有 BOM"
    } else {
        $body = New-Object byte[] ($bytes.Length + 3)
        $bytes.CopyTo($body, 3)
        $body[0] = 0xEF; $body[1] = 0xBB; $body[2] = 0xBF
        [System.IO.File]::WriteAllBytes($f, $body)
        Write-Ok "$(Split-Path $f -Leaf) 已补写 BOM（原为无 BOM，PS5.1 下会乱码空转）"
    }
}

# ------------------------------------------------------------
# 2. 停止现有 agent（幂等，重复安装不会产生多实例）
# ------------------------------------------------------------
Write-Step '停止现有 Agent 进程'

$procs = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
           Where-Object { $_.CommandLine -like '*device-agent.ps1*' })
if ($procs.Count -gt 0) {
    foreach ($p in $procs) {
        try {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
            Write-Ok "已停止 agent PID=$($p.ProcessId)"
        } catch { Write-Warn "停止 PID=$($p.ProcessId) 失败：$($_.Exception.Message)" }
    }
} else {
    Write-Ok '当前无 agent 实例在运行'
}

# ------------------------------------------------------------
# 3. 计划任务
# ------------------------------------------------------------
Write-Step '注册 / 注销 计划任务'

if ($Uninstall) {
    foreach ($t in @($TaskName, 'DeviceAgent')) {
        if (Get-ScheduledTask -TaskName $t -ErrorAction SilentlyContinue) {
            try {
                Stop-ScheduledTask -TaskName $t -ErrorAction SilentlyContinue
                Unregister-ScheduledTask -TaskName $t -Confirm:$false -ErrorAction Stop
                Write-Ok "已注销计划任务 $t"
            } catch { Write-Err "注销任务 $t 失败：$($_.Exception.Message)" }
        } else {
            Write-Ok "计划任务 $t 不存在，无需注销"
        }
    }
} else {
    # 清掉遗留的旧版 python agent 任务（会指向已不存在的 device-agent.py）
    $legacy = Get-ScheduledTask -TaskName 'DeviceMonitorAgent' -ErrorAction SilentlyContinue
    if ($legacy) {
        try {
            Disable-ScheduledTask -TaskName 'DeviceMonitorAgent' -ErrorAction SilentlyContinue | Out-Null
            Write-Ok '已禁用遗留旧版任务 DeviceMonitorAgent（python 版）'
        } catch { Write-Warn "禁用旧任务失败：$($_.Exception.Message)" }
    }

    $watcherArgs = '-ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $WATCH_PS1 + '"'
    try {
        $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $watcherArgs
        # 每 1 分钟触发一次，持续无限期；实例策略 IgnoreNew 防止重复拉起
        $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) `
                    -RepetitionInterval (New-TimeSpan -Minutes 1)
        $settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew `
                    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                    -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable

        # PS 5.1 的 Set-ScheduledTask 不支持 -Description，统一走「注销后重建」，参数最可靠
        if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
            Stop-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
            Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
            Write-Ok "已移除旧任务 $TaskName，将按新配置重建"
        }
        try {
            Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
                                   -Settings $settings `
                                   -Description '设备监控 Agent 守护：每分钟检查并拉起 device-agent.ps1' `
                                   -ErrorAction Stop | Out-Null
            Write-Ok "已注册计划任务 $TaskName"
        } catch {
            Write-Err "计划任务注册失败：$($_.Exception.Message)"
        }
        Enable-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue | Out-Null
        $t = Get-ScheduledTask -TaskName $TaskName
        Write-Ok ("任务状态：{0}   指向：{1}" -f $t.State, $WATCH_PS1)

        # ---- Agent 常驻任务（2026-10-12 新增）----
        # vbs 链路在部分系统更新后会静默失效，改为计划任务直接以 SYSTEM 身份
        # 常驻运行 agent（开机自启、无窗口），watcher 每分钟检查并 Start-ScheduledTask 拉起。
        $agentArgs = '-ExecutionPolicy Bypass -NonInteractive -File "' + $AGENT_PS1 + '"'
        try {
            if (Get-ScheduledTask -TaskName 'DeviceAgent' -ErrorAction SilentlyContinue) {
                Stop-ScheduledTask -TaskName 'DeviceAgent' -ErrorAction SilentlyContinue
                Unregister-ScheduledTask -TaskName 'DeviceAgent' -Confirm:$false -ErrorAction SilentlyContinue
            }
            $agentAction   = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $agentArgs
            $agentTrigger  = New-ScheduledTaskTrigger -AtStartup
            $agentSettings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew `
                                -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
                                -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable `
                                -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
            # SYSTEM 身份运行于 Session 0，天然无窗口（2026-10-12 修复：默认交互身份会在桌面弹出控制台窗口）
            $agentPrincipal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount
            Register-ScheduledTask -TaskName 'DeviceAgent' -Action $agentAction `
                                   -Trigger $agentTrigger -Settings $agentSettings `
                                   -Principal $agentPrincipal `
                                   -Description '设备监控 Agent 常驻进程：SYSTEM 身份无窗口运行，由 DeviceAgentWatcher 守护拉起' `
                                   -ErrorAction Stop | Out-Null
            Enable-ScheduledTask -TaskName 'DeviceAgent' -ErrorAction SilentlyContinue | Out-Null
            Write-Ok '已注册计划任务 DeviceAgent（Agent 常驻，开机自启）'
        } catch {
            Write-Err "Agent 常驻任务注册失败：$($_.Exception.Message)"
        }
    } catch {
        Write-Err "计划任务注册失败：$($_.Exception.Message)"
    }
}

# ------------------------------------------------------------
# 4. 写入配置（卸载模式或 -KeepConfig 时跳过）
# ------------------------------------------------------------
if (-not $Uninstall -and -not $KeepConfig) {
    Write-Step '写入 Agent 配置'

    if (-not $ServerUrl) {
        $inHost = Read-Host '  服务器地址 (直接回车用 http://YOUR_SERVER_IP:8443)'
        $ServerUrl = if ($inHost) { $inHost } else { 'http://YOUR_SERVER_IP:8443' }
    }
    if (-not $DeviceId) {
        $inId = Read-Host '  设备ID (直接回车用 windows)'
        $DeviceId = if ($inId) { $inId } else { 'windows' }
    }
    if (-not $DeviceName) {
        $inName = Read-Host '  设备名称 (直接回车用 Windows PC)'
        $DeviceName = if ($inName) { $inName } else { 'Windows PC' }
    }
    if (-not $ApiKey) { $ApiKey = 'YOUR_API_KEY' }

    $map = [ordered]@{
        'SERVER_URL'  = '"' + $ServerUrl  + '"'
        'DEVICE_ID'   = '"' + $DeviceId   + '"'
        'DEVICE_NAME' = '"' + ($DeviceName -replace '"', '') + '"'
        'DEVICE_TYPE' = '"' + $DeviceType + '"'
        'INTERVAL'    = [string]$Interval
        'API_KEY'     = '"' + $ApiKey     + '"'
    }

    # 逐行替换，避免 [regex]::Replace 的 $ 组引用把变量名写成 ${0}
    $allLines = [System.IO.File]::ReadAllLines($AGENT_PS1, [System.Text.Encoding]::UTF8)
    $hitKeys = @{}
    for ($i = 0; $i -lt $allLines.Length; $i++) {
        foreach ($key in $map.Keys) {
            if ($allLines[$i] -match ('^\s*\$' + [regex]::Escape($key) + '\s*=')) {
                $allLines[$i] = '$' + $key + ' = ' + $map[$key]
                $hitKeys[$key] = $true
                break
            }
        }
    }
    foreach ($key in $map.Keys) {
        if (-not $hitKeys.ContainsKey($key)) { Write-Warn "配置项 \$$key 未在脚本中找到，请手动检查" }
    }
    $utf8Bom = New-Object System.Text.UTF8Encoding($true)
    [System.IO.File]::WriteAllLines($AGENT_PS1, $allLines, $utf8Bom)

    Write-Ok '配置已写入 device-agent.ps1：'
    foreach ($key in $map.Keys) {
        $val = $map[$key] -replace '^"|"$', ''
        Write-Host ("         {0,-12} = {1}" -f $key, $val)
    }
} elseif ($Uninstall) {
    Write-Step '配置'
    Write-Ok '卸载模式，配置保持原样（如需清除请手动编辑 device-agent.ps1）'
}

# ------------------------------------------------------------
# 5. 启动 + 验证
# ------------------------------------------------------------
if (-not $Uninstall) {
    Write-Step '启动 Agent 并验证'

    try {
        Start-ScheduledTask -TaskName $TaskName
        Write-Ok '已触发计划任务，正在等待 Agent 首次上报...'
    } catch {
        Write-Err "触发计划任务失败：$($_.Exception.Message)"
    }

    Write-Host '  等待 40 秒采集首轮数据（含 OHM 温度就绪等待）...' -ForegroundColor DarkGray
    Start-Sleep -Seconds 40

    Write-Host ''
    Write-Host '  ---- agent.log 末尾 ----' -ForegroundColor Cyan
    if (Test-Path -LiteralPath $AGENT_LOG) {
        Get-Content -LiteralPath $AGENT_LOG -Tail 12 -Encoding UTF8 | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Warn "未生成 $AGENT_LOG"
    }
    Write-Host '  ------------------------' -ForegroundColor Cyan
    Write-Host ''

    $running = @(Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
                 Where-Object { $_.CommandLine -like '*device-agent.ps1*' })
    if ($running.Count -eq 1) {
        Write-Ok "Agent 正在运行（PID=$($running[0].ProcessId)），单实例无重复"
    } elseif ($running.Count -gt 1) {
        Write-Warn "检测到 $($running.Count) 个 Agent 实例，建议重启后重新安装"
    } else {
        Write-Err 'Agent 未运行，请查看 agent.log 排查'
    }

    $ohm = @(Get-Process -Name 'OpenHardwareMonitor' -ErrorAction SilentlyContinue)
    if ($ohm.Count -gt 0) { Write-Ok "OpenHardwareMonitor 运行中（PID=$($ohm[0].Id)）" }
    else { Write-Warn 'OpenHardwareMonitor 未运行，温度将上报 -1' }
}

# ------------------------------------------------------------
# 6. 汇总
# ------------------------------------------------------------
Write-Host ''
Write-Host ('=' * 62) -ForegroundColor Cyan
if ($Uninstall) {
    Write-Host '  卸载完成：已停止 Agent 并注销计划任务。' -ForegroundColor Cyan
    Write-Host '  项目文件保留在原目录，如需彻底删除请手动删除。' -ForegroundColor DarkGray
} else {
    Write-Host '  安装完成' -ForegroundColor Cyan
    Write-Host ''
    Write-Host '  请逐条核对：' -ForegroundColor Yellow
    Write-Host '   1. 上方 agent.log 出现「上报成功」'
    Write-Host '   2. 温度为具体数值而非 -1'
    Write-Host '   3. Agent 实例数为 1（无重复）'
    Write-Host '   4. 计划任务状态为 Ready，且指向本目录'
    Write-Host '   5. 手机 APP 上该设备显示为在线'
    Write-Host ''
    Write-Host "  常用命令：" -ForegroundColor DarkGray
    Write-Host "   查看状态  : Get-ScheduledTask -TaskName $TaskName" -ForegroundColor DarkGray
    Write-Host "   手动触发  : Start-ScheduledTask -TaskName $TaskName" -ForegroundColor DarkGray
    Write-Host "   查看日志  : Get-Content `"$AGENT_LOG`" -Tail 50 -Wait" -ForegroundColor DarkGray
    Write-Host "   停止      : Stop-ScheduledTask -TaskName $TaskName" -ForegroundColor DarkGray
    Write-Host "   卸载      : .\install.bat -Uninstall" -ForegroundColor DarkGray
}
Write-Host ('=' * 62) -ForegroundColor Cyan
Write-Host ''

if ($script:Fails -gt 0) {
    Write-Host "  完成，但有 $script:Fails 项失败，请检查上方 [FAIL] 标记。" -ForegroundColor Red
    exit 1
}
exit 0
