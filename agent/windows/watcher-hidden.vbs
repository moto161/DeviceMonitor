' Watcher 无窗口启动脚本
' 由Windows计划任务调用，完全隐藏PowerShell窗口
Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "powershell.exe -ExecutionPolicy Bypass -NonInteractive -File """ & Replace(WScript.ScriptFullName, "watcher-hidden.vbs", "agent-watcher.ps1") & """", 0, False
Set WshShell = Nothing
