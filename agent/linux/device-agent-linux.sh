#!/bin/sh
# ============================================
# 设备监控 Linux Agent (增强版 v1.3.0)
# 适用于: 通用 Linux 设备（云服务器 / 手机 / 平板 / 路由器 / 开发板）
# 新增: GPU/WiFi/网络接口/进程/内核/架构/Swap/CPU型号
# ============================================

# ========== 配置区 ==========
# 中心服务器地址（必须修改！）
SERVER_URL="http://YOUR_SERVER_IP:8443"

# 设备ID（唯一标识，建议用英文+数字）
DEVICE_ID="your-device-id"      # TODO: 改成你的设备唯一标识

# 设备名称（显示用）
DEVICE_NAME="My Device"        # TODO: 改成你的设备展示名

# 设备类型: server / phone / tablet / router / pc
DEVICE_TYPE="server"          # server / phone / tablet / router / pc

# 上报间隔（秒）
INTERVAL=30

# API Key（与服务器一致，默认即可）
API_KEY="YOUR_API_KEY"

# ========== 采集函数 ==========

# CPU 使用率
get_cpu_usage() {
  cpu1=$(cat /proc/stat | head -1 | awk '{print $2+$3+$4+$5+$6+$7+$8}')
  idle1=$(cat /proc/stat | head -1 | awk '{print $5}')
  sleep 1
  cpu2=$(cat /proc/stat | head -1 | awk '{print $2+$3+$4+$5+$6+$7+$8}')
  idle2=$(cat /proc/stat | head -1 | awk '{print $5}')

  total=$((cpu2 - cpu1))
  idle=$((idle2 - idle1))

  if [ "$total" -gt 0 ]; then
    usage=$(awk "BEGIN {printf \"%.1f\", (1 - $idle / $total) * 100}")
  else
    usage="0.0"
  fi
  echo "$usage"
}

# CPU 核心数
get_cpu_cores() {
  nproc 2>/dev/null || cat /proc/cpuinfo | grep -c processor
}

# CPU 型号
get_cpu_model() {
  model=$(cat /proc/cpuinfo 2>/dev/null | grep -m1 "model name" | cut -d: -f2 | sed 's/^ *//;s/ *$//')
  if [ -z "$model" ]; then
    model=$(cat /proc/cpuinfo 2>/dev/null | grep -m1 "Hardware" | cut -d: -f2 | sed 's/^ *//;s/ *$//')
  fi
  if [ -z "$model" ]; then
    model=$(uname -m)
  fi
  echo "$model"
}

# CPU 温度
get_cpu_temp() {
  temp=""

  # 方式1: /sys/class/thermal
  if [ -f /sys/class/thermal/thermal_zone0/temp ]; then
    raw=$(cat /sys/class/thermal/thermal_zone0/temp)
    if [ "$raw" -gt 1000 ]; then
      temp=$(awk "BEGIN {printf \"%.1f\", $raw / 1000}")
    else
      temp="$raw"
    fi
  fi

  # 方式2: 遍历所有thermal_zone取最高
  if [ -z "$temp" ]; then
    max_temp=0
    for zone in /sys/class/thermal/thermal_zone*/temp; do
      if [ -f "$zone" ]; then
        raw=$(cat "$zone")
        if [ "$raw" -gt 1000 ]; then
          val=$(awk "BEGIN {printf \"%.0f\", $raw / 1000}")
        else
          val="$raw"
        fi
        if [ "$val" -gt "$max_temp" ]; then
          max_temp="$val"
        fi
      fi
    done
    if [ "$max_temp" -gt 0 ]; then
      temp="$max_temp"
    fi
  fi

  # 方式3: sensors 命令
  if [ -z "$temp" ]; then
    if command -v sensors >/dev/null 2>&1; then
      temp=$(sensors 2>/dev/null | grep -i 'core 0' | head -1 | grep -oP '\+\K[0-9.]+' | head -1)
    fi
  fi

  echo "${temp:-0}"
}

