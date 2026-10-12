# ============================================
# 设备监控 Windows Agent (PowerShell) - v2 稳定版
# 修复：整体错误处理、日志文件、自动恢复
# ============================================

# ========== 配置区 ==========
$SERVER_URL = "http://YOUR_SERVER_IP:8443"
$DEVICE_ID = "windows"
$DEVICE_NAME = "Windows PC"
$DEVICE_TYPE = "pc"
$INTERVAL = 30
$API_KEY = "YOUR_API_KEY"
# 以下路径基于脚本所在目录自动推导，项目整体移动到任意盘符/路径均可用

$AGENT_DIR = $PSScriptRoot

$LOG_FILE = Join-Path $AGENT_DIR "agent.log"
$MAX_LOG_SIZE = 5MB

# OHM 路径（使用项目内路径）
$ohmExe = Join-Path $AGENT_DIR "openhardwaremonitor\extracted\OpenHardwareMonitor\OpenHardwareMonitor.exe"
$ohmDll = Join-Path $AGENT_DIR "openhardwaremonitor\extracted\OpenHardwareMonitor\OpenHardwareMonitorLib.dll"

# ========== 日志函数 ==========
function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    $logLine = "[$timestamp] [$Level] $Message"
    Write-Host $logLine
    try {
        # 日志轮转：超过5MB就备份
        if (Test-Path $LOG_FILE) {
            $size = (Get-Item $LOG_FILE).Length
            if ($size -gt $MAX_LOG_SIZE) {
                Rename-Item $LOG_FILE "$LOG_FILE.old" -Force
            }
        }
        Add-Content -Path $LOG_FILE -Value $logLine -Encoding UTF8
    } catch {}
}

Write-Log "============================================"
Write-Log "  设备监控 Agent 启动 (Windows) v2"
Write-Log "  设备: $DEVICE_NAME ($DEVICE_ID)"
Write-Log "  服务器: $SERVER_URL"
Write-Log "  上报间隔: ${INTERVAL}秒"
Write-Log "  日志文件: $LOG_FILE"
Write-Log "============================================"

# ========== 主循环（整体错误处理） ==========
$loopCount = 0
$consecutiveErrors = 0

