package com.devicemonitor.app.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devicemonitor.app.components.*
import com.devicemonitor.app.data.*
import com.devicemonitor.app.ui.theme.*
import com.devicemonitor.app.ui.components.getDeviceIcon
import com.devicemonitor.app.ui.components.NetworkInterfaceItem
import com.devicemonitor.app.ui.components.LoadItem
import com.devicemonitor.app.ui.components.DiskItem
import com.devicemonitor.app.ui.components.LegendDot
import com.devicemonitor.app.ui.components.HistoryChart
import com.devicemonitor.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    deviceId: String,
    onBack: () -> Unit
) {
    var device by remember { mutableStateOf<DeviceDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf<List<HistoryPoint>>(emptyList()) }

    LaunchedEffect(deviceId) {
        // 加载历史数据（只加载一次）
        loadDeviceHistory(deviceId) { points ->
            history = points
        }
        // 定时刷新详情
        while (true) {
            loadDeviceDetail(deviceId) { detail ->
                device = detail
                isLoading = false
            }
            delay(Constants.DETAIL_REFRESH_INTERVAL_MS)
        }
    }

    Scaffold(
        containerColor = CyberBlack,
        topBar = {
            TopAppBar(
                title = {
                    device?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = getDeviceIcon(it.type),
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = it.name,
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = NeonCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberDark,
                    titleContentColor = TextWhite
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CyberBlack)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = NeonCyan)
                }
            } else {
                device?.let { detail ->
                    DeviceDetailContent(detail, history)
                }
            }
        }
    }
}

