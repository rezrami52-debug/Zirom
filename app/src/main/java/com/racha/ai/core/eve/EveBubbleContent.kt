package com.racha.ai.core.eve

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun EveBubbleContent(
    uiState: EveUiState,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AlertPulse")
    val alertPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "PulseScale"
    )

    val eyeOpenRatio = remember { Animatable(0f) }
    var clickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.mood) {
        when (uiState.mood) {
            EveMood.AWAKE_SMILING -> eyeOpenRatio.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            EveMood.NOTIFICATION_ALERT -> eyeOpenRatio.animateTo(1f, tween(300))
            EveMood.SLEEPING -> eyeOpenRatio.animateTo(0.15f, tween(600))
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .size(110.dp, 130.dp) // ✅ جعل الفقاعة أطول قليلاً
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (uiState.mood == EveMood.NOTIFICATION_ALERT) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = (size.minDimension / 2f) * alertPulseScale
                drawCircle(
                    color = uiState.config.alertRingColor.copy(alpha = 0.4f),
                    radius = radius,
                    style = Stroke(width = 6.dp.toPx())
                )
            }
        }

        Canvas(
            modifier = Modifier
                .size(90.dp, 115.dp) // ✅ جعل الجسم أطول قليلاً
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { !it.pressed }) {
                                if (clickCount == 0) {
                                    // النقرة الأولى: فتح العيون (فقط)
                                    EveState.setMood(EveMood.AWAKE_SMILING)
                                    clickCount = 1
                                    // العودة للنوم بعد 2 ثانية
                                    launch {
                                        kotlinx.coroutines.delay(2000)
                                        EveState.setMood(EveMood.SLEEPING)
                                        clickCount = 0
                                    }
                                } else {
                                    // النقرة الثانية: فتح التطبيق
                                    onClick()
                                    clickCount = 0
                                }
                            }
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            val bodyBrush = Brush.radialGradient(
                colors = listOf(
                    uiState.config.bodyColor,
                    uiState.config.bodyColor.copy(alpha = 0.85f),
                    Color(0xFFD0D7DE)
                ),
                center = Offset(width * 0.4f, height * 0.35f),
                radius = width * 0.8f
            )

            drawOval(
                brush = bodyBrush,
                topLeft = Offset(0f, 0f),
                size = Size(width, height),
                alpha = uiState.config.backgroundAlpha
            )

            drawOval(
                color = Color.White.copy(alpha = 0.6f),
                topLeft = Offset(1.5f, 1.5f),
                size = Size(width - 3f, height - 3f),
                style = Stroke(width = 2.dp.toPx())
            )

            val faceWidth = width * 0.72f
            val faceHeight = height * 0.46f
            val faceTop = height * 0.18f
            val faceLeft = (width - faceWidth) / 2f

            drawOval(
                color = uiState.config.faceScreenColor,
                topLeft = Offset(faceLeft, faceTop),
                size = Size(faceWidth, faceHeight)
            )

            val currentEyeHeight = 16.dp.toPx() * eyeOpenRatio.value.coerceAtLeast(0.12f)
            val eyeWidth = 18.dp.toPx()
            val eyeY = faceTop + (faceHeight * 0.35f)

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(uiState.config.eyeColor, uiState.config.eyeGlowColor)
                ),
                topLeft = Offset(faceLeft + faceWidth * 0.18f, eyeY),
                size = Size(eyeWidth, currentEyeHeight)
            )

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(uiState.config.eyeColor, uiState.config.eyeGlowColor)
                ),
                topLeft = Offset(faceLeft + faceWidth * 0.54f, eyeY),
                size = Size(eyeWidth, currentEyeHeight)
            )

            if (eyeOpenRatio.value > 0.6f) {
                val smilePath = Path().apply {
                    val startX = faceLeft + faceWidth * 0.35f
                    val endX = faceLeft + faceWidth * 0.65f
                    val startY = faceTop + faceHeight * 0.72f
                    moveTo(startX, startY)
                    quadraticBezierTo(
                        (startX + endX) / 2f,
                        startY + (8.dp.toPx() * eyeOpenRatio.value),
                        endX, startY
                    )
                }
                drawPath(
                    path = smilePath,
                    color = uiState.config.eyeColor,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .size(20.dp) // ✅ تصغير زر الإغلاق
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Bubble",
                tint = Color.White,
                modifier = Modifier.size(12.dp) // ✅ تصغير الأيقونة
            )
        }
    }
}
