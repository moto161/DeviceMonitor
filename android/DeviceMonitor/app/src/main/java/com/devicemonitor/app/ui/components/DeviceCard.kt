package com.devicemonitor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TabletMac
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devicemonitor.app.components.CyberCard
import com.devicemonitor.app.components.StatusIndicator
import com.devicemonitor.app.data.Device
import com.devicemonitor.app.data.formatUptime
import com.devicemonitor.app.data.formatTime
import com.devicemonitor.app.ui.theme.NeonCyan
import com.devicemonitor.app.ui.theme.NeonGreen
import com.devicemonitor.app.ui.theme.NeonRed
import com.devicemonitor.app.ui.theme.AlertRed
import com.devicemonitor.app.ui.theme.NeonYellow
import com.devicemonitor.app.ui.theme.TextMuted
import com.devicemonitor.app.ui.theme.TextPrimary
import com.devicemonitor.app.ui.theme.TextSecondary
import com.devicemonitor.app.ui.theme.TextWhite

/**
 * 设备卡片组件
 * 支持左滑删除：弹簧收尾 + 速度感知 + 越界阻尼，删除按钮随滑动进度渐显
 */
@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    isSwiped: Boolean = false,
    onSwipeChange: (Boolean) -> Unit = {}
) {
    val isOnline = remember(device.status) { device.status == "online" }
    val data = device.latestData
    val glowColor = remember(device.status, data?.cpuUsage) {
        when {
            !isOnline -> NeonRed
            (data?.cpuUsage ?: 0.0) > 80 -> NeonYellow
            else -> NeonCyan
        }
    }

    SwipeToReveal(
        revealed = isSwiped,
        onRevealChange = onSwipeChange,
        revealWidth = 100.dp,
        modifier = Modifier.fillMaxWidth(),
        actionContent = { progress ->
            DeleteAction(progress = progress, onClick = onDelete)
        }
    ) {
        CyberCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isSwiped, onClick = onClick),
            glowColor = glowColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = getDeviceIcon(device.type),
                            contentDescription = null,
                            tint = glowColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = device.name,
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = device.type.uppercase(),
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                    StatusIndicator(isOnline = isOnline)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isOnline && data != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        MiniStat(
                            label = "CPU",
                            value = "${data.cpuUsage?.toInt() ?: 0}%",
                            color = if ((data.cpuUsage ?: 0.0) > 80) NeonYellow else NeonCyan
                        )
                        MiniStat(
                            label = "内存",
                            value = "${data.memoryUsage?.toInt() ?: 0}%",
                            color = if ((data.memoryUsage ?: 0.0) > 80) NeonYellow else NeonGreen
                        )
                        MiniStat(
                            label = "温度",
                            value = if (data.cpuTemp != null && data.cpuTemp > 0) "${data.cpuTemp.toInt()}°" else "--",
                            color = if ((data.cpuTemp ?: 0.0) > 70) NeonRed else NeonCyan
                        )
                        data.battery?.let {
                            MiniStat(
                                label = "电量",
                                value = "$it%",
                                color = if (it < 20 && data.charging == false) NeonRed else NeonGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    data.uptime?.let {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "运行时间",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = it.formatUptime(),
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                } else if (!isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "最后在线: ${device.lastSeen.formatTime()}",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左滑露出的删除按钮
 * progress：0（未露出）→ 1（完全露出），用于渐进显现（透明度 + 缩放）
 */
@Composable
private fun DeleteAction(
    progress: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = progress
                scaleX = 0.85f + 0.15f * progress
                scaleY = 0.85f + 0.15f * progress
            }
            .background(
                color = AlertRed,
                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
            )
            .clickable(enabled = progress > 0.5f) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "删除",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 迷你统计项
 */
@Composable
fun MiniStat(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp
        )
    }
}

/**
 * 根据设备类型获取图标
 */
fun getDeviceIcon(type: String): ImageVector {
    return when (type.lowercase()) {
        "server" -> Icons.Default.Toys
        "router" -> Icons.Default.Router
        "phone" -> Icons.Default.Smartphone
        "tablet" -> Icons.Default.TabletMac
        "pc" -> Icons.Default.PhoneAndroid
        else -> Icons.Default.PhoneAndroid
    }
}
