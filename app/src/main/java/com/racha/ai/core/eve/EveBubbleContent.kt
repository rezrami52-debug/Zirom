package com.racha.ai.core.eve

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racha.ai.core.media.LocalMediaManager
import com.racha.ai.core.media.MediaItemData
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun EveBubbleContent(
    modifier: Modifier = Modifier,
    onToggleSleepWake: (Boolean) -> Unit = {},
    onOpenApp: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onMediaPlayPause: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onMediaPrev: () -> Unit = {},
    onMediaItemSelected: (MediaItemData) -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> }
) {
    val eveUiState by EveState.uiState.collectAsState()
    val isSleeping = eveUiState.mood == EveMood.SLEEPING
    val isNotifying = eveUiState.mood == EveMood.NOTIFICATION_ALERT
    val playback = eveUiState.playback
    val hasActiveMedia = playback.title.isNotBlank()
    val shelfType = eveUiState.shelfType

    val context = LocalContext.current
    val mediaManager = remember { LocalMediaManager(context) }

    // حركة الطفو
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

    // وميض نيون العيون
    val eyeGlow by infiniteTransition.animateFloat(
        initialValue = if (isSleeping) 0.4f else 0.85f,
        targetValue = if (isSleeping) 0.6f else 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isNotifying) 400 else 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeGlow"
    )

    // تحريك شريط الصدر (Marquee Animation)
    val textOffset by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = -60f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chestMarquee"
    )

    // الرمش
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

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        // --- أولاً: جسد EVE وعناصر التحكم السفلية ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .offset { IntOffset(0, hoverY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { totalDragY = 0f; totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragY > dismissThreshold && totalDragY > kotlin.math.abs(totalDragX) * 1.2f) {
                                onDismiss()
                            }
                            totalDragY = 0f; totalDragX = 0f
                        },
                        onDragCancel = { totalDragY = 0f; totalDragX = 0f },
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
                            val isNowAwake = EveState.toggleSleepWake()
                            onToggleSleepWake(isNowAwake)
                        },
                        onDoubleTap = { onOpenApp() },
                        onLongPress = { onDismiss() }
                    )
                }
        ) {
            // كبسولة EVE الرئيسية
            Box(
                modifier = Modifier.size(width = 80.dp, height = 105.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val bodyBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFF3F6FA), Color(0xFFDCE4ED))
                    )

                    drawRoundRect(
                        color = Color(0x33000000),
                        topLeft = Offset(w * 0.08f, h * 0.05f),
                        size = Size(w * 0.84f, h * 0.90f),
                        cornerRadius = CornerRadius(w * 0.42f, h * 0.45f)
                    )

                    drawRoundRect(
                        brush = bodyBrush,
                        topLeft = Offset(w * 0.07f, h * 0.03f),
                        size = Size(w * 0.86f, h * 0.92f),
                        cornerRadius = CornerRadius(w * 0.43f, h * 0.46f)
                    )

                    drawRoundRect(
                        color = if (isNotifying) Color(0xFFFF1744) else Color(0xFFC7D3E0),
                        topLeft = Offset(w * 0.07f, h * 0.03f),
                        size = Size(w * 0.86f, h * 0.92f),
                        cornerRadius = CornerRadius(w * 0.43f, h * 0.46f),
                        style = Stroke(width = if (isNotifying) 2.5f else 1.5f)
                    )

                    // قناع الوجه
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

                    // العيون
                    val eyeGlowColor = if (isNotifying) Color(0xFFFF1744) else Color(0xFF00E5FF)
                    val eyeCenterY = visorTop + visorHeight * 0.52f
                    val eyeWidth = visorWidth * 0.28f
                    val eyeHeight = (visorHeight * 0.30f) * eyeHeightScale
                    val leftEyeX = visorLeft + visorWidth * 0.22f
                    val rightEyeX = visorLeft + visorWidth * 0.78f

                    if (isSleeping) {
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
                        drawRoundRect(
                            brush = Brush.radialGradient(listOf(Color(0xFFE0FFFF), eyeGlowColor, Color(0xFF0091EA)), center = Offset(leftEyeX, eyeCenterY), radius = eyeWidth * eyeGlow),
                            topLeft = Offset(leftEyeX - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
                            size = Size(eyeWidth, eyeHeight),
                            cornerRadius = CornerRadius(eyeWidth * 0.45f, eyeHeight * 0.45f)
                        )
                        drawRoundRect(
                            brush = Brush.radialGradient(listOf(Color(0xFFE0FFFF), eyeGlowColor, Color(0xFF0091EA)), center = Offset(rightEyeX, eyeCenterY), radius = eyeWidth * eyeGlow),
                            topLeft = Offset(rightEyeX - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
                            size = Size(eyeWidth, eyeHeight),
                            cornerRadius = CornerRadius(eyeWidth * 0.45f, eyeHeight * 0.45f)
                        )
                    }
                }

                // --- شريط الصدر النيوني الدائري (Chest Marquee HUD) ---
                if (hasActiveMedia) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .width(62.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF080D1A).copy(alpha = 0.9f))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎵 ${playback.title}",
                            color = Color(0xFF00E5FF),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.offset(x = textOffset.dp)
                        )
                    }
                }
            }

            // --- كبسولة أزرار التحكم السفلية (⏮️ ⏯️ ⏭️) ---
            // تظهر فقط عندما تكون الموسيقى تعمل وتكون EVE مستيقظة
            AnimatedVisibility(
                visible = hasActiveMedia && !isSleeping,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0A0F1D).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onMediaPrev, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        IconButton(onClick = onMediaPlayPause, modifier = Modifier.size(30.dp)) {
                            Icon(
                                imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onMediaNext, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // --- ثانياً: نافذة الهولوجرام الجانبية للوسائط (Holographic Media Shelf) ---
        AnimatedVisibility(
            visible = shelfType != MediaShelfType.NONE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val items = when (shelfType) {
                MediaShelfType.MUSIC -> mediaManager.getLocalAudios()
                MediaShelfType.VIDEOS -> mediaManager.getLocalVideos()
                MediaShelfType.PHOTOS -> mediaManager.getLocalPhotos()
                else -> emptyList()
            }

            Surface(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .width(180.dp)
                    .height(260.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF080D1A).copy(alpha = 0.92f),
                border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (shelfType) {
                                MediaShelfType.MUSIC -> "الموسيقى 🎶"
                                MediaShelfType.VIDEOS -> "الفيديوهات 📽️"
                                MediaShelfType.PHOTOS -> "الصور 🖼️"
                                else -> ""
                            },
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = { EveState.closeShelf() }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onMediaItemSelected(item) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (item.type) {
                                        "AUDIO" -> Icons.Default.MusicNote
                                        "VIDEO" -> Icons.Default.PlayCircle
                                        else -> Icons.Default.Image
                                    },
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