@Composable
fun DeviceDetailContent(device: DeviceDetail, history: List<HistoryPoint> = emptyList()) {
    val data = device.latestData
    val isOnline = device.status == "online"
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 状态卡片
        DeviceStatusHeader(
            device = device,
            isOnline = isOnline,
            onIpClick = { ip -> clipboardManager.setText(AnnotatedString(ip)) }
        )

        if (isOnline && data != null) {
            // CPU 和内存 圆形进度
            DevicePerformanceCard(data = data)


            // 历史趋势图表
            if (history.isNotEmpty()) {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = NeonCyan
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GlowText(
                            text = "TREND (最近趋势)",
                            color = NeonCyan,
                            fontSize = 12,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HistoryChart(history = history)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            LegendDot(color = NeonCyan, label = "CPU")
                            Spacer(modifier = Modifier.width(24.dp))
                            LegendDot(color = NeonPurple, label = "内存")
                        }
                    }
                }
            }
            // GPU信息卡片
            if (data.gpuName != null || data.gpuUsage != null || data.gpuTemp != null) {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = NeonPurple
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GlowText(
                            text = "GPU",
                            color = NeonPurple,
                            fontSize = 12,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        data.gpuName?.let {
                            DataItem("GPU 型号", it)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.gpuUsage?.let {
                            DataItem("GPU 使用率", "${it.toInt()}%")
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.gpuTemp?.let {
                            DataItem(
                                "GPU 温度",
                                "${it.toInt()}°C",
                                valueColor = if (it > 80) NeonRed else TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (data.gpuMemoryTotal != null && data.gpuMemoryTotal > 0) {
                            DataItem(
                                "GPU 显存",
                                "${(data.gpuMemoryUsed ?: 0).formatBytes()} / ${(data.gpuMemoryTotal ?: 0).formatBytes()}"
                            )
                        }
                    }
                }
            }

            // 系统负载
            data.load1?.let {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = NeonPurple
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GlowText(
                            text = "SYSTEM LOAD",
                            color = NeonPurple,
                            fontSize = 12,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            LoadItem("1分钟", data.load1 ?: 0.0)
                            LoadItem("5分钟", data.load5 ?: 0.0)
                            LoadItem("15分钟", data.load15 ?: 0.0)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        data.processCount?.let {
                            DataItem("进程数", "$it 个")
                        }
                    }
                }
            }

            // 系统信息
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonCyan
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    GlowText(
                        text = "SYSTEM INFO",
                        color = NeonCyan,
                        fontSize = 12,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    data.os?.let {
                        DataItem("操作系统", it)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    data.osVersion?.let {
                        DataItem("系统版本", it)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    data.kernelVersion?.let {
                        DataItem("内核版本", it)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    data.architecture?.let {
                        DataItem("系统架构", it)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    data.hostname?.let {
                        DataItem("主机名", it)
                    }
                }
            }

            // WiFi信息
            if (data.wifiSsid != null || data.wifiInterface != null) {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = NeonGreen
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GlowText(
                            text = "WiFi",
                            color = NeonGreen,
                            fontSize = 12,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        data.wifiSsid?.let {
                            DataItem("SSID", it)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.wifiInterface?.let {
                            DataItem("接口", it)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.wifiSignal?.let {
                            DataItem("信号强度", "$it dBm")
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.wifiRate?.let {
                            DataItem("连接速率", "$it Mbps")
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.wifiFreq?.let {
                            DataItem("频率", "$it MHz")
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.wifiBssid?.let {
                            DataItem("BSSID", it)
                        }
                    }
                }
            }

            // 网络接口
            data.networkInterfaces?.let { interfaces ->
                if (interfaces.isNotEmpty()) {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = NeonBlue
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            GlowText(
                                text = "NETWORK INTERFACES",
                                color = NeonBlue,
                                fontSize = 12,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            interfaces.forEach { iface ->
                                NetworkInterfaceItem(iface, clipboardManager)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // 磁盘信息
            data.disks?.let { disks ->
                if (disks.isNotEmpty()) {
                    CyberCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = NeonBlue
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            GlowText(
                                text = "DISK USAGE",
                                color = NeonBlue,
                                fontSize = 12,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            disks.forEach { disk ->
                                DiskItem(disk)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // 路由器特有信息
            if (device.type == "router") {
                CyberCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = NeonOrange
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GlowText(
                            text = "ROUTER INFO",
                            color = NeonOrange,
                            fontSize = 12,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        data.wanStatus?.let {
                            DataItem(
                                "WAN 状态",
                                it.uppercase(),
                                valueColor = if (it == "connected") NeonGreen else NeonRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.wanIp?.let {
                            DataItem("WAN IP", it)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        data.clientsCount?.let {
                            DataItem(
                                "在线客户端",
                                "$it 台",
                                valueColor = NeonCyan
                            )
                        }
                    }
                }
            }
        } else {
            // 离线状态
            CyberCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonRed
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NeonRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "设备离线",
                        color = NeonRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "最后在线: ${device.lastSeen.formatTime()}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}


suspend fun loadDeviceDetail(
    deviceId: String,
    onResult: (DeviceDetail?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.getApi().getDeviceDetail(deviceId).execute()
            onResult(response.body()?.device)
        } catch (e: IOException) {
            Log.w("DeviceDetail", "加载设备详情失败 id=$deviceId", e)
            onResult(null)
        }
    }
}

suspend fun loadDeviceHistory(
    deviceId: String,
    onResult: (List<HistoryPoint>) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.getApi().getDeviceHistory(deviceId).execute()
            onResult(response.body()?.history ?: emptyList())
        } catch (e: IOException) {
            Log.w("DeviceDetail", "加载设备历史失败 id=$deviceId", e)
            onResult(emptyList())
        }
    }
}


@Composable
private fun DeviceStatusHeader(
    device: DeviceDetail,
    isOnline: Boolean,
    onIpClick: (String) -> Unit
) {
    val data = device.latestData
    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (isOnline) NeonGreen else NeonRed
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIndicator(isOnline = isOnline)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ID: ${device.id}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    data?.ipAddress?.let {
                        Text(
                            text = "IP: $it",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.clickable { onIpClick(it) }
                        )
                    }
                    data?.publicIp?.let {
                        Text(
                            text = "公网: $it",
                            color = NeonCyan,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.clickable { onIpClick(it) }
                        )
                    }
                    data?.hostname?.let {
                        Text(
                            text = "主机: $it",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            data?.uptime?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("运行时间", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        it.formatUptime(),
                        color = NeonCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("最后更新", color = TextSecondary, fontSize = 12.sp)
                Text(
                    device.lastSeen.formatTime(),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}


@Composable
private fun DevicePerformanceCard(data: DeviceData) {
    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = NeonCyan
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            GlowText(
                text = "PERFORMANCE",
                color = NeonCyan,
                fontSize = 12,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                CircularProgress(
                    progress = (data.cpuUsage ?: 0.0).toFloat() / 100f,
                    size = 90,
                    color = if ((data.cpuUsage ?: 0.0) > 80) NeonYellow else NeonCyan,
                    label = "CPU",
                    value = "${data.cpuUsage?.toInt() ?: 0}%"
                )
                CircularProgress(
                    progress = (data.memoryUsage ?: 0.0).toFloat() / 100f,
                    size = 90,
                    color = if ((data.memoryUsage ?: 0.0) > 80) NeonYellow else NeonGreen,
                    label = "内存",
                    value = "${data.memoryUsage?.toInt() ?: 0}%"
                )
                data.gpuUsage?.let {
                    CircularProgress(
                        progress = it.toFloat() / 100f,
                        size = 90,
                        color = if (it > 80) NeonYellow else NeonPurple,
                        label = "GPU",
                        value = "${it.toInt()}%"
                    )
                }
                data.battery?.let {
                    CircularProgress(
                        progress = it.toFloat() / 100f,
                        size = 90,
                        color = if (it < 20 && data.charging == false) NeonRed else NeonGreen,
                        label = "电量",
                        value = "$it%"
                    )
                }
            }
            // 充电状态显示
            data.battery?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (data.charging == true) NeonYellow.copy(alpha = 0.15f) else TextMuted.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 10.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (data.charging == true) Icons.Default.Bolt else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (data.charging == true) NeonYellow else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (data.charging == true) "正在充电 ⚡" else "未充电",
                        color = if (data.charging == true) NeonYellow else TextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // CPU 详情
            data.cpuModel?.let {
                DataItem("CPU 型号", it)
                Spacer(modifier = Modifier.height(4.dp))
            }
            DataItem("CPU Cores", "${data.cpuCores ?: '?'}")
            Spacer(modifier = Modifier.height(4.dp))
            DataItem(
                "CPU 温度",
                if (data.cpuTemp != null && data.cpuTemp > 0) "${data.cpuTemp.toInt()}°C" else "不支持",
                valueColor = if ((data.cpuTemp ?: 0.0) > 70) NeonRed else TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            DataItem(
                "内存使用",
                "${(data.memoryUsed ?: 0).formatBytes()} / ${(data.memoryTotal ?: 0).formatBytes()}"
            )
            Spacer(modifier = Modifier.height(4.dp))
            DataItem(
                "内存可用",
                (data.memoryAvailable ?: 0).formatBytes()
            )
        }
    }
}
