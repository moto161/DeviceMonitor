package com.devicemonitor.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devicemonitor.app.components.HorizontalProgressBar
import com.devicemonitor.app.data.DiskInfo
import com.devicemonitor.app.data.HistoryPoint
import com.devicemonitor.app.data.NetworkInterfaceInfo
import com.devicemonitor.app.data.formatBytes
import com.devicemonitor.app.ui.theme.NeonBlue
import com.devicemonitor.app.ui.theme.NeonCyan
import com.devicemonitor.app.ui.theme.ChartGridColor
import com.devicemonitor.app.ui.theme.NeonGreen
import com.devicemonitor.app.ui.theme.NeonPurple
import com.devicemonitor.app.ui.theme.NeonRed
import com.devicemonitor.app.ui.theme.NeonYellow
import com.devicemonitor.app.ui.theme.TextMuted
import com.devicemonitor.app.ui.theme.TextPrimary
import com.devicemonitor.app.ui.theme.TextSecondary
import com.devicemonitor.app.util.Constants
import java.util.Locale

/**
 * 网络接口项
 */
@Composable
fun NetworkInterfaceItem(iface: NetworkInterfaceInfo, clipboardManager: ClipboardManager) {
    val isUp = iface.state == "up"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isUp) NeonBlue.copy(alpha = 0.08f) else TextMuted.copy(alpha = 0.05f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = if (isUp) NeonBlue else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = iface.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Text(
                text = iface.state?.uppercase() ?: "UNKNOWN",
                color = if (isUp) NeonGreen else TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        iface.ip?.let {
            if (it.isNotEmpty()) {
                Text(
                    text = "IP: $it",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(it))
                    }
                )
            }
        }
        iface.mac?.let {
            if (it.isNotEmpty() && it != "00:00:00:00:00:00") {
                Text(
                    text = "MAC: $it",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
        iface.speed?.let {
            if (it.isNotEmpty() && it != "-1") {
                Text(
                    text = "速度: $it Mbps",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * 负载项
 */
@Composable
fun LoadItem(label: String, value: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format(Locale.US, "%.2f", value),
            color = NeonPurple,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

/**
 * 磁盘项
 */
@Composable
fun DiskItem(disk: DiskInfo) {
    val color = when {
        disk.usage > 90 -> NeonRed
        disk.usage > 75 -> NeonYellow
        else -> NeonBlue
    }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = disk.mount,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${disk.usage.toInt()}%",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalProgressBar(
            progress = disk.usage.toFloat() / 100f,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${disk.used.formatBytes()} / ${disk.total.formatBytes()}",
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

/**
 * 图例句柄
 */
@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

/**
 * 历史图表
 */
@Composable
fun HistoryChart(history: List<HistoryPoint>) {
    val points = history.takeLast(Constants.HISTORY_CHART_POINTS)
    if (points.size < 2) {
        Text("历史数据不足", color = TextMuted, fontSize = 12.sp)
        return
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height
        val padBottom = 16f
        val padTop = 8f
        val chartHeight = height - padBottom - padTop

        // 网格线
        val gridColor = ChartGridColor
        for (i in 0..4) {
            val y = padTop + chartHeight * i / 4
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // 绘制折线
        fun drawTrendLine(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = width * index / (values.size - 1)
                val y = padTop + chartHeight * (1 - value / 100f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2f))
        }

        val cpuValues = points.map { (it.data.cpuUsage ?: 0.0).toFloat() }
        val memValues = points.map { (it.data.memoryUsage ?: 0.0).toFloat() }
        drawTrendLine(cpuValues, NeonCyan)
        drawTrendLine(memValues, NeonGreen)
    }
}
