package com.devicemonitor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devicemonitor.app.components.*
import com.devicemonitor.app.data.*
import com.devicemonitor.app.ui.theme.*
import com.devicemonitor.app.ui.components.DeviceCard
import com.devicemonitor.app.ui.components.SwipeToReveal
import com.devicemonitor.app.ui.components.MiniStat
import com.devicemonitor.app.ui.components.getDeviceIcon
import com.devicemonitor.app.ui.components.SystemOverviewCard
import com.devicemonitor.app.ui.components.StatItem
import com.devicemonitor.app.ui.viewmodel.DeviceListViewModel
import com.devicemonitor.app.util.Constants
import com.devicemonitor.app.util.EncryptedPrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun DeviceListScreen(
    onDeviceClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val viewModel: DeviceListViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val devices = uiState.devices
    val systemStatus = uiState.systemStatus
    val isLoading = uiState.isLoading
    val isRefreshing = uiState.isRefreshing
    val errorMessage = uiState.errorMessage
    val alerts = uiState.alerts
    val isLoadingAlerts = uiState.isLoadingAlerts
    var showDetailDialog by remember { mutableStateOf(false) }
    var detailType by remember { mutableStateOf("") }
    var isDeletingAlert by remember { mutableStateOf(false) }
    var alertToDelete by remember { mutableStateOf<Alert?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showDeleteDeviceConfirm by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<Device?>(null) }
    var swipedDeviceId by remember { mutableStateOf<String?>(null) }
    var swipedAlertId by remember { mutableStateOf<Int?>(null) }
    var deleteCodeInput by remember { mutableStateOf("") }
    var deleteCodeError by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val encryptedPrefs = remember { EncryptedPrefsHelper(context) }

    // 删除单条告警（委托给ViewModel）
    fun deleteAlert(alertId: Int) {
        viewModel.deleteAlert(alertId)
    }

    // 删除全部告警（委托给ViewModel）
    fun deleteAllAlerts() {
        viewModel.deleteAllAlerts()
    }

    // 删除设备（委托给ViewModel）
    fun deleteDevice(deviceId: String) {
        viewModel.deleteDevice(
            deviceId = deviceId,
            onSuccess = { swipedDeviceId = null },
            onError = { }
        )
    }
    Scaffold(
        containerColor = CyberBlack,
        topBar = {
            DeviceListTopBar(
                onlineCount = systemStatus?.onlineDevices ?: 0,
                onSettingsClick = onSettingsClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.refresh() },
                containerColor = NeonCyan,
                contentColor = CyberBlack,
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = CyberBlack,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CyberBlack)
        ) {
            when {
                isLoading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(4) { SkeletonDeviceCard() }
                    }
                }
                devices.isEmpty() && errorMessage == null -> {
                    EmptyState(
                        icon = Icons.Default.Devices,
                        title = "暂无设备",
                        message = "还没有设备连接到服务器",
                        actionLabel = "刷新",
                        onAction = { viewModel.refresh() }
                    )
                }
                errorMessage != null -> {
                    DeviceListErrorState(
                        errorMessage = errorMessage,
                        serverUrl = ApiClient.getBaseUrl(),
                        onRetry = { viewModel.refresh() },
                        onSettingsClick = onSettingsClick
                    )
                }
                devices.isEmpty() -> {
                    DeviceListEmptyState(serverUrl = ApiClient.getBaseUrl())
                }
                else -> {
                    val pullRefreshState = rememberPullRefreshState(
                        refreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() }
                    )
                    Box(Modifier.pullRefresh(pullRefreshState)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(contentType = "overview") {
                            systemStatus?.let {
                                SystemOverviewCard(
                                    status = it,
                                    onOnlineClick = {
                                        detailType = "online"
                                        showDetailDialog = true
                                    },
                                    onOfflineClick = {
                                        detailType = "offline"
                                        showDetailDialog = true
                                    },
                                    onTotalClick = {
                                        detailType = "total"
                                        showDetailDialog = true
                                    },
                                    onAlertClick = {
                                        detailType = "alert"
                                        showDetailDialog = true
                                        // 加载告警列表
                                        viewModel.loadAlerts()
                                    }
                                )
                            }
                        }
                        items(devices, key = { it.id }, contentType = { "device" }) { device ->
                            DeviceCard(
                                device = device,
                                onClick = { onDeviceClick(device.id) },
                                onDelete = {
                                    deviceToDelete = device
                                    deleteCodeInput = ""
                                    deleteCodeError = false
                                    showDeleteDeviceConfirm = true
                                },
                                isSwiped = swipedDeviceId == device.id,
                                onSwipeChange = { swiped ->
                                    swipedDeviceId = if (swiped) device.id else null
                                }
                            )
                        }
                    }
                    PullRefreshIndicator(
                        refreshing = isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter),
                        backgroundColor = CyberCard,
                        contentColor = NeonCyan
                    )
                    }
                }
            }

            if (isRefreshing && !isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = NeonCyan,
                    trackColor = CyberDark
                )
            }

            // 详情弹窗
            if (showDetailDialog) {
                val dialogTitle = when (detailType) {
                    "online" -> "在线设备 (" + devices.filter { it.status == "online" }.size + ")"
                    "offline" -> "离线设备 (" + devices.filter { it.status == "offline" }.size + ")"
                    "total" -> "所有设备 (" + devices.size + ")"
                    "alert" -> "告警列表 (" + alerts.size + ")"
                    else -> "详情"
                }
                val filteredDevices = when (detailType) {
                    "online" -> devices.filter { it.status == "online" }
                    "offline" -> devices.filter { it.status == "offline" }
                    else -> devices
                }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDetailDialog = false },
                    containerColor = CyberDark,
                    titleContentColor = NeonCyan,
                    textContentColor = TextWhite,
                    title = {
                        Text(
                            text = dialogTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        if (detailType == "alert") {
                            if (isLoadingAlerts) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = NeonCyan)
                                }
                            } else if (alerts.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无告警", color = TextMuted)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(alerts, key = { it.id }) { alert ->
                                        SwipeToReveal(
                                            revealed = swipedAlertId == alert.id,
                                            onRevealChange = { revealed ->
                                                swipedAlertId = if (revealed) alert.id else null
                                            },
                                            revealWidth = 80.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp)),
                                            actionContent = { progress ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .graphicsLayer {
                                                            alpha = progress
                                                            scaleX = 0.85f + 0.15f * progress
                                                            scaleY = 0.85f + 0.15f * progress
                                                        }
                                                        .background(DarkGray)
                                                        .clickable(enabled = progress > 0.5f) {
                                                            alertToDelete = alert
                                                        }
                                                        .padding(horizontal = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "删除",
                                                            tint = NeonRed,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "删除",
                                                            color = NeonRed,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            // 前景告警内容
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (alert.level == "critical") AlertBgRed else AlertBgYellow,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = alert.type,
                                                        color = if (alert.level == "critical") NeonRed else NeonYellow,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        text = alert.timestamp.formatTime(),
                                                        color = TextMuted,
                                                        fontSize = 10.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = alert.message,
                                                    color = TextWhite,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "设备: ${alert.deviceName ?: alert.deviceId}",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if (filteredDevices.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂无设备", color = TextMuted)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 400.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredDevices, key = { it.id }) { device ->
                                        val data = device.latestData
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(CyberBlack.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (device.status == "online") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                contentDescription = null,
                                                tint = if (device.status == "online") NeonGreen else NeonRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = device.name,
                                                    color = TextWhite,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "${device.type.uppercase()} | " +
                                                        "CPU: ${data?.cpuUsage?.toInt() ?: 0}% | " +
                                                        "内存: ${data?.memoryUsage?.toInt() ?: 0}%",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            data?.battery?.let {
                                                Text(
                                                    text = "$it%",
                                                    color = if (it < 20 && data.charging == false) NeonRed else NeonGreen,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (detailType == "alert" && alerts.isNotEmpty()) {
                                TextButton(
                                    onClick = { showDeleteAllConfirm = true },
                                    enabled = !isDeletingAlert
                                ) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        null,
                                        modifier = Modifier.size(18.dp),
                                        tint = NeonCyan
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("全部清除", color = NeonCyan)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }
                            TextButton(onClick = { showDetailDialog = false }) {
                                Text("关闭", color = NeonCyan)
                            }
                        }
                    }
                )
            }

            // 单条告警删除确认
            if (alertToDelete != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { alertToDelete = null },
                    containerColor = CyberDark,
                    titleContentColor = NeonCyan,
                    textContentColor = TextWhite,
                    title = { Text("删除告警", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column {
                            Text("确定要删除这条告警吗？", color = TextWhite, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = alertToDelete!!.message,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "设备: ${alertToDelete!!.deviceName ?: alertToDelete!!.deviceId}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteAlert(alertToDelete!!.id)
                            alertToDelete = null
                        }) {
                            Text("删除", color = NeonRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { alertToDelete = null }) {
                            Text("取消", color = NeonCyan)
                        }
                    }
                )
            }

            // 全部清除确认
            if (showDeleteAllConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDeleteAllConfirm = false },
                    containerColor = CyberDark,
                    titleContentColor = NeonCyan,
                    textContentColor = TextWhite,
                    title = { Text("清除全部告警", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Text(
                            "确定要清除全部 ${alerts.size} 条告警吗？此操作不可恢复。",
                            color = TextWhite,
                            fontSize = 14.sp
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteAllAlerts()
                            showDeleteAllConfirm = false
                        }) {
                            Text("全部清除", color = NeonRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAllConfirm = false }) {
                            Text("取消", color = NeonCyan)
                        }
                    }
                )
            }

            // 删除设备确认对话框（需验证码）
            if (showDeleteDeviceConfirm && deviceToDelete != null) {
                DeleteDeviceDialog(
                    deviceName = deviceToDelete!!.name,
                    correctCode = encryptedPrefs.getDeleteCode(),
                    onConfirm = {
                        deleteDevice(deviceToDelete!!.id)
                        showDeleteDeviceConfirm = false
                        deviceToDelete = null
                    },
                    onDismiss = {
                        showDeleteDeviceConfirm = false
                        deviceToDelete = null
                    }
                )
            }
        }
    }
}


