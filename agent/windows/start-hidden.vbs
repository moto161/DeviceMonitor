' ============================================================
' 静默启动设备监控 Agent（VBS 包装，专治 PowerShell 窗口弹窗）
' 2026-10-07 新增
'
' 【为什么需要这个文件】
' 计划任务 DeviceAgentWatcher 每分钟调用 agent-watcher.ps1，
' 而它原来用 Start-Process -WindowStyle Hidden 启动 agent。
' 但 -WindowStyle Hidden 对 "powershell.exe -File xxx.ps1" 这种启动方式
' 并不能阻止控制台窗口被创建 —— 表现为每隔一两分钟屏幕中央闪一下
' PowerShell 窗口，干扰正在进行的操作。
'
' 【解决办法】
' 用 VBS 的 WScript.Shell.Run(命令, 0, False)：
'   第二个参数 0 = 窗口隐藏
'   第三个参数 False = 异步执行（不等待，立即返回）
' 这样 agent 就能完全无窗口地在后台常驻运行。
' ============================================================

Option Explicit

Dim shell, agentPath

' 直接写死绝对路径，避免中文路径解析问题
agentPath = "E:\设备监控\DeviceMonitor\agent\windows\device-agent.ps1"

Set shell = CreateObject("WScript.Shell")
' 0 = 隐藏窗口, False = 不等待（异步）
shell.Run "powershell.exe -ExecutionPolicy Bypass -File """ & agentPath & """", 0, False
