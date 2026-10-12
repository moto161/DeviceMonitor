package com.devicemonitor.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devicemonitor.app.ui.theme.*

// 发光卡片
@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    glowColor: Color = NeonCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CyberCard)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(glowColor.copy(alpha = 0.6f), glowColor.copy(alpha = 0.2f))
                ),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(content = content)
    }
}

// 圆形进度条（科幻风格）
@Composable
fun CircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Int = 80,
    strokeWidth: Float = 8f,
    color: Color = NeonCyan,
    trackColor: Color = CyberBorder,
    label: String = "",
    value: String = ""
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size.dp)) {
            // 背景圆环
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
            // 进度圆环（发光效果）
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        color.copy(alpha = 0.3f),
                        color,
                        color.copy(alpha = 0.8f)
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth)
            )
            // 端点发光
            val angle = -90f + 360f * animatedProgress
            val radius = (size.dp.toPx() - strokeWidth) / 2
            val centerX = size.dp.toPx() / 2
            val centerY = size.dp.toPx() / 2
            val endX = centerX + radius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
            val endY = centerY + radius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
            drawCircle(
                color = color,
                radius = strokeWidth / 2 + 2,
                center = Offset(endX, endY)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    color = TextWhite,
                    fontSize = (size / 4).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = (size / 8).sp
                )
            }
        }
    }
}

// 霓虹文字
@Composable
fun GlowText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan,
    fontSize: Int = 16,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                spotColor = color.copy(alpha = 0.5f),
                ambientColor = color.copy(alpha = 0.3f)
            )
    )
}

// 状态指示器（在线/离线脉冲）
@Composable
fun StatusIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isOnline) NeonGreen else NeonRed

    // 只有在线才起脉冲；离线设备完全不参与动画计算
    val pulseAlpha by if (isOnline) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                // 阴影用固定颜色（不再每帧变化 → 不触发阴影重绘），
                // 脉冲只改合成层 alpha，成本降到渲染线程的一次属性写入
                .graphicsLayer { alpha = if (isOnline) pulseAlpha else 0.6f }
                .shadow(
                    elevation = if (isOnline) 8.dp else 0.dp,
                    spotColor = color,
                    ambientColor = color
                )
                .background(color = color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isOnline) "ONLINE" else "OFFLINE",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

// 数据项（标签+值）
@Composable
fun DataItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

// 进度条（水平）
@Composable
fun HorizontalProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan,
    height: Int = 6
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "hProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            // 圆角交给外层裁切，内部填充条保持矩形，
            // 这样缩放时圆角不会被拉伸变形
            .clip(RoundedCornerShape(height.dp))
            .background(CyberBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                // 走合成层缩放：不再每帧改变宽度，避免 measure + layout 全链路重算
                .graphicsLayer {
                    scaleX = animatedProgress
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.6f), color)
                    )
                )
        )
    }
}

/**
 * 骨架屏卡片 - 加载时占位
 */
@Composable
fun SkeletonDeviceCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            // 缓入缓出替代线性，呼吸更柔和、无机械感
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    CyberCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标占位
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberBorder.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(16.dp))
            // 文字占位
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberBorder.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberBorder.copy(alpha = alpha))
                )
            }
            // 状态占位
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CyberBorder.copy(alpha = alpha))
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // 指标占位
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(CyberBorder.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CyberBorder.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

/**
 * 空状态组件 - 无设备时显示
 */
@Composable
fun EmptyState(
    icon: ImageVector = Icons.Default.Devices,
    title: String = "暂无设备",
    message: String = "还没有设备连接到服务器",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = CyberBlack
                )
            ) {
                Text(actionLabel)
            }
        }
    }
}