// ========== 提取的子组件 ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceListTopBar(
    onlineCount: Int,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowText(
                    text = "DEVICE MONITOR",
                    color = NeonCyan,
                    fontSize = 18,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusIndicator(isOnline = onlineCount > 0)
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
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


@Composable
private fun DeviceListErrorState(
    errorMessage: String?,
    serverUrl: String,
    onRetry: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = NeonRed,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "连接失败",
                color = NeonRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage ?: "未知错误",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "服务器: $serverUrl",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = CyberBlack
                )
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("重试", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onSettingsClick) {
                Text("检查服务器设置", color = NeonCyan)
            }
        }
    }
}


@Composable
private fun DeviceListEmptyState(serverUrl: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("暂无设备", color = TextSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("请先在设备上安装 Agent", color = TextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "服务器: $serverUrl",
                color = TextMuted,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}


@Composable
private fun DeleteDeviceDialog(
    deviceName: String,
    correctCode: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberDark,
        titleContentColor = NeonRed,
        textContentColor = TextWhite,
        title = {
            Text(
                text = "删除设备",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "确定要删除设备「$deviceName」吗？",
                    color = TextWhite,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "删除后该设备的所有历史数据和告警记录将被永久删除，此操作不可恢复。",
                    color = NeonRed,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it
                        codeError = false
                    },
                    label = { Text("请输入验证码", color = TextSecondary) },
                    placeholder = { Text("输入验证码以确认删除", color = TextMuted) },
                    isError = codeError,
                    supportingText = {
                        if (codeError) {
                            Text("验证码错误，请重新输入", color = NeonRed, fontSize = 11.sp)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = if (codeError) NeonRed else NeonCyan,
                        unfocusedBorderColor = if (codeError) NeonRed else CyberBorder,
                        cursorColor = NeonCyan,
                        errorBorderColor = NeonRed,
                        errorSupportingTextColor = NeonRed
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (codeInput == correctCode) {
                        onConfirm()
                    } else {
                        codeError = true
                    }
                }
            ) {
                Text("确认删除", color = NeonRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = NeonCyan)
            }
        }
    )
}
