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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun EveBubbleContent(
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    onClick: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()

    // 1. حركة الطفو الدائمة (Anti-gravity floating)
    val infiniteTransition = rememberInfiniteTransition(label = "EveHover")
    val hoverY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hoverY"
    )

    // 2. توهج العيون النيون
    val eyeGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isProcessing) 500 else 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeGlow"
    )

    // 3. محاكاة الرمش الطبيعي للعيون (Blinking)
    var isBlinking by remember { mutableStateOf(false) }
    var isHappy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay((3000..5000).random().toLong())
            isBlinking = true
            delay(150)
            isBlinking = false
        }
    }

    val eyeHeightScale by animateFloatAsState(
        targetValue = if (isBlinking) 0.1f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "eyeBlink"
    )

    Box(
        modifier = modifier
            .size(width = 80.dp, height = 105.dp)
            .offset { IntOffset(0, hoverY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        coroutineScope.launch {
                            isHappy = true
                            delay(600)
                            isHappy = false
                            onClick()
                        }
                    },
                    onLongPress = {
                        onDismiss() // الضغط المطول يغلق الفقاعة
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // --- أ) رسم جسم EVE الكبسولي الأبيض اللامع (Glossy White Shell) ---
            val bodyBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF3F6FA),
                    Color(0xFFDCE4ED)
                )
            )

            // ظل مجسم ثلاثي الأبعاد
            drawRoundRect(
                color = Color(0x33000000),
                topLeft = Offset(w * 0.08f, h * 0.05f),
                size = Size(w * 0.84f, h * 0.90f),
                cornerRadius = CornerRadius(w * 0.42f, h * 0.45f)
            )

            // الهيكل الأبيض
            drawRoundRect(
                brush = bodyBrush,
                topLeft = Offset(w * 0.07f, h * 0.03f),
                size = Size(w * 0.86f, h * 0.92f),
                cornerRadius = CornerRadius(w * 0.43f, h * 0.46f)
            )

            // إطار خارجي رفيع وناعم
            drawRoundRect(
                color = Color(0xFFC7D3E0),
                topLeft = Offset(w * 0.07f, h * 0.03f),
                size = Size(w * 0.86f, h * 0.92f),
                cornerRadius = CornerRadius(w * 0.43f, h * 0.46f),
                style = Stroke(width = 1.5f)
            )

            // خط فاصل ناعم بين الرأس والجسم
            val seamPath = Path().apply {
                moveTo(w * 0.12f, h * 0.48f)
                cubicTo(w * 0.35f, h * 0.52f, w * 0.65f, h * 0.52f, w * 0.88f, h * 0.48f)
            }
            drawPath(seamPath, color = Color(0xFFBDCAD8), style = Stroke(width = 1.2f))

            // --- ب) قناع الوجه الأسود الزجاجي (Black Glossy Visor) ---
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

            // لمعان زجاجي أعلى القناع (Glass Reflection)
            val reflectionPath = Path().apply {
                moveTo(visorLeft + 8f, visorTop + 4f)
                lineTo(visorLeft + visorWidth - 8f, visorTop + 4f)
                cubicTo(visorLeft + visorWidth * 0.7f, visorTop + 14f, visorLeft + visorWidth * 0.3f, visorTop + 14f, visorLeft + 8f, visorTop + 4f)
            }
            drawPath(reflectionPath, color = Color.White.copy(alpha = 0.28f))

            // --- ج) العيون النيون الزرقاء التفاعلية (Cyan LED Eyes) ---
            val cyanGlow = Color(0xFF00E5FF)
            val eyeCenterY = visorTop + visorHeight * 0.52f
            val eyeWidth = visorWidth * 0.28f
            val eyeHeight = (visorHeight * 0.30f) * eyeHeightScale
            val leftEyeX = visorLeft + visorWidth * 0.22f
            val rightEyeX = visorLeft + visorWidth * 0.78f

            if (isHappy) {
                // عيون مبتسمة مقوسة (Happy Crescent Eyes)
                val happyLeft = Path().apply {
                    moveTo(leftEyeX - eyeWidth * 0.5f, eyeCenterY + 4f)
                    quadraticBezierTo(leftEyeX, eyeCenterY - 10f, leftEyeX + eyeWidth * 0.5f, eyeCenterY + 4f)
                }
                val happyRight = Path().apply {
                    moveTo(rightEyeX - eyeWidth * 0.5f, eyeCenterY + 4f)
                    quadraticBezierTo(rightEyeX, eyeCenterY - 10f, rightEyeX + eyeWidth * 0.5f, eyeCenterY + 4f)
                }
                drawPath(happyLeft, color = cyanGlow, style = Stroke(width = 5f, cap = StrokeCap.Round))
                drawPath(happyRight, color = cyanGlow, style = Stroke(width = 5f, cap = StrokeCap.Round))
            } else {
                // العيون الطبيعية الكبسولية المتوهجة
                // العين اليسرى
                drawRoundRect(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFE0FFFF), cyanGlow, Color(0xFF0091EA)),
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
                        listOf(Color(0xFFE0FFFF), cyanGlow, Color(0xFF0091EA)),
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