# GPU 信息
get_gpu_info() {
  gpu_name=""
  gpu_usage=""
  gpu_temp=""
  gpu_mem_total=""
  gpu_mem_used=""

  # 方式1: NVIDIA (nvidia-smi)
  if command -v nvidia-smi >/dev/null 2>&1; then
    gpu_name=$(nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null | head -1)
    gpu_usage=$(nvidia-smi --query-gpu=utilization.gpu --format=csv,noheader,nounits 2>/dev/null | head -1)
    gpu_temp=$(nvidia-smi --query-gpu=temperature.gpu --format=csv,noheader,nounits 2>/dev/null | head -1)
    gpu_mem_total=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits 2>/dev/null | head -1)
    gpu_mem_used=$(nvidia-smi --query-gpu=memory.used --format=csv,noheader,nounits 2>/dev/null | head -1)
  fi

  # 方式2: AMD (rocm-smi)
  if [ -z "$gpu_name" ] && command -v rocm-smi >/dev/null 2>&1; then
    gpu_name=$(rocm-smi --showproductname 2>/dev/null | grep -i "GPU" | head -1 | awk '{print $NF}')
    gpu_usage=$(rocm-smi --showuse 2>/dev/null | grep -i "GPU" | head -1 | grep -oP '\d+%' | head -1 | tr -d '%')
    gpu_temp=$(rocm-smi --showtemp 2>/dev/null | grep -i "GPU" | head -1 | grep -oP '\d+' | head -1)
  fi

  # 方式3: Intel (intel_gpu_top) - 简化
  if [ -z "$gpu_name" ]; then
    if [ -d /sys/class/drm/card0/device ]; then
      gpu_name=$(cat /sys/class/drm/card0/device/vendor 2>/dev/null)
      if [ "$gpu_name" = "0x8086" ]; then
        gpu_name="Intel Integrated Graphics"
      fi
    fi
  fi

  # 方式4: 高通Adreno (从设备树或gpuinfo)
  if [ -z "$gpu_name" ]; then
    if [ -f /sys/class/kgsl/kgsl-3d0/gpu_model ]; then
      gpu_name=$(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null)
    elif [ -f /proc/gpuinfo ]; then
      gpu_name=$(grep -i "Renderer" /proc/gpuinfo 2>/dev/null | head -1 | cut -d: -f2 | sed 's/^ *//')
    fi
  fi

  # GPU使用率 (高通kgsl)
  if [ -z "$gpu_usage" ]; then
    if [ -f /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage ]; then
      gpu_usage=$(cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null | tr -d '%')
    fi
  fi

  # GPU温度 (如果有专门的thermal zone)
  if [ -z "$gpu_temp" ]; then
    for zone in /sys/class/thermal/thermal_zone*/type; do
      if [ -f "$zone" ]; then
        ztype=$(cat "$zone" 2>/dev/null)
        case "$ztype" in
          *gpu*|*GPU*|*kgsl*|*adreno*)
            zpath=$(dirname "$zone")/temp
            if [ -f "$zpath" ]; then
              raw=$(cat "$zpath")
              if [ "$raw" -gt 1000 ]; then
                gpu_temp=$(awk "BEGIN {printf \"%.1f\", $raw / 1000}")
              else
                gpu_temp="$raw"
              fi
              break
            fi
            ;;
        esac
      fi
    done
  fi

  echo "${gpu_name:-}|${gpu_usage:-}|${gpu_temp:-}|${gpu_mem_total:-}|${gpu_mem_used:-}"
}

