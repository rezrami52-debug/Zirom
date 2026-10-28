package com.racha.ai.core.eve

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Size
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.racha.ai.core.media.LocalMediaManager
import com.racha.ai.core.media.MediaItemData
import com.racha.ai.core.shizuku.ShizukuShellRunner
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.AppNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val eveUiState by EveState.uiState.collectAsState()
    val isSleeping = eveUiState.mood == EveMood.SLEEPING
    val isNotifying = eveUiState.mood == EveMood.NOTIFICATION_ALERT
    val playback = eveUiState.playback
    val hasActiveMedia = playback.title.isNotBlank()
    val shelfType = eveUiState.shelfType

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val mediaManager = remember { LocalMediaManager(context) }

    val notifications by NotificationRepository.notifications.collectAsState()

    val maxAllowedWidth = (configuration.screenWidthDp - 25).dp
    val maxAllowedHeight = (configuration.screenHeightDp - 100).dp
    var customWidth by remember { mutableStateOf(275.dp) }
    var customHeight by remember { mutableStateOf(350.dp) }

    var activePreviewItem by remember { mutableStateOf<MediaItemData?>(null) }

    LaunchedEffect(shelfType) {
        activePreviewItem = null
    }

    val mediaItems by produceState<List<MediaItemData>>(initialValue = emptyList(), shelfType) {
        if (shelfType != MediaShelfType.NONE && shelfType != MediaShelfType.NOTIFICATIONS) {
            value = withContext(Dispatchers.IO) {
                when (shelfType) {
                    MediaShelfType.MUSIC -> mediaManager.getLocalAudios()
                    MediaShelfType.VIDEOS -> mediaManager.getLocalVideos()
                    MediaShelfType.PHOTOS -> mediaManager.getLocalPhotos()
                    else -> emptyList()
                }
            }
        } else {
            value = emptyList()
        }
    }

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

    val eyeGlow by infiniteTransition.animateFloat(
        initialValue = if (isSleeping) 0.4f else 0.85f,
        targetValue = if (isSleeping) 0.6f else 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isNotifying) 400 else 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeGlow"
    )

    val textOffset by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = -60f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chestMarquee"
    )

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

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .offset { IntOffset(0, hoverY.roundToInt()) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isDrag = false
                        val startX = down.position.x
                        val startY = down.position.y
                        val startTime = System.currentTimeMillis()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            val dx = change.position.x - startX
                            val dy = change.position.y - startY

                            if (!isDrag && (kotlin.math.abs(dx) > 12f || kotlin.math.abs(dy) > 12f)) {
                                isDrag = true
                            }

                            if (isDrag) {
                                val moveDx = change.position.x - change.previousPosition.x
                                val moveDy = change.position.y - change.previousPosition.y
                                change.consume()
                                onDrag(moveDx, moveDy)
                            }
                        }

                        if (isDrag) {
                            onDragEnd()
                        } else {
                            val duration = System.currentTimeMillis() - startTime
                            if (duration > 650) {
                                onDismiss()
                            } else {
                                EveState.setMood(EveMood.AWAKE_SMILING)
                                onToggleSleepWake(true)
                            }
                        }
                    }
                }
        ) {
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
                        size = GeometrySize(w * 0.84f, h * 0.90f),
                        cornerRadius = CornerRadius(w * 0.42f, h * 0.45f)
                    )

                    drawRoundRect(
                        brush = bodyBrush,
                        topLeft = Offset(w * 0.07f, h * 0.03f),
                        size = GeometrySize(w * 0.86f, h * 0.92f),
                        cornerRadius = CornerRadius(w * 0.43f, h * 0.46f)
                    )

                    drawRoundRect(
                        color = if (isNotifying) Color(0xFFFF1744) else Color(0xFFC7D3E0),
                        topLeft = Offset(w * 0.07f, h * 0.03f),
                        size = GeometrySize(w * 0.86f, h * 0.92f),
                        cornerRadius = CornerRadius(w * 0.43f, h * 0.46f),
                        style = Stroke(width = if (isNotifying) 2.5f else 1.5f)
                    )

                    val visorWidth = w * 0.64f
                    val visorHeight = h * 0.32f
                    val visorTop = h * 0.13f
                    val visorLeft = (w - visorWidth) / 2f

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF151922), Color(0xFF090B0E), Color(0xFF020305))
                        ),
                        topLeft = Offset(visorLeft, visorTop),
                        size = GeometrySize(visorWidth, visorHeight),
                        cornerRadius = CornerRadius(visorWidth * 0.42f, visorHeight * 0.50f)
                    )

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
                            size = GeometrySize(eyeWidth, eyeHeight),
                            cornerRadius = CornerRadius(eyeWidth * 0.45f, eyeHeight * 0.45f)
                        )
                        drawRoundRect(
                            brush = Brush.radialGradient(listOf(Color(0xFFE0FFFF), eyeGlowColor, Color(0xFF0091EA)), center = Offset(rightEyeX, eyeCenterY), radius = eyeWidth * eyeGlow),
                            topLeft = Offset(rightEyeX - eyeWidth / 2f, eyeCenterY - eyeHeight / 2f),
                            size = GeometrySize(eyeWidth, eyeHeight),
                            cornerRadius = CornerRadius(eyeWidth * 0.45f, eyeHeight * 0.45f)
                        )
                    }
                }

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
                            Icon(Icons.Default.FastRewind, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(16.dp))
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
                            Icon(Icons.Default.FastForward, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // --- 2. نافذة العرض العائمة مع التحجيم الحر وسحب الإشعارات ---
        AnimatedVisibility(
            visible = shelfType != MediaShelfType.NONE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .width(customWidth)
                    .height(customHeight),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF070B16).copy(alpha = 0.98f),
                border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f)),
                shadowElevation = 18.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(10.dp).fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    activePreviewItem != null -> "🎬 المشغل السينمائي"
                                    shelfType == MediaShelfType.NOTIFICATIONS -> "الإشعارات (${notifications.size}) 🔔"
                                    shelfType == MediaShelfType.MUSIC -> "الموسيقى (${mediaItems.size}) 🎶"
                                    shelfType == MediaShelfType.VIDEOS -> "الفيديوهات (${mediaItems.size}) 📽️"
                                    shelfType == MediaShelfType.PHOTOS -> "الصور (${mediaItems.size}) 🖼️"
                                    else -> ""
                                },
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (customWidth > 320.dp) {
                                            customWidth = 275.dp
                                            customHeight = 350.dp
                                        } else {
                                            customWidth = maxAllowedWidth
                                            customHeight = maxAllowedHeight
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (customWidth > 320.dp) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "ملء الشاشة",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (activePreviewItem != null) {
                                    TextButton(
                                        onClick = { activePreviewItem = null },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("رجوع ↩️", color = Color.White, fontSize = 11.sp)
                                    }
                                }

                                IconButton(onClick = { EveState.closeShelf() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        if (shelfType == MediaShelfType.NOTIFICATIONS) {
                            if (notifications.isEmpty()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("لا توجد إشعارات جديدة حالياً يا رمزي", color = Color.Gray, fontSize = 11.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(notifications) { notif ->
                                        NotificationItemRow(notif = notif) {
                                            openAppInFreeform(context, notif.packageName)
                                        }
                                    }
                                }
                            }
                        } else if (activePreviewItem != null) {
                            val currentItem = activePreviewItem!!
                            val currentIndex = mediaItems.indexOfFirst { it.id == currentItem.id }

                            if (currentItem.type == "VIDEO") {
                                var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
                                var isPlaying by remember { mutableStateOf(true) }
                                var currentPosition by remember { mutableIntStateOf(0) }
                                var totalDuration by remember { mutableIntStateOf(1) }

                                LaunchedEffect(currentItem.uri) {
                                    currentPosition = 0
                                    isPlaying = true
                                    videoViewRef?.setVideoURI(currentItem.uri)
                                    videoViewRef?.start()
                                }

                                LaunchedEffect(isPlaying) {
                                    while (isPlaying) {
                                        videoViewRef?.let { vv ->
                                            if (vv.isPlaying) {
                                                currentPosition = vv.currentPosition
                                                totalDuration = vv.duration.coerceAtLeast(1)
                                            }
                                        }
                                        delay(400)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black)
                                    ) {
                                        AndroidView(
                                            factory = { ctx ->
                                                VideoView(ctx).apply {
                                                    setVideoURI(currentItem.uri)
                                                    setOnPreparedListener { mp ->
                                                        totalDuration = duration.coerceAtLeast(1)
                                                        mp.isLooping = true
                                                        start()
                                                        isPlaying = true
                                                    }
                                                    videoViewRef = this
                                                }
                                            },
                                            update = { vv -> videoViewRef = vv },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF0F172A).copy(alpha = 0.95f),
                                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(formatMillis(currentPosition), color = Color.White, fontSize = 9.sp)
                                                Slider(
                                                    value = currentPosition.toFloat(),
                                                    onValueChange = {
                                                        currentPosition = it.toInt()
                                                        videoViewRef?.seekTo(it.toInt())
                                                    },
                                                    valueRange = 0f..totalDuration.toFloat(),
                                                    modifier = Modifier.weight(1f).height(18.dp)
                                                )
                                                Text(formatMillis(totalDuration), color = Color.Gray, fontSize = 9.sp)
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (currentIndex > 0) activePreviewItem = mediaItems[currentIndex - 1]
                                                    },
                                                    enabled = currentIndex > 0,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.SkipPrevious, contentDescription = "السابق", tint = if (currentIndex > 0) Color(0xFF00E5FF) else Color.DarkGray, modifier = Modifier.size(18.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        videoViewRef?.let { vv ->
                                                            val target = (vv.currentPosition - 10000).coerceAtLeast(0)
                                                            vv.seekTo(target)
                                                            currentPosition = target
                                                        }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.FastRewind, contentDescription = "ترجيع 10ث", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        videoViewRef?.let { vv ->
                                                            if (vv.isPlaying) {
                                                                vv.pause()
                                                                isPlaying = false
                                                            } else {
                                                                vv.start()
                                                                isPlaying = true
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.size(34.dp).background(Color(0xFF00E5FF), CircleShape)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = "تشغيل/إيقاف",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        videoViewRef?.let { vv ->
                                                            val target = (vv.currentPosition + 10000).coerceAtMost(vv.duration)
                                                            vv.seekTo(target)
                                                            currentPosition = target
                                                        }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Default.FastForward, contentDescription = "تقديم 10ث", tint = Color.White, modifier = Modifier.size(16.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        if (currentIndex != -1 && currentIndex < mediaItems.lastIndex) activePreviewItem = mediaItems[currentIndex + 1]
                                                    },
                                                    enabled = currentIndex != -1 && currentIndex < mediaItems.lastIndex,
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.SkipNext, contentDescription = "التالي", tint = if (currentIndex != -1 && currentIndex < mediaItems.lastIndex) Color(0xFF00E5FF) else Color.DarkGray, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (currentItem.type == "PHOTO") {
                                val bmp = remember(currentItem) {
                                    try {
                                        context.contentResolver.openInputStream(currentItem.uri)?.use { stream ->
                                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                                        }
                                    } catch (_: Exception) { null }
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        } else {
                            if (mediaItems.isEmpty()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("لا توجد ملفات أو جاري الفحص...", color = Color.Gray, fontSize = 11.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(mediaItems) { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                if (item.type == "AUDIO") {
                                                    onMediaItemSelected(item)
                                                } else {
                                                    activePreviewItem = item
                                                }
                                            }
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(vertical = 5.dp, horizontal = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MediaItemThumbnail(
                                                item = item,
                                                modifier = Modifier.size(width = 46.dp, height = 34.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // مقبض السحب اليدوي الحر في زاوية النافذة السفلية
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    with(density) {
                                        val newW = customWidth + dragAmount.x.toDp()
                                        val newH = customHeight + dragAmount.y.toDp()
                                        customWidth = newW.coerceIn(240.dp, maxAllowedWidth)
                                        customHeight = newH.coerceIn(260.dp, maxAllowedHeight)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "تحجيم يدوي",
                            tint = Color(0xFF00E5FF).copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItemRow(
    notif: AppNotification,
    onDragOutToOpen: () -> Unit
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1628)),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(dragOffsetX.roundToInt(), 0) }
            .pointerInput(notif.packageName) {
                detectDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(dragOffsetX) > 90f) {
                            onDragOutToOpen()
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                    }
                )
            }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(notif.appName, color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("اسحب لفتح نافذة 🪟", color = Color(0xFF38BDF8), fontSize = 9.sp)
            }
            if (notif.title.isNotBlank()) {
                Text(notif.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            if (notif.text.isNotBlank()) {
                Text(notif.text, color = Color(0xFFCBD5E1), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notif.timestamp)),
                color = Color.Gray,
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun MediaItemThumbnail(item: MediaItemData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(item.uri, Size(120, 120), null)
                } else {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (item.type) {
                    "VIDEO" -> Icons.Default.PlayCircle
                    "AUDIO" -> Icons.Default.MusicNote
                    else -> Icons.Default.Image
                },
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun openAppInFreeform(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val componentName = launchIntent?.component?.flattenToShortString()
        if (componentName != null) {
            ShizukuShellRunner.execCommand("am start -n $componentName --windowingMode 5")
        } else {
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    } catch (_: Exception) {}
}

private fun formatMillis(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
