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
import kotlinx.coroutines.launch

@Composable
fun EveBubbleContent(
    uiState: EveUiState,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AlertPulse")
    val alertPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val eyeOpenRatio = remember { Animatable(0.15f) }
    var clickCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.mood) {
        when (uiState.mood) {
            EveMood.AWAKE_SMILING -> {
                eyeOpenRatio.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            EveMood.NOTIFICATION_ALERT -> {
                eyeOpenRatio.animateTo(1f, animationSpec = tween(300))
            }
            EveMood.SLEEPING -> {
                eyeOpenRatio.animateTo(0.15f, animationSpec = tween(600))
            }
            else -> {}
        }
    }

    // 1. الفقاعة أطول (145.dp بدلاً من 110.dp)
    Box(
        modifier = Modifier
            .width(115.dp)
            .height(145.dp)
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = {
                        // إذا كان السحب للأسفل أكثر من 150 بكسل، أغلق الخدمة
                        if (totalDragY > 150f) {
                            onCloseClick()
                        }
                        totalDragY = 0f
                    }
                )
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

        // 2. جسم EVE (90.dp عرض × 130.dp ارتفاع)
        Canvas(
            modifier = Modifier
                .size(90.dp, 130.dp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { !it.pressed }) {
                                if (clickCount == 0) {
                                    EveState.setMood(EveMood.AWAKE_SMILING)
                                    // تشغيل صوت الترحيب
                                    (onClick as? (() -> Unit))?.let { /* لا شيء */ }
                                    // استدعاء دالة speakWelcome عبر EveState
                                    EveState.setMood(EveMood.AWAKE_SMILING)
                                    clickCount = 1
                                    scope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        EveState.setMood(EveMood.SLEEPING)
                                        clickCount = 0
                                    }
                                } else {
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
                    uiState.config.bodyColor.copy(alpha = 0.90f),
                    Color(0xFFD2D9E0)
                ),
                center = Offset(width * 0.4f, height * 0.28f),
                radius = width * 0.9f
            )

            drawOval(
                brush = bodyBrush,
                topLeft = Offset(0f, 0f),
                size = Size(width, height),
                alpha = uiState.config.backgroundAlpha
            )

            drawOval(
                color = Color.White.copy(alpha = 0.7f),
                topLeft = Offset(1.5f, 1.5f),
                size = Size(width - 3f, height - 3f),
                style = Stroke(width = 2.dp.toPx())
            )

            // 3. الشاشة السوداء والعيون (بحجم ثابت)
            val faceWidth = 62.dp.toPx()
            val faceHeight = 44.dp.toPx()
            val faceTop = 18.dp.toPx()
            val faceLeft = (width - faceWidth) / 2f

            drawOval(
                color = uiState.config.faceScreenColor,
                topLeft = Offset(faceLeft, faceTop),
                size = Size(faceWidth, faceHeight)
            )

            val currentEyeHeight = 16.dp.toPx() * eyeOpenRatio.value.coerceAtLeast(0.12f)
            val eyeWidth = 18.dp.toPx()
            val eyeY = faceTop + (faceHeight * 0.36f)

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(uiState.config.eyeColor, uiState.config.eyeGlowColor)
                ),
                topLeft = Offset(faceLeft + faceWidth * 0.16f, eyeY),
                size = Size(eyeWidth, currentEyeHeight)
            )

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(uiState.config.eyeColor, uiState.config.eyeGlowColor)
                ),
                topLeft = Offset(faceLeft + faceWidth * 0.55f, eyeY),
                size = Size(eyeWidth, currentEyeHeight)
            )

            if (eyeOpenRatio.value > 0.6f) {
                val smilePath = Path().apply {
                    val startX = faceLeft + faceWidth * 0.35f
                    val endX = faceLeft + faceWidth * 0.65f
                    val startY = faceTop + faceHeight * 0.74f
                    moveTo(startX, startY)
                    quadraticBezierTo(
                        (startX + endX) / 2f,
                        startY + (7.dp.toPx() * eyeOpenRatio.value),
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


    }
}
