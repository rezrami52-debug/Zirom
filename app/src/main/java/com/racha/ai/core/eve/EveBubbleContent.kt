package com.racha.ai.core.eve

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun EveBubbleContent(
    modifier: Modifier = Modifier,
    onToggleSleepWake: (Boolean) -> Unit = {},
    onOpenApp: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> }
) {
    val eveUiState by EveState.uiState.collectAsState()
    val isSleeping = eveUiState.mood == EveMood.SLEEPING
    val isNotifying = eveUiState.mood == EveMood.NOTIFICATION_ALERT

    // 1. حركة الطفو الانسيابية (أبطأ في النوم لتوفير الطاقة)
    val hoverDuration = if (isSleeping) 2800 else 1600
    val infiniteTransition = rememberInfiniteTransition(label = "EveAnimation")
    val hoverY by infiniteTransition.animateFloat(
        initialValue = if (isSleeping) -2f else -6f,
        targetValue = if (isSleeping) 2f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(hoverDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hoverY"
    )

    // 2. توهج العيون والوميض التنبيهي
    val eyeGlow by infiniteTransition.animateFloat(
        initialValue = if (isSleeping) 0.4f else 0.85f,
        targetValue = if (isSleeping) 0.6f else 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isNotifying) 400 else 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeGlow"
    )

    // 3. محاكاة الرمش الطبيعي (يعمل فقط عندما تكون مستيقظة)
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(isSleeping) {
        if (!isSleeping) {
            while (true) {
                delay((3500..6000).random().toLong())
                isBlinking = true
                delay(140)
                isBlinking = false
            }
        }
    }

    val eyeHeightScale by animateFloatAsState(
        targetValue = if (isSleeping || isBlinking) 0.08f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "eyeScale"
    )

    var totalDragY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    val dismissThreshold = 180f

    Box(
        modifier = modifier
            .size(width = 80.dp, height = 105.dp)
            .offset { IntOffset(0, hoverY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        totalDragY = 0f
                        totalDragX = 0f
                    },
                    onDragEnd = {
                        if (totalDragY > dismissThreshold && totalDragY > kotlin.math.abs(totalDragX) * 1.2f) {
                            onDismiss()
                        }
                        totalDragY = 0f
                        totalDragX = 0f
                    },
                    onDragCancel = {
                        totalDragY = 0f
                        totalDragX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount.y
                        totalDragX += dragAmount.x
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // نقرة واحدة: تبديل النوم واليقظة
                        val isNowAwake = EveState.toggleSleepWake()
                        onToggleSleepWake(isNowAwake)
                    },
                    onDoubleTap = {
                        // نقرتان: فتح التطبيق الرئيسي
                        onOpenApp()
                    },
                    onLongPress = {
                        onDismiss()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // --- أ) جسم EVE الكبسولي الأبيض اللامع ---
            val bodyBrush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF3F6FA), Color(0xFFDCE4ED))
            )

            // ظل مجسم
            drawRoundRect(
                color = Color(0x33000000),
                topLeft = Offset(w * 0.08f, h * 0.05f),
                size = Size(w * 0.84f, h * 0.90f),
                cornerRadius = CornerRadius(w * 0.42f, h * 0.45f)
            )

            // الهيكل الخارجي
            drawRoundRect(
                brush = bodyBrush,
                topLeft = Offset(w * 0.07f, h * 0.03f),
                size = Size(w * 0.86f, h * 0.92f),
                cornerRadius = CornerRadius(w * 0.43f, h * 0.46f)
            )

            // إطار خارجي ناعم
            drawRoundRect(
                color = if (isNotifying) Color(0xFFFF1744) else Color(0xFFC7D3E0),
                topLeft = Offset(w * 0.07f, h * 0.03f),
                size = Size(w * 0.86f, h * 0.92f),
                cornerRadius = CornerRadius(w * 0.43f, h * 0.46f),
                style = Stroke(width = if (isNotifying) 2.5f else 1.5f)
            )

            // خط فاصل بين الرأس والجسم
            val seamPath = Path().apply {
                moveTo(w * 0.12f, h * 0.48f)
                cubicTo(w * 0.35f, h * 0.52f, w * 0.65f, h * 0.52f, w * 0.88f, h * 0.48f)
            }
            drawPath(seamPath, color = Color(0xFFBDCAD8), style = Stroke(width = 1.2f))

            // --- ب) قناع الوجه الأسود الزجاجي (Black Visor) ---
            val visorWidth = w * 0.64f
            val visorHeight = h * 0.32f
            val visorTop = h * 0.13f
            val visorLeft = (w - visorWidth) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF151922), Color(0xFF090B0E), Color(0xFF020305))
                ),
                topLeft = Offset(visorLeft, visorTop),
                size = Size(visorWidth, visorHeight),
                cornerRadius = CornerRadius(visorWidth * 0.42f, visorHeight * 0.50f)
            )

            // لمعان زجاجي أعلى القناع
            val reflectionPath = Path().apply {
                moveTo(visorLeft + 8f, visorTop + 4f)
                lineTo(visorLeft + visorWidth - 8f, visorTop + 4f)
                cubicTo(visorLeft + visorWidth * 0.7f, visorTop + 14f, visorLeft + visorWidth * 0.3f, visorTop + 14f, visorLeft + 8f, visorTop + 4f)
            }
            drawPath(reflectionPath, color = Color.White.copy(alpha = 0.28f))

            // --- ج) العيون التفاعلية (مغمضة في السبات / مفتوحة عند اليقظة) ---
            val eyeGlowColor = if (isNotifying) Color(0xFFFF1744) else Color(0xFF00E5FF)
            val eyeCenterY = visorTop + visorHeight * 0.52f
            val eyeWidth = visorWidth * 0.28f
            val eyeHeight = (visorHeight * 0.30f) * eyeHeightScale
            val leftEyeX = visorLeft + visorWidth * 0.22f
            val rightEyeX = visorLeft + visorWidth * 0.78f

            if (isSleeping) {
                // رسم الأعين المغمضة النائمة بهدوء (Sleep Arcs)
                val sleepLeft = Path().apply {
                    moveTo(leftEyeX - eyeWidth * 0.45f, eyeCenterY - 1f)
                    quadraticBezierTo(leftEyeX, eyeCenterY + 4f, leftEyeX + eyeWidth * 0.45f, eyeCenterY - 1f)
                }
                val sleepRight = Path().apply {
                    moveTo(rightEyeX - eyeWidth * 0.45f, eyeCenterY - 1f)
                    quadraticBezierTo(rightEyeX, eyeCenterY + 4f, rightEyeX + eyeWidth * 0.45f, eyeCenterY - 1f)
                }
                drawPath(sleepLeft, color = eyeGlowColor.copy(alpha = 0.55f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                drawPath(sleepRight, color = eyeGlowColor.copy(alpha = 0.55f), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            } else {
                // رسم الأعين المستيقظة المتوهجة
                // العين اليسرى
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFE0FFFF), eyeGlowColor, Color(0xFF0091EA)),
                        center = Offset(leftEyeX, eyeCenterY),
                        radius = eyeWidth * eyeGlow
                    ),
                    topLeft = Offset(leftEyeX - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeWidth * 0.45f, eyeHeight * 0.45f)
                )

                // العين اليمنى
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFE0FFFF), eyeGlowColor, Color(0xFF0091EA)),
                        center = Offset(rightEyeX, eyeCenterY),
                        radius = eyeWidth * eyeGlow
                    ),
                    topLeft = Offset(rightEyeX - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
                    size = Size(eyeWidth, eyeHeight),
                    cornerRadius = CornerRadius(eyeWidth * 0.45f, eyeHeight * 0.45f)
                )
            }
        }
    }
}