# 内存信息（增强：增加cached/buffers）
get_memory() {
  total=$(free -b 2>/dev/null | awk '/Mem:/ {print $2}')
  used=$(free -b 2>/dev/null | awk '/Mem:/ {print $3}')
  available=$(free -b 2>/dev/null | awk '/Mem:/ {print $7}')
  cached=$(free -b 2>/dev/null | awk '/Mem:/ {print $6}')
  buffers=$(free -b 2>/dev/null | awk '/Mem:/ {print $5}')

  if [ -z "$total" ]; then
    total=$(free | awk '/Mem:/ {print $2}')
    used=$(free | awk '/Mem:/ {print $3}')
    available=$(free | awk '/Mem:/ {print $4}')
    cached="0"
    buffers="0"
    total=$((total * 1024))
    used=$((used * 1024))
    available=$((available * 1024))
  fi

  if [ "$total" -gt 0 ]; then
    usage=$(awk "BEGIN {printf \"%.1f\", $used / $total * 100}")
  else
    usage="0.0"
  fi

  echo "$total|$used|$available|$usage|${cached:-0}|${buffers:-0}"
}

# Swap信息
get_swap() {
  swap_total=$(free -b 2>/dev/null | awk '/Swap:/ {print $2}')
  swap_used=$(free -b 2>/dev/null | awk '/Swap:/ {print $3}')

  if [ -z "$swap_total" ]; then
    swap_total=$(free | awk '/Swap:/ {print $2}')
    swap_used=$(free | awk '/Swap:/ {print $3}')
    swap_total=$((swap_total * 1024))
    swap_used=$((swap_used * 1024))
  fi

  if [ "$swap_total" -gt 0 ] 2>/dev/null; then
    swap_usage=$(awk "BEGIN {printf \"%.1f\", $swap_used / $swap_total * 100}")
  else
    swap_usage="0.0"
  fi

  echo "${swap_total:-0}|${swap_used:-0}|$swap_usage"
}

