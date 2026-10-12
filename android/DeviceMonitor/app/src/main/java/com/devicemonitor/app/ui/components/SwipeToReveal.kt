package com.devicemonitor.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 通用「左滑显露操作区」容器（设备卡片 / 告警条目共用）
 *
 * 手感设计（针对「不流畅、不自然」逐项修正）：
 * 1. 拖拽期间只写状态、不逐帧 launch 协程 —— 消除每帧协程调度导致的掉帧与迟滞
 * 2. 松手用 spring 物理曲线收尾 —— 替代 tween(200) 的线性硬切
 * 3. 越界橡皮筋阻尼 —— 滑到尽头不再「撞墙」
 * 4. 速度感知（fling）—— 快速左滑即使未过半也展开，快速右滑立刻收起
 * 5. 操作区随进度渐进显现（alpha + scale）—— 不再瞬间弹出
 */
@Composable
fun SwipeToReveal(
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    revealWidth: Dp = 100.dp,
    enabled: Boolean = true,
    actionContent: @Composable (progress: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { revealWidth.toPx() }.coerceAtLeast(1f)
    val scope = rememberCoroutineScope()

    // settled = 收尾动画的权威值；拖拽期间由 dragOffset 接管显示
    val settled = remember { Animatable(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var lastTime by remember { mutableLongStateOf(0L) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val settleSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // 展开判定：位移过 40% 宽度，或向左 fling 速度超过阈值
    val positionThreshold = revealWidthPx * 0.4f
    val flingVelocity = with(density) { 700.dp.toPx() }

    // 外部状态变化（例如滑动了另一张卡片）时同步展开/收起；手势自身收尾时不重复动画
    LaunchedEffect(revealed) {
        val target = if (revealed) -revealWidthPx else 0f
        if (settleJob?.isActive == true) return@LaunchedEffect
        if (abs(settled.value - target) > 1f) {
            settleJob = launch { settled.animateTo(target, settleSpec) }
            dragOffset = target
        }
    }

    val offsetX = if (dragging) dragOffset else settled.value
    val progress = (-offsetX / revealWidthPx).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        // 操作区：位于内容下层，随滑动进度渐显
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            actionContent(progress)
        }

        // 内容层：跟手滑动
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .then(
                    if (enabled) {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    settleJob?.cancel()
                                    settleJob = null
                                    velocity = 0f
                                    lastTime = 0L
                                    dragging = true
                                    dragOffset = settled.value
                                },
                                onDragCancel = {
                                    dragging = false
                                    settleJob = scope.launch {
                                        settled.snapTo(dragOffset)
                                        settled.animateTo(
                                            if (revealed) -revealWidthPx else 0f,
                                            settleSpec
                                        )
                                    }
                                },
                                onDragEnd = {
                                    dragging = false
                                    val shouldReveal =
                                        dragOffset < -positionThreshold || velocity < -flingVelocity
                                    settleJob = scope.launch {
                                        settled.snapTo(dragOffset)
                                        settled.animateTo(
                                            if (shouldReveal) -revealWidthPx else 0f,
                                            settleSpec
                                        )
                                    }
                                    onRevealChange(shouldReveal)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    val now = change.uptimeMillis
                                    val dt = (now - lastTime)
                                    if (lastTime != 0L && dt > 0) {
                                        // 指数平滑速度（px/s），向左为负
                                        velocity = velocity * 0.6f + (dragAmount / dt * 1000f) * 0.4f
                                    }
                                    lastTime = now
                                    dragOffset = rubberBand(
                                        value = dragOffset + dragAmount,
                                        min = -revealWidthPx,
                                        max = 0f
                                    )
                                    change.consume()
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}

/**
 * 越界阻尼：超出边界的部分按渐近曲线衰减，最多额外露出约 40% 宽度，
 * 模拟橡皮筋手感，避免硬 clamp 造成「撞墙」感。
 */
private fun rubberBand(value: Float, min: Float, max: Float): Float {
    val range = max - min
    return when {
        value < min -> min - decay(min - value, range)
        value > max -> max + decay(value - max, range)
        else -> value
    }
}

private fun decay(overflow: Float, range: Float): Float {
    return (overflow / (1f + overflow / range)) * 0.4f
}
