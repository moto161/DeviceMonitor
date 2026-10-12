package com.devicemonitor.app.data

import com.google.gson.annotations.SerializedName
import java.util.Locale

// 设备数据模型
data class Device(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("status") val status: String,
    @SerializedName("last_seen") val lastSeen: Long,
    @SerializedName("latest_data") val latestData: DeviceData?
)

data class DeviceData(
    @SerializedName("cpu_usage") val cpuUsage: Double?,
    @SerializedName("cpu_cores") val cpuCores: Int?,
    @SerializedName("cpu_model") val cpuModel: String?,
    @SerializedName("cpu_temp") val cpuTemp: Double?,
    @SerializedName("gpu_name") val gpuName: String?,
    @SerializedName("gpu_usage") val gpuUsage: Double?,
    @SerializedName("gpu_temp") val gpuTemp: Double?,
    @SerializedName("gpu_memory_total") val gpuMemoryTotal: Long?,
    @SerializedName("gpu_memory_used") val gpuMemoryUsed: Long?,
    @SerializedName("memory_total") val memoryTotal: Long?,
    @SerializedName("memory_used") val memoryUsed: Long?,
    @SerializedName("memory_available") val memoryAvailable: Long?,
    @SerializedName("memory_usage") val memoryUsage: Double?,
    @SerializedName("memory_cached") val memoryCached: Long?,
    @SerializedName("memory_buffers") val memoryBuffers: Long?,
    @SerializedName("swap_total") val swapTotal: Long?,
    @SerializedName("swap_used") val swapUsed: Long?,
    @SerializedName("swap_usage") val swapUsage: Double?,
    @SerializedName("disks") val disks: List<DiskInfo>?,
    @SerializedName("uptime") val uptime: Long?,
    @SerializedName("load_1") val load1: Double?,
    @SerializedName("load_5") val load5: Double?,
    @SerializedName("load_15") val load15: Double?,
    @SerializedName("process_count") val processCount: Int?,
    @SerializedName("kernel_version") val kernelVersion: String?,
    @SerializedName("architecture") val architecture: String?,
    @SerializedName("hostname") val hostname: String?,
    @SerializedName("ip_address") val ipAddress: String?,
    @SerializedName("public_ip") val publicIp: String?,
    @SerializedName("battery") val battery: Int?,
    @SerializedName("charging") val charging: Boolean?,
    @SerializedName("wifi_ssid") val wifiSsid: String?,
    @SerializedName("wifi_signal") val wifiSignal: String?,
    @SerializedName("wifi_rate") val wifiRate: String?,
    @SerializedName("wifi_freq") val wifiFreq: String?,
    @SerializedName("wifi_bssid") val wifiBssid: String?,
    @SerializedName("wifi_interface") val wifiInterface: String?,
    @SerializedName("network_interfaces") val networkInterfaces: List<NetworkInterfaceInfo>?,
    @SerializedName("wan_status") val wanStatus: String?,
    @SerializedName("wan_ip") val wanIp: String?,
    @SerializedName("clients_count") val clientsCount: Int?,
    @SerializedName("os") val os: String?,
    @SerializedName("os_version") val osVersion: String?
)

data class NetworkInterfaceInfo(
    @SerializedName("name") val name: String,
    @SerializedName("ip") val ip: String?,
    @SerializedName("mac") val mac: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("speed") val speed: String?
)

data class DiskInfo(
    @SerializedName("filesystem") val filesystem: String,
    @SerializedName("mount") val mount: String,
    @SerializedName("total") val total: Long,
    @SerializedName("used") val used: Long,
    @SerializedName("usage") val usage: Double
)

// API 响应模型
data class DevicesResponse(
    @SerializedName("devices") val devices: List<Device>
)

data class DeviceDetailResponse(
    @SerializedName("device") val device: DeviceDetail
)

data class DeviceDetail(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("status") val status: String,
    @SerializedName("last_seen") val lastSeen: Long,
    @SerializedName("latest_data") val latestData: DeviceData?,
    @SerializedName("latest_timestamp") val latestTimestamp: Long?
)

data class HistoryResponse(
    @SerializedName("history") val history: List<HistoryPoint>
)

data class HistoryPoint(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("data") val data: DeviceData
)

data class AlertsResponse(
    @SerializedName("alerts") val alerts: List<Alert>
)

data class DeleteResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("deleted") val deleted: Int?
)

data class Alert(
    @SerializedName("id") val id: Int,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String?,
    @SerializedName("type") val type: String,
    @SerializedName("message") val message: String,
    @SerializedName("level") val level: String,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("acknowledged") val acknowledged: Int
)

data class SystemStatus(
    @SerializedName("server_time") val serverTime: Long,
    @SerializedName("total_devices") val totalDevices: Int,
    @SerializedName("online_devices") val onlineDevices: Int,
    @SerializedName("offline_devices") val offlineDevices: Int,
    @SerializedName("unacknowledged_alerts") val unacknowledgedAlerts: Int,
    @SerializedName("total_data_points") val totalDataPoints: Long,
    @SerializedName("version") val version: String
)

// 工具函数
fun Long.formatBytes(): String {
    val gb = this / (1024.0 * 1024 * 1024)
    val mb = this / (1024.0 * 1024)
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", this / 1024.0)
    }
}

fun Long.formatUptime(): String {
    val days = this / 86400
    val hours = (this % 86400) / 3600
    val minutes = (this % 3600) / 60
    return when {
        days > 0 -> "${days}天${hours}小时"
        hours > 0 -> "${hours}小时${minutes}分"
        else -> "${minutes}分钟"
    }
}

// 线程安全的SimpleDateFormat缓存，避免每次调用都创建新对象
private val timeFormatThreadLocal = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
}

fun Long.formatTime(): String {
    return timeFormatThreadLocal.get().format(java.util.Date(this))
}