# 硬盘信息
get_disks() {
  tmpfile="/tmp/df_output_$$"
  df -k 2>/dev/null > "$tmpfile"
  result="["
  first=1
  tail -n +2 "$tmpfile" | while read filesystem total used avail pct mount; do
    case "$filesystem" in
      tmpfs|devtmpfs|overlay|none|"") continue ;;
    esac
    case "$mount" in
      /dev|/run|/tmp|/sys|/proc|/dev/*|/run/*|/sys/*|/proc/*) continue ;;
    esac
    if [ "$total" -lt 10240 ] 2>/dev/null; then
      continue
    fi
    total_bytes=$(awk "BEGIN {printf \"%.0f\", $total * 1024}" 2>/dev/null || echo "$((total * 1024))")
    used_bytes=$(awk "BEGIN {printf \"%.0f\", $used * 1024}" 2>/dev/null || echo "$((used * 1024))")
    usage=$(awk "BEGIN {printf \"%.1f\", ($total > 0 ? $used / $total * 100 : 0)}" 2>/dev/null || echo "0")
    echo "{\"filesystem\":\"$filesystem\",\"mount\":\"$mount\",\"total\":$total_bytes,\"used\":$used_bytes,\"usage\":$usage}" >> "${tmpfile}_items"
  done
  if [ -f "${tmpfile}_items" ]; then
    while read line; do
      if [ "$first" -eq 1 ]; then
        first=0
      else
        result="$result,"
      fi
      result="$result$line"
    done < "${tmpfile}_items"
    rm -f "${tmpfile}_items"
  fi
  rm -f "$tmpfile"
  result="$result]"
  echo "$result"
}

# 运行时间
get_uptime() {
  if [ -f /proc/uptime ]; then
    uptime_sec=$(cat /proc/uptime | awk '{print int($1)}')
    echo "$uptime_sec"
  else
    echo "0"
  fi
}

# 系统负载
get_load() {
  load1=$(cat /proc/loadavg | awk '{print $1}')
  load5=$(cat /proc/loadavg | awk '{print $2}')
  load15=$(cat /proc/loadavg | awk '{print $3}')
  echo "$load1|$load5|$load15"
}

# 进程数
get_process_count() {
  if [ -d /proc ]; then
    count=$(ls -d /proc/[0-9]* 2>/dev/null | wc -l)
    echo "$count"
  else
    echo "0"
  fi
}

# 内核版本
get_kernel_version() {
  uname -r 2>/dev/null || echo "unknown"
}

# 系统架构
get_architecture() {
  uname -m 2>/dev/null || echo "unknown"
}

# 主机名
get_hostname() {
  hostname 2>/dev/null || cat /etc/hostname 2>/dev/null || echo "unknown"
}

# 操作系统信息
get_os_info() {
  os_name=""
  os_version=""

  if [ -f /etc/os-release ]; then
    os_name=$(grep -E "^NAME=" /etc/os-release 2>/dev/null | cut -d= -f2 | tr -d '"')
    os_version=$(grep -E "^VERSION_ID=" /etc/os-release 2>/dev/null | cut -d= -f2 | tr -d '"')
  fi

  if [ -z "$os_name" ]; then
    os_name=$(uname -s 2>/dev/null)
  fi

  echo "${os_name:-unknown}|${os_version:-unknown}"
}

# 电量
get_battery() {
  battery=""
  charging="false"

  for ps in /sys/class/power_supply/*/; do
    [ -d "$ps" ] || continue
    if [ -f "${ps}capacity" ]; then
      ptype=$(cat "${ps}type" 2>/dev/null | tr 'A-Z' 'a-z')
      if [ "$ptype" = "battery" ] || [ -z "$ptype" ]; then
        battery=$(cat "${ps}capacity")
        if [ -f "${ps}status" ]; then
          status=$(cat "${ps}status")
          if [ "$status" = "Charging" ] || [ "$status" = "Full" ]; then
            charging="true"
          fi
        fi
        break
      fi
    fi
  done

  if [ -z "$battery" ]; then
    for ps in /sys/class/power_supply/*/; do
      [ -d "$ps" ] || continue
      if [ -f "${ps}capacity" ]; then
        battery=$(cat "${ps}capacity")
        if [ -f "${ps}status" ]; then
          status=$(cat "${ps}status")
          if [ "$status" = "Charging" ] || [ "$status" = "Full" ]; then
            charging="true"
          fi
        fi
        break
      fi
    done
  fi

  if [ "$charging" = "false" ] && [ -z "$battery" ]; then
    for ps in /sys/class/power_supply/*/; do
      [ -d "$ps" ] || continue
      if [ -f "${ps}online" ]; then
        if [ "$(cat "${ps}online")" = "1" ]; then
          charging="true"
          break
        fi
      fi
    done
  fi

  echo "${battery:-}|$charging"
}

# WiFi信息
get_wifi_info() {
  wifi_ssid=""
  wifi_signal=""
  wifi_rate=""
  wifi_freq=""
  wifi_bssid=""

  # 查找WiFi接口
  wifi_iface=""
  for iface in wlan0 wlan1 wlp2s0 wlp3s0 wlp4s0 wlp5s0 ra0 ra1; do
    if [ -d "/sys/class/net/$iface" ]; then
      wifi_iface="$iface"
      break
    fi
  done

  if [ -n "$wifi_iface" ]; then
    # 用iw获取信息
    if command -v iw >/dev/null 2>&1; then
      link_info=$(iw dev "$wifi_iface" link 2>/dev/null)
      wifi_ssid=$(echo "$link_info" | grep -i "SSID" | head -1 | awk '{print $2}')
      wifi_signal=$(echo "$link_info" | grep -i "signal" | head -1 | awk '{print $2}')
      wifi_rate=$(echo "$link_info" | grep -i "bitrate" | head -1 | awk '{print $2}')
      wifi_freq=$(echo "$link_info" | grep -i "freq" | head -1 | awk '{print $2}')
      wifi_bssid=$(echo "$link_info" | grep -i "Connected to" | head -1 | awk '{print $3}')
    fi

    # 用iwconfig作为备用
    if [ -z "$wifi_ssid" ] && command -v iwconfig >/dev/null 2>&1; then
      wifi_ssid=$(iwconfig "$wifi_iface" 2>/dev/null | grep -i "ESSID" | head -1 | cut -d'"' -f2)
      wifi_signal=$(iwconfig "$wifi_iface" 2>/dev/null | grep -i "Signal level" | head -1 | grep -oP '\-\d+' | head -1)
      wifi_rate=$(iwconfig "$wifi_iface" 2>/dev/null | grep -i "Bit Rate" | head -1 | grep -oP '\d+\.?\d*' | head -1)
    fi

    # 从/sys获取频率
    if [ -z "$wifi_freq" ]; then
      if [ -f "/sys/class/net/$wifi_iface/wireless/frequency" ]; then
        wifi_freq=$(cat "/sys/class/net/$wifi_iface/wireless/frequency" 2>/dev/null)
      fi
    fi
  fi

  echo "${wifi_ssid:-}|${wifi_signal:-}|${wifi_rate:-}|${wifi_freq:-}|${wifi_bssid:-}|${wifi_iface:-}"
}

