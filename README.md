# 多设备状态监控系统

一个赛博朋克科幻风格的多设备状态监控系统，支持监控服务器、手机、路由器、电脑等设备的 CPU、内存、硬盘、温度、电量、运行时间等实时状态。

## 快速配置（重要）

仓库中的 `YOUR_API_KEY`、`YOUR_SERVER_IP`、`YOUR_API_KEY`（Base64 形式 `WU9VUl9BUElfS0VZ`）均为**占位符**，使用前请替换为你自己的值：

| 位置 | 说明 |
|---|---|
| `server/server.js` | 启动前设置环境变量 `API_KEY`（服务端不内置默认 Key） |
| `server/device-monitor-version.json` | 应用内更新清单，改回你的服务器地址 |
| `agent/linux/*.sh` / `agent/openwrt/device-agent` | 顶部 `SERVER_URL` / `API_KEY` 变量 |
| `agent/windows/*.ps1` | 顶部 `$SERVER_URL` / `$API_KEY` 变量 |
| `android/.../util/Constants.kt` | `DEFAULT_SERVER_URL` / `API_KEY` |

> **Windows 温度采集**：依赖 [OpenHardwareMonitor](https://openhardwaremonitor.org/)，出于体积与许可考虑仓库未附带二进制，请自行下载并解压到 `agent/windows/openhardwaremonitor/extracted/OpenHardwareMonitor/`。

## 系统架构

```
阿里云服务器（中心服务）
    ↓ ↑
各设备 Agent（数据采集上报）
    ↓ ↑
Android APP（科幻风格界面展示）
```

## 目录结构

```
DeviceMonitor/
├── server/                 # 中心服务器（Node.js + SQLite）
│   ├── package.json
│   └── server.js
├── agent/
│   ├── linux/              # Linux Agent（通用：服务器 / 手机 / 平板 / 开发板）
│   │   ├── device-agent.sh
│   │   ├── device-agent.service
│   │   └── install.sh
│   ├── openwrt/            # OpenWrt Agent（软路由 / 嵌入式设备）
│   │   ├── device-agent
│   │   └── install.sh
│   └── windows/            # Windows Agent（PowerShell，零外部依赖）
│       ├── device-agent.ps1     # 主程序：采集 + 30秒上报（常驻循环）
│       ├── agent-watcher.ps1    # 守护：每分钟检查并拉起主程序
│       ├── install.bat          # 安装入口（双击，需管理员）
│       ├── install.ps1          # 安装逻辑（配置写入 / 任务注册 / 验证）
│       ├── openhardwaremonitor/ # 温度采集组件（项目内自带，无需另装）
│       ├── agent.log            # 运行日志（5MB 自动轮转）
│       └── agent-watcher.log    # 守护日志
└── android/                # Android APP（Kotlin + Jetpack Compose）
    └── DeviceMonitor/
```

## 快速部署

### 1. 部署中心服务器（阿里云）

```bash
# 上传 server 目录到阿里云
cd server
npm install
# 配置微信推送（可选）
export WECHAT_SENDKEY="你的Server酱SendKey"
# 启动服务
node server.js
# 或使用 systemd 开机自启（参考 agent/linux/device-agent.service）
```

服务器默认监听 `8443` 端口，请在阿里云安全组开放该端口。

### 2. 安装设备 Agent

#### Linux 设备

```bash
chmod +x install.sh
./install.sh 设备ID 设备名称 设备类型 服务器地址
# 示例:
./install.sh my-server 我的服务器 server http://1.2.3.4:8443
```

设备类型可选：`server`、`phone`、`tablet`、`router`、`pc`

#### OpenWrt 设备

```bash
chmod +x install.sh
./install.sh my-router 家里的路由器 http://1.2.3.4:8443
```

#### Windows 电脑

**纯 PowerShell 实现，不需要安装 Python 或任何第三方库。**

1. 右键 `agent\windows\install.bat` → **以管理员身份运行**
2. 按提示输入服务器地址、设备 ID、设备名称（直接回车用默认值）
3. 脚本会自动完成：环境检查 → 编码修复 → 停止旧实例 → 注册计划任务 →
   写入配置 → 启动 Agent → 等待首轮上报并打印结果

也可以用命令行静默安装（免交互）：

```powershell
# 安装（参数全部指定时不进入交互）
.\agent\windows\install.ps1 -ServerUrl "http://1.2.3.4:8443" -DeviceId "windows" -DeviceName "Windows PC"

# 修改配置但保留其他项
.\agent\windows\install.ps1 -KeepConfig -Interval 60

# 卸载（停 Agent + 注销计划任务，保留项目文件）
.\agent\windows\install.ps1 -Uninstall
```

**运行机制**

| 计划任务 | 频率 | 作用 |
|---|---|---|
| `DeviceAgentWatcher` | 每 1 分钟 | 运行 `agent-watcher.ps1`，检测到 Agent 未运行就拉起 |

`MultipleInstances = IgnoreNew`，重复触发不会产生多实例。
Agent 内部为常驻循环，每 30 秒采集上报一次；温度依赖项目自带的
OpenHardwareMonitor（组件随项目分发，无需单独安装）。

**手动运维命令**

```powershell
Get-ScheduledTask -TaskName DeviceAgentWatcher        # 查看任务状态
Start-ScheduledTask -TaskName DeviceAgentWatcher      # 手动触发一次
Get-Content agent\windows\agent.log -Tail 50 -Wait    # 实时看日志
Stop-ScheduledTask -TaskName DeviceAgentWatcher       # 暂停守护
```

> ⚠️ **修改 agent 脚本必须保持 UTF-8 BOM**。PowerShell 5.1 读取无 BOM 的
> `.ps1` 会按 GBK 解码，脚本内的中文路径将全部乱码，导致 Agent 静默空转
> （表现为计划任务执行成功但日志不生成、也不上报）。`install.ps1` 每次运行
> 都会自动检查并补写 BOM。

### 3. 安装 Android APP

1. 使用 Android Studio 打开 `android/DeviceMonitor` 项目
2. 编译生成 APK
3. 安装到手机
4. 打开 APP，进入设置页配置服务器地址
5. 返回首页即可查看所有设备状态

## 功能特性

### 中心服务器
- 接收各设备数据上报
- SQLite 存储历史数据（保留30天）
- REST API 供 APP 查询
- 微信告警推送（Server酱）
- 设备离线自动检测
- 告警阈值可配置

### 设备 Agent
- 轻量级，资源占用极低
- 自动采集 CPU、内存、硬盘、温度、电量
- 30秒自动上报
- 开机自启
- 断线自动重连

### Android APP
- 赛博朋克科幻风格 UI
- 深色主题 + 霓虹发光效果
- 设备列表实时状态
- 设备详情页（圆形进度条、数据图表）
- 系统概览（在线/离线/告警统计）
- 自动刷新（30秒）
- 服务器地址可配置

## 告警规则

| 告警类型 | 阈值 | 级别 |
|---------|------|------|
| CPU 使用率 | > 90% | 警告 |
| CPU 温度 | > 80°C | 严重 |
| 内存使用率 | > 90% | 警告 |
| 电量 | < 20%（未充电） | 警告 |
| 硬盘使用率 | > 90% | 警告 |
| 设备离线 | > 2分钟 | 严重 |

## 微信推送配置

1. 访问 https://sct.ftqq.com/ 注册 Server酱
2. 获取 SendKey
3. 启动服务器时设置环境变量：
   ```bash
   export WECHAT_SENDKEY="你的SendKey"
   node server.js
   ```

## 技术栈

- **中心服务器**: Node.js + Express + SQLite
- **Linux Agent**: Shell Script
- **OpenWrt Agent**: Shell Script (BusyBox)
- **Windows Agent**: PowerShell 5.1（零外部依赖，内置 OpenHardwareMonitor 采集温度）
- **Android APP**: Kotlin + Jetpack Compose + Retrofit

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