while ($true) {
    $loopCount++
    try {
        Write-Log "=== 第 $loopCount 次采集 ==="

        # 确保 OpenHardwareMonitor 在运行
        $ohmRunning = Get-Process -Name "OpenHardwareMonitor" -ErrorAction SilentlyContinue
        if (-not $ohmRunning) {
            Write-Log "OHM 未运行，正在启动..."
            if (Test-Path $ohmExe) {
                Start-Process -FilePath $ohmExe -WindowStyle Minimized -ErrorAction SilentlyContinue

                # 轮询等待 OHM 的 WMI 命名空间就绪（最多 30 秒）。
                # 固定 sleep 5 秒不够，会导致首轮采集温度恒为 -1 并误触发告警。
                $waited = 0
                $ohmReady = $false
                while ($waited -lt 30) {
                    Start-Sleep -Seconds 2
                    $waited += 2
                    try {
                        $probe = @(Get-CimInstance -Namespace "root\OpenHardwareMonitor" -ClassName Sensor -ErrorAction Stop)
                        if ($probe.Count -gt 0) {
                            $ohmReady = $true
                            Write-Log "OHM 就绪，耗时 ${waited}s"
                            break
                        }
                    } catch {
                        # WMI 命名空间尚未注册，继续等待
                    }
                }
                if (-not $ohmReady) {
                    Write-Log "OHM 启动超时（30s），本轮温度可能不可用" "WARN"
                }
            } else {
                Write-Log "OHM 可执行文件不存在: $ohmExe" "WARN"
            }
        }

        # CPU 使用率
        $cpuUsage = 0
        try {
            $cpuCounter = Get-Counter -Counter "\Processor(_Total)\% Processor Time" -SampleInterval 1 -MaxSamples 1 -ErrorAction Stop
            $cpuUsage = [math]::Round($cpuCounter.CounterSamples[0].CookedValue, 1)
        } catch {
            try {
                $cpuUsage = (Get-CimInstance Win32_Processor -ErrorAction Stop | Measure-Object -Property LoadPercentage -Average).Average
            } catch {
                Write-Log "CPU使用率获取失败: $_" "WARN"
            }
        }
        if ($null -eq $cpuUsage) { $cpuUsage = 0 }

        # CPU 核心数
        $cpuCores = [System.Environment]::ProcessorCount

        # CPU 型号
        $cpuModel = "Unknown"
        try {
            $proc = Get-CimInstance Win32_Processor -ErrorAction Stop | Select-Object -First 1
            if ($proc) { $cpuModel = $proc.Name.Trim() }
        } catch {
            Write-Log "CPU型号获取失败: $_" "WARN"
        }

        # CPU 温度
        $cpuTemp = -1
        try {
            $sensors = Get-CimInstance -Namespace "root\OpenHardwareMonitor" -ClassName Sensor -ErrorAction Stop
            if ($sensors) {
                foreach ($sensor in $sensors) {
                    if ($sensor.SensorType -eq "Temperature" -and $sensor.Value) {
                        $name = $sensor.Name
                        if ($name -notlike "*GPU*" -and ($name -like "*CPU*" -or $name -like "*Core*" -or $name -like "*Package*" -or $name -eq "Temperature")) {
                            if ($cpuTemp -lt 0 -or $sensor.Value -gt $cpuTemp) {
                                $cpuTemp = [math]::Round($sensor.Value, 1)
                            }
                        }
                    }
                }
            }
        } catch {
            # WMI 失败，尝试直接加载 DLL
            try {
                if (Test-Path $ohmDll) {
                    Add-Type -Path $ohmDll -ErrorAction Stop
                    $ohmComputer = New-Object OpenHardwareMonitor.Hardware.Computer
                    $ohmComputer.CPUEnabled = $true
                    $ohmComputer.Open()
                    Start-Sleep -Milliseconds 500
                    foreach ($hw in $ohmComputer.Hardware) {
                        $hw.Update()
                        foreach ($sensor in $hw.Sensors) {
                            if ($sensor.SensorType -eq "Temperature" -and $sensor.Value) {
                                if ($cpuTemp -lt 0 -or $sensor.Value -gt $cpuTemp) {
                                    $cpuTemp = [math]::Round($sensor.Value, 1)
                                }
                            }
                        }
                    }
                    $ohmComputer.Close()
                }
            } catch {
                Write-Log "CPU温度获取失败: $_" "WARN"
            }
        }

        # 内存
        $memTotal = 0
        $memFree = 0
        try {
            $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
            $memTotal = $os.TotalVisibleMemorySize * 1024
            $memFree = $os.FreePhysicalMemory * 1024
        } catch {
            Write-Log "内存获取失败: $_" "WARN"
        }
        $memUsed = if ($memTotal -gt 0) { $memTotal - $memFree } else { 0 }
        $memUsage = if ($memTotal -gt 0) { [math]::Round(($memUsed / $memTotal) * 100, 1) } else { 0 }

        # 磁盘
        $disks = @()
        try {
            Get-PSDrive -PSProvider FileSystem -ErrorAction Stop | Where-Object { $_.Used -gt 0 } | ForEach-Object {
                $total = $_.Used + $_.Free
                if ($total -gt 0) {
                    $usage = [math]::Round(($_.Used / $total) * 100, 1)
                    $disks += @{
                        filesystem = "$($_.Name):"
                        mount = "$($_.Name):\"
                        total = [int64]$total
                        used = [int64]$_.Used
                        usage = $usage
                    }
                }
            }
        } catch {
            Write-Log "磁盘获取失败: $_" "WARN"
        }

        # 运行时间
        $uptime = 0
        try {
            $lastBoot = (Get-CimInstance Win32_OperatingSystem -ErrorAction Stop).LastBootUpTime
            $uptime = [int]((Get-Date) - $lastBoot).TotalSeconds
        } catch {
            Write-Log "运行时间获取失败: $_" "WARN"
        }

        # 负载
        $load1 = 0
        try {
            $queueCounter = Get-Counter -Counter "\System\Processor Queue Length" -SampleInterval 1 -MaxSamples 1 -ErrorAction Stop
            $queueLen = $queueCounter.CounterSamples[0].CookedValue
            $load1 = [math]::Round($queueLen + ($cpuUsage / 100), 2)
        } catch {
            $load1 = [math]::Round($cpuUsage / 100, 2)
        }
        $load5 = $load1
        $load15 = $load1

        # IP 地址
        $ipAddr = ""
        try {
            $eth = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object { $_.InterfaceAlias -like "*以太网*" -and $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | Select-Object -First 1
            if ($eth) {
                $ipAddr = $eth.IPAddress
            } else {
                $wlan = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object { $_.InterfaceAlias -like "*WLAN*" -and $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | Select-Object -First 1
                if ($wlan) { $ipAddr = $wlan.IPAddress }
            }
            if (-not $ipAddr) {
                $ipAddr = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop | Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } | Select-Object -First 1).IPAddress
            }
        } catch {
            Write-Log "IP获取失败: $_" "WARN"
        }

        # 公网IP（通过外部API获取，超时5秒）
        $publicIp = ""
        try {
            $publicIp = (Invoke-RestMethod -Uri "https://api.ipify.org?format=json" -TimeoutSec 5).ip
        } catch {
            try {
                $publicIp = (Invoke-WebRequest -Uri "https://ifconfig.me/ip" -TimeoutSec 5 -UseBasicParsing).Content.Trim()
            } catch {
                Write-Log "公网IP获取失败: $_" "WARN"
            }
        }

        # 构建 data
        $data = @{
            cpu_usage = [double]$cpuUsage
            cpu_cores = $cpuCores
            cpu_model = $cpuModel
            cpu_temp = [double]$cpuTemp
            memory_total = [int64]$memTotal
            memory_used = [int64]$memUsed
            memory_available = [int64]$memFree
            memory_usage = [double]$memUsage
            disks = $disks
            uptime = $uptime
            load_1 = [double]$load1
            load_5 = [double]$load5
            load_15 = [double]$load15
            ip_address = $ipAddr
            public_ip = $publicIp
        }

        # 构建请求
        $jsonData = @{
            device_id = $DEVICE_ID
            device_name = $DEVICE_NAME
            device_type = $DEVICE_TYPE
            data = $data
        } | ConvertTo-Json -Depth 5 -Compress

        Write-Log "采集完成: CPU=$cpuUsage% 内存=$memUsage% 温度=$cpuTemp°C IP=$ipAddr"

        # 上报
        try {
            $headers = @{
                "Content-Type" = "application/json"
                "x-api-key" = $API_KEY
            }
            $response = Invoke-RestMethod -Uri "$SERVER_URL/api/report" -Method Post -Body $jsonData -Headers $headers -TimeoutSec 10
            Write-Log "上报成功"
            $consecutiveErrors = 0
        } catch {
            $consecutiveErrors++
            Write-Log "上报失败 (连续第 $consecutiveErrors 次): $_" "ERROR"
        }

    } catch {
        # 整体错误捕获：任何未预期的异常都不会导致脚本退出
        $consecutiveErrors++
        Write-Log "循环异常 (连续第 $consecutiveErrors 次): $_" "ERROR"
        Write-Log "异常详情: $($_.ScriptStackTrace)" "ERROR"
    }

    # 连续错误过多时，延长等待时间，避免疯狂重试
    if ($consecutiveErrors -gt 5) {
        Write-Log "连续错误过多，等待60秒后重试..." "WARN"
        Start-Sleep -Seconds 60
    } else {
        Start-Sleep -Seconds $INTERVAL
    }
}