# 网络接口信息
get_network_interfaces() {
  result="["
  first=1

  for iface in /sys/class/net/*; do
    iface_name=$(basename "$iface")
    # 跳过lo和虚拟接口
    case "$iface_name" in
      lo|docker*|veth*|br-*|virbr*) continue ;;
    esac

    # 获取IP地址
    ip_addr=$(ip addr show "$iface_name" 2>/dev/null | grep 'inet ' | head -1 | awk '{print $2}' | cut -d/ -f1)
    # 获取MAC地址
    mac_addr=$(cat "/sys/class/net/$iface_name/address" 2>/dev/null)
    # 获取状态
    state=$(cat "/sys/class/net/$iface_name/operstate" 2>/dev/null)
    # 获取速度
    speed=$(cat "/sys/class/net/$iface_name/speed" 2>/dev/null)
    [ "$speed" = "-1" ] && speed=""

    # 只有有IP或状态up的才显示
    if [ -n "$ip_addr" ] || [ "$state" = "up" ]; then
      if [ "$first" -eq 1 ]; then
        first=0
      else
        result="$result,"
      fi
      result="$result{\"name\":\"$iface_name\",\"ip\":\"${ip_addr:-}\",\"mac\":\"${mac_addr:-}\",\"state\":\"${state:-unknown}\",\"speed\":\"${speed:-}\"}"
    fi
  done

  result="$result]"
  echo "$result"
}

# 网络信息（主IP）
# 云服务器优先获取公网IP，再回退到网卡内网IP
get_network() {
  ip_addr=""
  public_ip=""

  # 1. 优先通过外部服务获取公网IP（云服务器必需）
  if command -v curl >/dev/null 2>&1; then
    public_ip=$(curl -s --connect-timeout 3 --max-time 5 ifconfig.me 2>/dev/null)
    if [ -z "$public_ip" ] || ! echo "$public_ip" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
      public_ip=$(curl -s --connect-timeout 3 --max-time 5 ipinfo.io/ip 2>/dev/null)
    fi
    if [ -z "$public_ip" ] || ! echo "$public_ip" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
      public_ip=$(curl -s --connect-timeout 3 --max-time 5 api.ipify.org 2>/dev/null)
    fi
  elif command -v wget >/dev/null 2>&1; then
    public_ip=$(wget -q -O- --timeout=5 ifconfig.me 2>/dev/null)
  fi

  # 2. 获取网卡内网IP
  # 优先获取 eth0 (有线) 的 IP
  ip_addr=$(ip addr show eth0 2>/dev/null | grep 'inet ' | head -1 | awk '{print $2}' | cut -d/ -f1)
  # 其次获取 wlan0 (WiFi) 的 IP
  if [ -z "$ip_addr" ]; then
    ip_addr=$(ip addr show wlan0 2>/dev/null | grep 'inet ' | head -1 | awk '{print $2}' | cut -d/ -f1)
  fi
  # 获取第一个非本地、非usb0的真实网卡IP
  if [ -z "$ip_addr" ]; then
    ip_addr=$(ip addr show 2>/dev/null | grep 'inet ' | grep -vE '127\.0\.0\.1|10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.' | head -1 | awk '{print $2}' | cut -d/ -f1)
  fi
  # 最后兜底
  if [ -z "$ip_addr" ]; then
    ip_addr=$(ip addr show 2>/dev/null | grep 'inet ' | grep -v '127.0.0.1' | head -1 | awk '{print $2}' | cut -d/ -f1)
  fi

  # 3. 如果有公网IP，优先显示公网IP；否则显示内网IP
  if [ -n "$public_ip" ] && echo "$public_ip" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "$public_ip"
  else
    echo "$ip_addr"
  fi
}

# ========== 主循环 ==========
echo "============================================"
echo "  设备监控 Agent v1.3.0 启动"
echo "  设备: $DEVICE_NAME ($DEVICE_ID)"
echo "  服务器: $SERVER_URL"
echo "  上报间隔: ${INTERVAL}秒"
echo "============================================"

while true; do
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] 采集数据..."

  # 采集各项数据
  cpu_usage=$(get_cpu_usage)
  cpu_cores=$(get_cpu_cores)
  cpu_model=$(get_cpu_model)
  cpu_temp=$(get_cpu_temp)
  gpu_info=$(get_gpu_info)
  gpu_name=$(echo "$gpu_info" | cut -d'|' -f1)
  gpu_usage=$(echo "$gpu_info" | cut -d'|' -f2)
  gpu_temp=$(echo "$gpu_info" | cut -d'|' -f3)
  gpu_mem_total=$(echo "$gpu_info" | cut -d'|' -f4)
  gpu_mem_used=$(echo "$gpu_info" | cut -d'|' -f5)
  mem_info=$(get_memory)
  mem_total=$(echo "$mem_info" | cut -d'|' -f1)
  mem_used=$(echo "$mem_info" | cut -d'|' -f2)
  mem_available=$(echo "$mem_info" | cut -d'|' -f3)
  mem_usage=$(echo "$mem_info" | cut -d'|' -f4)
  mem_cached=$(echo "$mem_info" | cut -d'|' -f5)
  mem_buffers=$(echo "$mem_info" | cut -d'|' -f6)
  swap_info=$(get_swap)
  swap_total=$(echo "$swap_info" | cut -d'|' -f1)
  swap_used=$(echo "$swap_info" | cut -d'|' -f2)
  swap_usage=$(echo "$swap_info" | cut -d'|' -f3)
  disks=$(get_disks)
  uptime_sec=$(get_uptime)
  load_info=$(get_load)
  load1=$(echo "$load_info" | cut -d'|' -f1)
  load5=$(echo "$load_info" | cut -d'|' -f2)
  load15=$(echo "$load_info" | cut -d'|' -f3)
  process_count=$(get_process_count)
  kernel_version=$(get_kernel_version)
  architecture=$(get_architecture)
  hostname_val=$(get_hostname)
  os_info=$(get_os_info)
  os_name=$(echo "$os_info" | cut -d'|' -f1)
  os_version=$(echo "$os_info" | cut -d'|' -f2)
  battery_info=$(get_battery)
  battery=$(echo "$battery_info" | cut -d'|' -f1)
  charging=$(echo "$battery_info" | cut -d'|' -f2)
  wifi_info=$(get_wifi_info)
  wifi_ssid=$(echo "$wifi_info" | cut -d'|' -f1)
  wifi_signal=$(echo "$wifi_info" | cut -d'|' -f2)
  wifi_rate=$(echo "$wifi_info" | cut -d'|' -f3)
  wifi_freq=$(echo "$wifi_info" | cut -d'|' -f4)
  wifi_bssid=$(echo "$wifi_info" | cut -d'|' -f5)
  wifi_iface=$(echo "$wifi_info" | cut -d'|' -f6)
  network_interfaces=$(get_network_interfaces)
  ip_addr=$(get_network)

  # 构建 JSON
  json_data="{"
  json_data="$json_data\"cpu_usage\":$cpu_usage,"
  json_data="$json_data\"cpu_cores\":$cpu_cores,"
  json_data="$json_data\"cpu_model\":\"$cpu_model\","
  json_data="$json_data\"cpu_temp\":$cpu_temp,"
  json_data="$json_data\"gpu_name\":\"$gpu_name\","
  json_data="$json_data\"gpu_usage\":${gpu_usage:-null},"
  json_data="$json_data\"gpu_temp\":${gpu_temp:-null},"
  json_data="$json_data\"gpu_memory_total\":${gpu_mem_total:-null},"
  json_data="$json_data\"gpu_memory_used\":${gpu_mem_used:-null},"
  json_data="$json_data\"memory_total\":$mem_total,"
  json_data="$json_data\"memory_used\":$mem_used,"
  json_data="$json_data\"memory_available\":$mem_available,"
  json_data="$json_data\"memory_usage\":$mem_usage,"
  json_data="$json_data\"memory_cached\":$mem_cached,"
  json_data="$json_data\"memory_buffers\":$mem_buffers,"
  json_data="$json_data\"swap_total\":$swap_total,"
  json_data="$json_data\"swap_used\":$swap_used,"
  json_data="$json_data\"swap_usage\":$swap_usage,"
  json_data="$json_data\"disks\":$disks,"
  json_data="$json_data\"uptime\":$uptime_sec,"
  json_data="$json_data\"load_1\":$load1,"
  json_data="$json_data\"load_5\":$load5,"
  json_data="$json_data\"load_15\":$load15,"
  json_data="$json_data\"process_count\":$process_count,"
  json_data="$json_data\"kernel_version\":\"$kernel_version\","
  json_data="$json_data\"architecture\":\"$architecture\","
  json_data="$json_data\"hostname\":\"$hostname_val\","
  json_data="$json_data\"os\":\"$os_name\","
  json_data="$json_data\"os_version\":\"$os_version\","
  json_data="$json_data\"wifi_ssid\":\"$wifi_ssid\","
  json_data="$json_data\"wifi_signal\":\"$wifi_signal\","
  json_data="$json_data\"wifi_rate\":\"$wifi_rate\","
  json_data="$json_data\"wifi_freq\":\"$wifi_freq\","
  json_data="$json_data\"wifi_bssid\":\"$wifi_bssid\","
  json_data="$json_data\"wifi_interface\":\"$wifi_iface\","
  json_data="$json_data\"network_interfaces\":$network_interfaces,"
  json_data="$json_data\"ip_address\":\"$ip_addr\""

  if [ -n "$battery" ]; then
    json_data="$json_data,\"battery\":$battery,\"charging\":$charging"
  fi

  json_data="$json_data}"

  # 上报到服务器
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] 上报: CPU=${cpu_usage}% 内存=${mem_usage}% 温度=${cpu_temp}°C GPU=${gpu_name:-N/A} WiFi=${wifi_ssid:-N/A}"

  if command -v curl >/dev/null 2>&1; then
    curl -s -X POST "$SERVER_URL/api/report" \
      -H "Content-Type: application/json" \
      -H "x-api-key: $API_KEY" \
      -d "{\"device_id\":\"$DEVICE_ID\",\"device_name\":\"$DEVICE_NAME\",\"device_type\":\"$DEVICE_TYPE\",\"data\":$json_data}" \
      --connect-timeout 10 --max-time 15 >/dev/null 2>&1
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O- --post-data="{\"device_id\":\"$DEVICE_ID\",\"device_name\":\"$DEVICE_NAME\",\"device_type\":\"$DEVICE_TYPE\",\"data\":$json_data}" \
      --header="Content-Type: application/json" \
      --header="x-api-key: $API_KEY" \
      "$SERVER_URL/api/report" >/dev/null 2>&1
  fi

  # 等待下一次上报
  sleep $INTERVAL
done
