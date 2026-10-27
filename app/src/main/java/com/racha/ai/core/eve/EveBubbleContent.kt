package com.racha.ai.core.eve

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.racha.ai.core.media.LocalMediaManager
import com.racha.ai.core.media.MediaItemData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val mediaManager = remember { LocalMediaManager(context) }

    // مستوى الزوم للشاشة (0: عادي 265dp، 1: متوسط 305dp، 2: كبير 345dp، 3: مسرحي 380dp)
    var zoomLevel by remember { mutableIntStateOf(0) }
    var activePreviewItem by remember { mutableStateOf<MediaItemData?>(null) }

    LaunchedEffect(shelfType) {
        activePreviewItem = null
        zoomLevel = 0
    }

    val items by produceState<List<MediaItemData>>(initialValue = emptyList(), shelfType) {
        if (shelfType != MediaShelfType.NONE) {
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
        // --- 1. جسد EVE وإيماءات اللمس ---
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

        // --- 2. نافذة الوسائط والمشغل السينمائي المطوّر ---
        AnimatedVisibility(
            visible = shelfType != MediaShelfType.NONE,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // أبعاد النافذة حسب مستوى التكبير المختار (0 إلى 3)
            val windowWidth = when (zoomLevel) {
                1 -> 305.dp
                2 -> 345.dp
                3 -> 380.dp
                else -> 265.dp
            }
            val windowHeight = when (zoomLevel) {
                1 -> 400.dp
                2 -> 480.dp
                3 -> 560.dp
                else -> 330.dp
            }

            val zoomLabel = when (zoomLevel) {
                1 -> "2x"
                2 -> "3x"
                3 -> "4x 🎬"
                else -> "1x"
            }

            Surface(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .width(windowWidth)
                    .height(windowHeight),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF070B16).copy(alpha = 0.98f),
                border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f)),
                shadowElevation = 16.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // شريط العنوان وأزرار التحجيم
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activePreviewItem != null) "🎬 المشغل ($zoomLabel)" else when (shelfType) {
                                MediaShelfType.MUSIC -> "الموسيقى (${items.size}) 🎶"
                                MediaShelfType.VIDEOS -> "الفيديوهات (${items.size}) 📽️"
                                MediaShelfType.PHOTOS -> "الصور (${items.size}) 🖼️"
                                else -> ""
                            },
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // زر التبديل بين مستويات الزوم الأربعة (1x -> 2x -> 3x -> 4x -> 1x)
                            IconButton(
                                onClick = { zoomLevel = (zoomLevel + 1) % 4 },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (zoomLevel > 0) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "تحجيم الشاشة",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (activePreviewItem != null) {
                                TextButton(
                                    onClick = {
                                        activePreviewItem = null
                                        zoomLevel = 0
                                    },
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

                    // 1. شاشة المشغل المباشر
                    if (activePreviewItem != null) {
                        val currentItem = activePreviewItem!!
                        val currentIndex = items.indexOfFirst { it.id == currentItem.id }

                        if (currentItem.type == "VIDEO") {
                            var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
                            var isPlaying by remember { mutableStateOf(true) }
                            var currentPosition by remember { mutableIntStateOf(0) }
                            var totalDuration by remember { mutableIntStateOf(1) }

                            // تحديث الفيديو عند الانتقال للتالي أو السابق
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
                                        update = { vv ->
                                            videoViewRef = vv
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // شريط التحكم السينمائي مع أزرار التالي والسابق
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
                                            // 1. الفيديو السابق
                                            IconButton(
                                                onClick = {
                                                    if (currentIndex > 0) {
                                                        activePreviewItem = items[currentIndex - 1]
                                                    }
                                                },
                                                enabled = currentIndex > 0,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.SkipPrevious,
                                                    contentDescription = "السابق",
                                                    tint = if (currentIndex > 0) Color(0xFF00E5FF) else Color.DarkGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            // 2. ترجيع 10 ثوانٍ
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

                                            // 3. تشغيل / إيقاف
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

                                            // 4. تقديم 10 ثوانٍ
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

                                            // 5. الفيديو التالي
                                            IconButton(
                                                onClick = {
                                                    if (currentIndex != -1 && currentIndex < items.lastIndex) {
                                                        activePreviewItem = items[currentIndex + 1]
                                                    }
                                                },
                                                enabled = currentIndex != -1 && currentIndex < items.lastIndex,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.SkipNext,
                                                    contentDescription = "التالي",
                                                    tint = if (currentIndex != -1 && currentIndex < items.lastIndex) Color(0xFF00E5FF) else Color.DarkGray,
                                                    modifier = Modifier.size(18.dp)
                                                )
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    } else {
                        // 2. قائمة الاستعراض مع البوسترات الحية والصور المصغرة
                        if (items.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("لا توجد ملفات أو جاري الفحص...", color = Color.Gray, fontSize = 11.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(items) { item ->
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
                                        // البوستر الحقيقي المصغر للفيديو أو الصورة بدلاً من الأيقونة الزرقاء!
                                        MediaItemThumbnail(
                                            item = item,
                                            modifier = Modifier
                                                .size(width = 46.dp, height = 34.dp)
                                                .clip(RoundedCornerShape(6.dp))
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
            }
        }
    }
}

/**
 * مكون استخراج الصورة المصغرة الحية للفيديو أو الصورة من ذاكرة أندرويد
 */
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

private fun formatMillis(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
