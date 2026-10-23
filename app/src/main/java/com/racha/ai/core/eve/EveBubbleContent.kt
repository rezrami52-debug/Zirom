package com.racha.ai.core.eve

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun EveBubbleContent(
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    onClick: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffsetY by animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = tween(durationMillis = 200),
        label = "dragOffset"
    )

    var isClicked by remember { mutableStateOf(false) }
    val clickPulse by animateFloatAsState(
        targetValue = if (isClicked) 1.25f else 1.0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "clickPulse"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorTransitions")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProcessing) 2000 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProcessing) 1500 else 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(
        modifier = modifier
            .size(width = 90.dp, height = 130.dp)
            .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0 || dragOffsetY > 0) {
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        if (dragOffsetY > 160f) {
                            onDismiss()
                        } else {
                            dragOffsetY = 0f
                        }
                    },
                    onDragCancel = { dragOffsetY = 0f }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        coroutineScope.launch {
                            isClicked = true
                            delay(200)
                            isClicked = false
                            onClick()
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val reactorCenter = Offset(x = canvasWidth / 2f, y = canvasWidth / 2f)
            val maxRadius = (canvasWidth / 2f) * 0.85f * clickPulse

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x5500E5FF),
                        Color(0x2200E5FF),
                        Color.Transparent
                    ),
                    center = reactorCenter,
                    radius = maxRadius * 1.35f * glowPulse
                ),
                radius = maxRadius * 1.35f * glowPulse,
                center = reactorCenter
            )

            drawCircle(
                color = Color(0xFF003050),
                radius = maxRadius,
                center = reactorCenter,
                style = Stroke(width = 4f)
            )

            rotate(degrees = outerRotation, pivot = reactorCenter) {
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = maxRadius * 0.92f,
                    center = reactorCenter,
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f, 10f, 15f), 0f)
                    )
                )
            }

            val coilCount = 10
            val coilRingRadius = maxRadius * 0.76f
            for (i in 0 until coilCount) {
                val angleDeg = (i * (360f / coilCount)) + outerRotation * 0.5f
                val angleRad = (angleDeg * PI / 180f).toFloat()
                val coilCenter = Offset(
                    x = reactorCenter.x + coilRingRadius * cos(angleRad),
                    y = reactorCenter.y + coilRingRadius * sin(angleRad)
                )

                drawCircle(
                    color = Color(0xFF00B0FF),
                    radius = 5.5f * clickPulse,
                    center = coilCenter
                )
                drawCircle(
                    color = Color(0xFFE0FFFF),
                    radius = 2.5f,
                    center = coilCenter
                )
            }

            rotate(degrees = innerRotation, pivot = reactorCenter) {
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = maxRadius * 0.56f,
                    center = reactorCenter,
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f),
                        cap = StrokeCap.Round
                    )
                )

                drawTechTriad(reactorCenter, maxRadius * 0.40f)
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFF80DEEA),
                        Color(0xFF00E5FF),
                        Color(0x0000E5FF)
                    ),
                    center = reactorCenter,
                    radius = maxRadius * 0.38f * glowPulse
                ),
                radius = maxRadius * 0.38f * glowPulse,
                center = reactorCenter
            )

            drawSwipeIndicator(
                startX = canvasWidth * 0.35f,
                endX = canvasWidth * 0.65f,
                y = size.height - 18.dp.toPx()
            )
        }
    }
}

private fun DrawScope.drawTechTriad(center: Offset, radius: Float) {
    for (i in 0..2) {
        val angleRad = (i * 120f * PI / 180f).toFloat()
        val endPoint = Offset(
            x = center.x + radius * cos(angleRad),
            y = center.y + radius * sin(angleRad)
        )
        drawLine(
            color = Color(0xAA00E5FF),
            start = center,
            end = endPoint,
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSwipeIndicator(startX: Float, endX: Float, y: Float) {
    drawLine(
        color = Color(0x8800E5FF),
        start = Offset(startX, y),
        end = Offset(endX, y),
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    drawLine(
        color = Color(0xCC00E5FF),
        start = Offset(startX + (endX - startX) * 0.3f, y + 6f),
        end = Offset((startX + endX) / 2f, y + 10f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xCC00E5FF),
        start = Offset((startX + endX) / 2f, y + 10f),
        end = Offset(endX - (endX - startX) * 0.3f, y + 6f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}
