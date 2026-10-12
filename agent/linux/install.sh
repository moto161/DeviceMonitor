#!/bin/sh
# ============================================
# 设备监控 Linux Agent 一键安装脚本
# 用法: ./install.sh 设备ID 设备名称 设备类型 服务器地址
# 示例: ./install.sh my-server 我的服务器 server http://1.2.3.4:8443
# ============================================

set -e

DEVICE_ID="${1:-my-device}"
DEVICE_NAME="${2:-我的设备}"
DEVICE_TYPE="${3:-server}"
SERVER_URL="${4:-http://your-server-ip:8443}"

echo "============================================"
echo "  设备监控 Agent 安装"
echo "  设备ID: $DEVICE_ID"
echo "  设备名称: $DEVICE_NAME"
echo "  设备类型: $DEVICE_TYPE"
echo "  服务器: $SERVER_URL"
echo "============================================"

# 检查是否为 root
if [ "$(id -u)" != "0" ]; then
  echo "错误: 请使用 root 用户运行"
  exit 1
fi

# 复制脚本
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cp "$SCRIPT_DIR/device-agent.sh" /usr/local/bin/device-agent.sh
chmod +x /usr/local/bin/device-agent.sh

# 修改配置
sed -i "s|SERVER_URL=.*|SERVER_URL=\"$SERVER_URL\"|" /usr/local/bin/device-agent.sh
sed -i "s|DEVICE_ID=.*|DEVICE_ID=\"$DEVICE_ID\"|" /usr/local/bin/device-agent.sh
sed -i "s|DEVICE_NAME=.*|DEVICE_NAME=\"$DEVICE_NAME\"|" /usr/local/bin/device-agent.sh
sed -i "s|DEVICE_TYPE=.*|DEVICE_TYPE=\"$DEVICE_TYPE\"|" /usr/local/bin/device-agent.sh

# 安装 systemd 服务
if command -v systemctl >/dev/null 2>&1; then
  cp "$SCRIPT_DIR/device-agent.service" /etc/systemd/system/device-agent.service
  systemctl daemon-reload
  systemctl enable device-agent
  systemctl start device-agent
  echo ""
  echo "✅ 安装完成！服务已启动并设置开机自启"
  echo "   查看状态: systemctl status device-agent"
  echo "   查看日志: journalctl -u device-agent -f"
else
  # OpenRC 或其他 init 系统
  echo ""
  echo "⚠️  未检测到 systemd，请手动设置开机自启"
  echo "   启动命令: /usr/local/bin/device-agent.sh &"
fi

echo ""
echo "安装路径: /usr/local/bin/device-agent.sh"
echo "配置文件: /usr/local/bin/device-agent.sh (直接编辑修改配置)"
echo "============================================"
