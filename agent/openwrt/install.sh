#!/bin/sh
# ============================================
# 设备监控 OpenWrt Agent 安装脚本
# 用法: ./install.sh 设备ID 设备名称 服务器地址
# 示例: ./install.sh my-router 家里的路由器 http://1.2.3.4:8443
# ============================================

DEVICE_ID="${1:-your-router}"
DEVICE_NAME="${2:-R2S软路由}"
SERVER_URL="${3:-http://your-server-ip:8443}"

echo "============================================"
echo "  设备监控 OpenWrt Agent 安装"
echo "  设备ID: $DEVICE_ID"
echo "  设备名称: $DEVICE_NAME"
echo "  服务器: $SERVER_URL"
echo "============================================"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 复制到 init.d
cp "$SCRIPT_DIR/device-agent" /etc/init.d/device-agent
chmod +x /etc/init.d/device-agent

# 修改配置
sed -i "s|SERVER_URL=.*|SERVER_URL=\"$SERVER_URL\"|" /etc/init.d/device-agent
sed -i "s|DEVICE_ID=.*|DEVICE_ID=\"$DEVICE_ID\"|" /etc/init.d/device-agent
sed -i "s|DEVICE_NAME=.*|DEVICE_NAME=\"$DEVICE_NAME\"|" /etc/init.d/device-agent

# 启用并启动
/etc/init.d/device-agent enable
/etc/init.d/device-agent start

echo ""
echo "✅ 安装完成！"
echo "   服务已启动并设置开机自启"
echo "   查看状态: /etc/init.d/device-agent status"
echo "   查看日志: tail -f /var/log/device-agent.log"
echo "   重启: /etc/init.d/device-agent restart"
echo "============================================"
