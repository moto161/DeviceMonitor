package com.devicemonitor.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devicemonitor.app.components.CyberCard
import com.devicemonitor.app.components.GlowText
import com.devicemonitor.app.data.SystemStatus
import com.devicemonitor.app.data.formatTime
import com.devicemonitor.app.ui.theme.NeonCyan
import com.devicemonitor.app.ui.theme.NeonGreen
import com.devicemonitor.app.ui.theme.NeonPurple
import com.devicemonitor.app.ui.theme.NeonRed
import com.devicemonitor.app.ui.theme.NeonYellow
import com.devicemonitor.app.ui.theme.TextMuted

/**
 * 系统概览卡片
 */
@Composable
fun SystemOverviewCard(
    status: SystemStatus,
    onOnlineClick: () -> Unit = {},
    onOfflineClick: () -> Unit = {},
    onTotalClick: () -> Unit = {},
    onAlertClick: () -> Unit = {}
) {
    CyberCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = NeonPurple
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlowText(
                    text = "SYSTEM OVERVIEW",
                    color = NeonPurple,
                    fontSize = 12,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = status.serverTime.formatTime(),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(
                    value = "${status.onlineDevices}",
                    label = "在线",
                    color = NeonGreen,
                    icon = Icons.Default.CheckCircle,
                    onClick = onOnlineClick
                )
                StatItem(
                    value = "${status.offlineDevices}",
                    label = "离线",
                    color = NeonRed,
                    icon = Icons.Default.Cancel,
                    onClick = onOfflineClick
                )
                StatItem(
                    value = "${status.totalDevices}",
                    label = "总数",
                    color = NeonCyan,
                    icon = Icons.Default.Devices,
                    onClick = onTotalClick
                )
                StatItem(
                    value = "${status.unacknowledgedAlerts}",
                    label = "告警",
                    color = if (status.unacknowledgedAlerts > 0) NeonYellow else TextMuted,
                    icon = Icons.Default.Warning,
                    onClick = onAlertClick
                )
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
fun StatItem(
    value: String,
    label: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = label,
            color = com.devicemonitor.app.ui.theme.TextSecondary,
            fontSize = 10.sp
        )
    }
}
