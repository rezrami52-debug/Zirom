package com.racha.ai.core.eve
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.min
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.compose.foundation.gestures.detectTransformGestures

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.Toast
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.racha.ai.core.media.IptvManager
import com.racha.ai.core.media.LocalMediaManager
import com.racha.ai.core.media.MediaItemData
import com.racha.ai.core.media.TvChannel
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.core.shizuku.ShizukuShellRunner
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EveBubbleAvatar(
    modifier: Modifier = Modifier,
    onToggleSleepWake: (Boolean) -> Unit = {},
    onOpenApp: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onMediaPlayPause: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onMediaPrev: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {}
) {
    val eveUiState by EveState.uiState.collectAsState()
    val isSleeping = eveUiState.mood == EveMood.SLEEPING
    val isNotifying = eveUiState.mood == EveMood.NOTIFICATION_ALERT
    val playback = eveUiState.playback
    val hasActiveMedia = playback.title.isNotBlank()

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

    val ghostAlpha by animateFloatAsState(
        targetValue = if (isSleeping) 0.35f else 1.0f,
        animationSpec = tween(durationMillis = 450),
        label = "ghostAlpha"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(0, hoverY.roundToInt()) }
            .graphicsLayer { alpha = ghostAlpha }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.pointerInput(Unit) {
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
                modifier = Modifier.size(width = 78.dp, height = 108.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawEveOriginalStyle(
                        glow = eyeGlow,
                        isSleeping = isSleeping,
                        eyeHeightScale = eyeHeightScale,
                        isNotifying = isNotifying,
                        speaking = (eveUiState.mood == EveMood.OBSERVING_SCREEN || eveUiState.mood == EveMood.AWAKE_SMILING)
                    )
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

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFFE11D48))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * النافذة المستقلة: تدعم التلفاز، قنوات الأفلام، المشغل السينمائي، الصور، والموسيقى
 */
@Composable
fun EveStandaloneShelfWindow(
    targetShelfType: MediaShelfType,
    onClose: () -> Unit,
    onMoveWindow: (dx: Float, dy: Float) -> Unit,
    onResizeWindow: (dw: Float, dh: Float) -> Unit,
    onToggleFullscreen: () -> Unit = {},
    onPlayAudio: (MediaItemData) -> Unit,
    onFocusWindow: () -> Unit = {}
) {
    val shelfType = targetShelfType
    val context = LocalContext.current
    val mediaManager = remember { LocalMediaManager(context) }
    val iptvManager = remember { IptvManager(context) }
    val notifications by NotificationRepository.notifications.collectAsState()
    val appPrefs = remember { AppPreferences(context) }
    val eveUiState by EveState.uiState.collectAsState()

    var activePreviewItem by remember { mutableStateOf<MediaItemData?>(null) }
    val activeTvChannel = if (shelfType == MediaShelfType.LIVE_TV) eveUiState.activeTvChannel else null
    var isTvLoading by remember { mutableStateOf(false) }

    var innerContentZoom by remember { mutableFloatStateOf(1.0f) }
    var videoVolume by remember { mutableFloatStateOf(1.0f) }

    // فئات التلفاز (مع إضافة فئة الأفلام 🎬)
    var selectedCategory by remember { mutableStateOf("الكل") }
    var tvSearchQuery by remember { mutableStateOf("") }

    val tvChannels by produceState<List<TvChannel>>(initialValue = emptyList(), shelfType) {
        if (shelfType == MediaShelfType.LIVE_TV) {
            value = withContext(Dispatchers.IO) { iptvManager.getAllChannels() }
        }
    }

    val filteredTvChannels by produceState<List<TvChannel>>(
        initialValue = emptyList(),
        tvChannels, selectedCategory, tvSearchQuery
    ) {
        value = withContext(Dispatchers.IO) {
            val q = tvSearchQuery.trim().lowercase()
            tvChannels.asSequence().filter { ch ->
                (selectedCategory == "الكل" || 
                 ch.category.contains(selectedCategory, ignoreCase = true) || 
                 (selectedCategory == "أفلام" && (ch.category == "أفلام" || ch.name.lowercase().contains("cinema") || ch.name.contains("سينما"))) ||
                 (selectedCategory == "رياضة" && ch.country.contains("رياض", ignoreCase = true))) &&
                (q.isBlank() || ch.name.lowercase().contains(q))
            }.take(100).toList()
        }
    }

    val mediaItems by produceState<List<MediaItemData>>(initialValue = emptyList(), shelfType) {
        if (shelfType != MediaShelfType.NONE && shelfType != MediaShelfType.NOTIFICATIONS && shelfType != MediaShelfType.LIVE_TV) {
            value = withContext(Dispatchers.IO) {
                when (shelfType) {
                    MediaShelfType.MUSIC -> mediaManager.getLocalAudios()
                    MediaShelfType.VIDEOS -> mediaManager.getLocalVideos()
                    MediaShelfType.PHOTOS -> mediaManager.getLocalPhotos()
                    else -> emptyList()
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF070B16).copy(alpha = 0.98f),
        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f)),
        shadowElevation = 20.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(8.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onMoveWindow(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "سحب",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text(
                            text = when {
                                activeTvChannel != null -> "📺 ${activeTvChannel.name} 🔴"
                                activePreviewItem != null -> "🎬 المشغل السينمائي"
                                shelfType == MediaShelfType.LIVE_TV -> "التلفاز والسينما (${tvChannels.size}) 🎬"
                                shelfType == MediaShelfType.NOTIFICATIONS -> "الإشعارات (${notifications.size}) 🔔"
                                shelfType == MediaShelfType.MUSIC -> "الموسيقى (${mediaItems.size}) 🎶"
                                shelfType == MediaShelfType.VIDEOS -> "الفيديوهات (${mediaItems.size}) 📽️"
                                shelfType == MediaShelfType.PHOTOS -> "الصور (${mediaItems.size}) 🖼️"
                                else -> ""
                            },
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "ملء الشاشة", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                        }

                        if (activePreviewItem != null || activeTvChannel != null) {
                            TextButton(
                                onClick = {
                                    activePreviewItem = null
                                    EveState.setActiveTvChannel(null)
                                    isTvLoading = false
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("رجوع ↩️", color = Color.White, fontSize = 10.sp)
                            }
                        }

                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                // 1. شاشة التلفاز والأفلام المباشرة (ExoPlayer المحترف)
                if (shelfType == MediaShelfType.LIVE_TV) {
                    if (activeTvChannel != null) {
                        val currentCh = activeTvChannel
                        var isTvPlaying by remember { mutableStateOf(true) }

                        val exoPlayer = remember(currentCh.streamUrl) {
                            val httpSourceFactory = DefaultHttpDataSource.Factory()
                                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")
                                .setAllowCrossProtocolRedirects(true)
                                .setConnectTimeoutMs(15000)
                                .setReadTimeoutMs(15000)

                            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                                .setDataSourceFactory(httpSourceFactory)

                            ExoPlayer.Builder(context)
                                .setMediaSourceFactory(mediaSourceFactory)
                                .build().apply {
                                    setMediaItem(MediaItem.fromUri(currentCh.streamUrl))
                                    prepare()
                                    playWhenReady = true
                                    volume = videoVolume
                                }
                        }

                        DisposableEffect(exoPlayer) {
                            val listener = object : androidx.media3.common.Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    isTvLoading = (state == androidx.media3.common.Player.STATE_BUFFERING)
                                }
                                override fun onIsPlayingChanged(playing: Boolean) {
                                    isTvPlaying = playing
                                }
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    isTvLoading = false
                                    Toast.makeText(context, "تعذر تشغيل هذا البث حالياً، جرب قناة أخرى", Toast.LENGTH_SHORT).show()
                                }
                            }
                            exoPlayer.addListener(listener)
                            onDispose {
                                exoPlayer.removeListener(listener)
                                exoPlayer.release()
                            }
                        }

                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            this.player = exoPlayer
                                            useController = false
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                        }
                                    },
                                    update = { pv -> pv.player = exoPlayer },
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (isTvLoading) {
                                    CircularProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.size(36.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // شريط التحكم بالتشغيل والصوت
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // زر التشغيل والإيقاف المؤقت
                                    IconButton(
                                        onClick = {
                                            if (exoPlayer.isPlaying) {
                                                exoPlayer.pause()
                                            } else {
                                                exoPlayer.play()
                                            }
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTvPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "تشغيل / إيقاف",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // زر كتم الصوت
                                    IconButton(
                                        onClick = {
                                            videoVolume = if (videoVolume > 0f) 0f else 1f
                                            exoPlayer.volume = videoVolume
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (videoVolume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = "الصوت",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // مؤشر الصوت المستجيب للمس
                                    Slider(
                                        value = videoVolume,
                                        onValueChange = {
                                            videoVolume = it
                                            exoPlayer.volume = it
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier
                                            .width(90.dp)
                                            .padding(horizontal = 2.dp)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            exoPlayer.seekToDefaultPosition()
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.Gray, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🔴 مباشر", color = Color(0xFFFF1744), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // فلاتر الفئات (مع تبويب 🎬 أفلام الجديد)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                            ) {
                                val categories = listOf("الكل", "أفلام", "تونس", "رياضة", "أخبار", "عام", "وثائقي")
                                items(categories) { cat ->
                                    val isMovie = cat == "أفلام"
                                    FilterChip(
                                        selected = selectedCategory == cat,
                                        onClick = { selectedCategory = cat },
                                        label = { 
                                            Text(if (isMovie) "🎬 أفلام" else cat, fontSize = 10.sp, fontWeight = if (isMovie) FontWeight.Bold else FontWeight.Normal) 
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = if (isMovie) Color(0xFFFF9100).copy(alpha = 0.35f) else Color(0xFF00E5FF).copy(alpha = 0.25f),
                                            selectedLabelColor = if (isMovie) Color(0xFFFFB74D) else Color(0xFF00E5FF)
                                        )
                                    )
                                }
                            }

                            if (filteredTvChannels.isEmpty()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("جاري استخراج القنوات أو لا توجد نتائج...", color = Color.Gray, fontSize = 11.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredTvChannels, key = { it.id + it.streamUrl }) { ch ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { EveState.setActiveTvChannel(ch) }
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (ch.category == "أفلام") Icons.Default.Movie else Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (ch.category == "أفلام") Color(0xFFFFB74D) else Color(0xFF00E5FF),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ch.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Text("${ch.category} • ${ch.country}", color = Color.Gray, fontSize = 9.sp)
                                            }
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // 2. بقية الرفوف
                else if (shelfType == MediaShelfType.NOTIFICATIONS) {
                    if (notifications.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("لا توجد إشعارات جديدة حالياً يا رمزي", color = Color.Gray, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(notifications) { notif ->
                                NotificationItemRow(notif = notif) { openAppInFreeform(context, notif.packageName) }
                            }
                        }
                    }
                } else if (activePreviewItem != null) {
                    val currentItem = activePreviewItem!!
                    val currentIndex = mediaItems.indexOfFirst { it.id == currentItem.id }

                    if (currentItem.type == "VIDEO") {
                        var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
                        var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
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
                                                mediaPlayerRef = mp
                                                totalDuration = duration.coerceAtLeast(1)
                                                mp.isLooping = true
                                                mp.setVolume(videoVolume, videoVolume)
                                                start()
                                                isPlaying = true
                                            }
                                            videoViewRef = this
                                        }
                                    },
                                    update = { vv -> videoViewRef = vv },
                                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = innerContentZoom, scaleY = innerContentZoom)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (videoVolume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp).clickable {
                                        videoVolume = if (videoVolume > 0f) 0f else 1f
                                        mediaPlayerRef?.setVolume(videoVolume, videoVolume)
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Slider(
                                    value = videoVolume,
                                    onValueChange = {
                                        videoVolume = it
                                        mediaPlayerRef?.setVolume(it, it)
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f).height(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "${(videoVolume * 100).toInt()}%", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { if (currentIndex > 0) activePreviewItem = mediaItems[currentIndex - 1] }, enabled = currentIndex > 0, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = if (currentIndex > 0) Color(0xFF00E5FF) else Color.DarkGray, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { videoViewRef?.let { vv -> val target = (vv.currentPosition - 10000).coerceAtLeast(0); vv.seekTo(target); currentPosition = target } }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Default.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { videoViewRef?.let { vv -> if (vv.isPlaying) { vv.pause(); isPlaying = false } else { vv.start(); isPlaying = true } } }, modifier = Modifier.size(34.dp).background(Color(0xFF00E5FF), CircleShape)) {
                                            Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = { videoViewRef?.let { vv -> val target = (vv.currentPosition + 10000).coerceAtMost(vv.duration); vv.seekTo(target); currentPosition = target } }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Default.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { if (currentIndex != -1 && currentIndex < mediaItems.lastIndex) activePreviewItem = mediaItems[currentIndex + 1] }, enabled = currentIndex != -1 && currentIndex < mediaItems.lastIndex, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = if (currentIndex != -1 && currentIndex < mediaItems.lastIndex) Color(0xFF00E5FF) else Color.DarkGray, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else if (currentItem.type == "PHOTO") {
                        val bmp = remember(currentItem) {
                            try {
                                context.contentResolver.openInputStream(currentItem.uri)?.use { stream -> BitmapFactory.decodeStream(stream)?.asImageBitmap() }
                            } catch (_: Exception) { null }
                        }
                        var zoomScale by remember(currentItem.uri) { mutableFloatStateOf(1f) }
                        var panOffsetX by remember(currentItem.uri) { mutableFloatStateOf(0f) }
                        var panOffsetY by remember(currentItem.uri) { mutableFloatStateOf(0f) }
                        var horizontalSwipe by remember(currentItem.uri) { mutableFloatStateOf(0f) }
                        var showPhotoOptions by remember(currentItem.uri) { mutableStateOf(false) }

                        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .pointerInput(currentItem.uri, currentIndex) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val wasZoomed = zoomScale > 1.05f

                                            zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)

                                            if (zoomScale > 1.05f || wasZoomed) {
                                                panOffsetX += pan.x * zoomScale
                                                panOffsetY += pan.y * zoomScale
                                                horizontalSwipe = 0f
                                            } else {
                                                zoomScale = 1f
                                                panOffsetX = 0f
                                                panOffsetY = 0f

                                                if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                                    horizontalSwipe += pan.x

                                                    if (kotlin.math.abs(horizontalSwipe) >= 120f) {
                                                        val targetIndex =
                                                            if (horizontalSwipe < 0f) {
                                                                currentIndex + 1
                                                            } else {
                                                                currentIndex - 1
                                                            }

                                                        if (targetIndex in mediaItems.indices) {
                                                            activePreviewItem = mediaItems[targetIndex]
                                                        }

                                                        horizontalSwipe = 0f
                                                    }
                                                } else {
                                                    horizontalSwipe = 0f
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = zoomScale,
                                                scaleY = zoomScale,
                                                translationX = panOffsetX,
                                                translationY = panOffsetY
                                            ),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "💡 اسحب الصورة لأعلى أو لأسفل لتحويلها لخلفية الهاتف", color = Color(0xFF38BDF8), fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A).copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { if (currentIndex > 0) activePreviewItem = mediaItems[currentIndex - 1] }, enabled = currentIndex > 0, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = if (currentIndex > 0) Color(0xFF00E5FF) else Color.DarkGray)
                                    }
                                    TextButton(onClick = {
                                        appPrefs.setBackgroundImagePath(currentItem.uri.toString())
                                        Toast.makeText(context, "✅ تم تعيين الصورة كخلفية لتطبيق Racha", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Text("خلفية للتطبيق 🎨", color = Color(0xFF00E5FF), fontSize = 10.sp)
                                    }
                                    IconButton(onClick = { if (currentIndex != -1 && currentIndex < mediaItems.lastIndex) activePreviewItem = mediaItems[currentIndex + 1] }, enabled = currentIndex != -1 && currentIndex < mediaItems.lastIndex, modifier = Modifier.size(30.dp)) {
                                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = if (currentIndex != -1 && currentIndex < mediaItems.lastIndex) Color(0xFF00E5FF) else Color.DarkGray)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (mediaItems.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("لا توجد ملفات أو جاري الفحص...", color = Color.Gray, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(mediaItems) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (item.type == "AUDIO") onPlayAudio(item) else activePreviewItem = item
                                        }
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .padding(vertical = 5.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MediaItemThumbnail(item = item, modifier = Modifier.size(width = 46.dp, height = 34.dp).clip(RoundedCornerShape(6.dp)))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = item.title, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // مقبض التحجيم اليدوي بالبكسل في الزاوية
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onResizeWindow(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.OpenInFull, contentDescription = "تحجيم حر", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
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
                        if (kotlin.math.abs(dragOffsetX) > 90f) onDragOutToOpen()
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(notif.appName, color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("اسحب لفتح نافذة 🪟", color = Color(0xFF38BDF8), fontSize = 9.sp)
            }
            if (notif.title.isNotBlank()) Text(notif.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            if (notif.text.isNotBlank()) Text(notif.text, color = Color(0xFFCBD5E1), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    if (bitmap != null) {
        Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier = modifier.background(Color(0xFF1E293B)), contentAlignment = Alignment.Center) {
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

private fun shareMedia(context: Context, item: MediaItemData) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.type == "VIDEO") "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ${item.title}").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    } catch (_: Exception) {}
}

private fun editMedia(context: Context, item: MediaItemData) {
    try {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(item.uri, if (item.type == "VIDEO") "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun setAsPhoneWallpaper(context: Context, uri: Uri) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val wm = WallpaperManager.getInstance(context)
                wm.setStream(stream)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ تم تعيين الصورة كخلفية لشاشة الهاتف بنجاح!", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {}
    }
}

private fun formatMillis(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}


fun DrawScope.drawEveOriginalStyle(
    glow: Float,
    isSleeping: Boolean = false,
    eyeHeightScale: Float = 1f,
    isNotifying: Boolean = false,
    speaking: Boolean = false
) {
    // نظام إحداثيات ثابت: العرض 100 والطول 140
    val unit = min(size.width / 100f, size.height / 140f)
    val offsetX = (size.width - unit * 100f) / 2f
    val offsetY = (size.height - unit * 140f) / 2f

    withTransform({
        translate(offsetX, offsetY)
        scale(unit, unit, pivot = Offset.Zero) // الارتكاز الصحيح لمنع اختفاء الرأس
    }) {
        // ظل التحويم ثلاثي الأبعاد أسفل إيف
        drawOval(
            color = Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(32f, 128f),
            size = androidx.compose.ui.geometry.Size(36f, 4f)
        )
        drawOval(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(37f, 128.4f),
            size = androidx.compose.ui.geometry.Size(26f, 1.8f)
        )

        // الذراع الأيسر ثلاثي الأبعاد بدون أي خطوط كرتونية
        val leftArm = Path().apply {
            moveTo(22f, 62f)
            cubicTo(17.5f, 63f, 14.8f, 68f, 14.2f, 76f)
            cubicTo(13.5f, 87f, 14.5f, 103f, 17.2f, 113f)
            cubicTo(18f, 116f, 20.5f, 116f, 21.3f, 112.5f)
            cubicTo(23.7f, 101f, 25f, 84f, 25.2f, 72f)
            cubicTo(25.3f, 66f, 24.4f, 63f, 22f, 62f)
            close()
        }
        drawPath(
            path = leftArm,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FBFC), Color(0xFFD7E3E8), Color(0xFFAFC4CE)),
                start = Offset(14f, 65f),
                end = Offset(25f, 112f)
            )
        )

        // الذراع الأيمن ثلاثي الأبعاد
        val rightArm = Path().apply {
            moveTo(78f, 62f)
            cubicTo(82.5f, 63f, 85.2f, 68f, 85.8f, 76f)
            cubicTo(86.5f, 87f, 85.5f, 103f, 82.8f, 113f)
            cubicTo(82f, 116f, 79.5f, 116f, 78.7f, 112.5f)
            cubicTo(76.3f, 101f, 75f, 84f, 74.8f, 72f)
            cubicTo(74.7f, 66f, 75.6f, 63f, 78f, 62f)
            close()
        }
        drawPath(
            path = rightArm,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FBFC), Color(0xFFD7E3E8), Color(0xFFAFC4CE)),
                start = Offset(86f, 65f),
                end = Offset(75f, 112f)
            )
        )

        // الجسم الرئيسي بتظليل ناعم واقعي بدون حواف قلم
        val body = Path().apply {
            moveTo(25f, 57f)
            cubicTo(35f, 56f, 65f, 56f, 75f, 57f)
            cubicTo(78f, 57.5f, 78.5f, 60f, 77.8f, 65f)
            cubicTo(76.8f, 78f, 73.5f, 99f, 66.5f, 113f)
            cubicTo(62f, 122f, 56f, 127f, 50f, 127.5f)
            cubicTo(44f, 127f, 38f, 122f, 33.5f, 113f)
            cubicTo(26.5f, 99f, 23.2f, 78f, 22.2f, 65f)
            cubicTo(21.5f, 60f, 22f, 57.5f, 25f, 57f)
            close()
        }
        drawPath(
            path = body,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFFAFCFD), Color(0xFFE3EDF1), Color(0xFFB2C7D1)),
                start = Offset(23f, 58f),
                end = Offset(76f, 125f)
            )
        )
        // بؤرة لمعان السيراميك البراقة على الكتف
        drawOval(
            color = Color.White.copy(alpha = 0.26f),
            topLeft = Offset(28f, 61f),
            size = androidx.compose.ui.geometry.Size(18f, 38f)
        )

        // ظل ناعم تحت الرأس وفوق الكتفين
        drawOval(
            color = Color(0xFF8EA5B1).copy(alpha = 0.22f),
            topLeft = Offset(31f, 55.5f),
            size = androidx.compose.ui.geometry.Size(38f, 5f)
        )

        // مؤشر الصدر الأيقوني الصغير
        drawCircle(color = Color(0xFF00E5FF), radius = 1.3f, center = Offset(45f, 78f))
        drawCircle(color = Color(0xFF00E5FF), radius = 1.3f, center = Offset(48f, 78f))
        drawCircle(color = Color(0xFF00E5FF), radius = 1.3f, center = Offset(51f, 78f))
        drawCircle(color = Color(0xFFFF5252).copy(alpha = 0.85f), radius = 2.0f, center = Offset(56f, 78f), style = Stroke(width = 0.8f))

        // الرأس المصقول كروياً بدون خطوط كرتونية
        val head = Path().apply {
            moveTo(22f, 13f)
            cubicTo(27f, 7f, 38f, 4.5f, 50f, 4.5f)
            cubicTo(62f, 4.5f, 73f, 7f, 78f, 13f)
            cubicTo(83f, 19f, 84.5f, 32f, 82f, 42f)
            cubicTo(79.5f, 52f, 68f, 57f, 50f, 57.5f)
            cubicTo(32f, 57f, 20.5f, 52f, 18f, 42f)
            cubicTo(15.5f, 32f, 17f, 19f, 22f, 13f)
            close()
        }
        drawPath(
            path = head,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFFAFCFD), Color(0xFFE5EEF2), Color(0xFFB8CBD5)),
                start = Offset(20f, 8f),
                end = Offset(78f, 55f)
            )
        )
        // بؤرة لمعان السيراميك الحقيقية على قمة الرأس
        drawOval(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(25f, 9f),
            size = androidx.compose.ui.geometry.Size(20f, 15f)
        )

        // لمعة الحافة الانعكاسية (Rim Highlight)
        drawArc(
            color = Color.White.copy(alpha = 0.84f),
            startAngle = 205f,
            sweepAngle = 92f,
            useCenter = false,
            topLeft = Offset(20f, 7f),
            size = androidx.compose.ui.geometry.Size(57f, 43f),
            style = Stroke(width = 1.4f, cap = StrokeCap.Round)
        )

        // القناع الزجاجي الأسود الداكن (Smoked Curved Glass)
        val visor = Path().apply {
            moveTo(29f, 21f)
            cubicTo(35f, 17f, 43f, 15.5f, 50f, 15.5f)
            cubicTo(57f, 15.5f, 65f, 17f, 71f, 21f)
            cubicTo(77f, 25f, 78.8f, 34f, 76f, 41f)
            cubicTo(73f, 48f, 63f, 51f, 50f, 51f)
            cubicTo(37f, 51f, 27f, 48f, 24f, 41f)
            cubicTo(21.2f, 34f, 23f, 25f, 29f, 21f)
            close()
        }
        drawPath(
            path = visor,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF173B51), Color(0xFF07131D), Color(0xFF020509)),
                center = Offset(50f, 31f),
                radius = 32f
            )
        )
        // حافة زجاجية خفيفة جداً بدلاً من الخط الكرتوني
        drawPath(
            path = visor,
            color = Color(0xFFBFEAF4).copy(alpha = 0.40f),
            style = Stroke(width = 0.35f)
        )

        val eyeColor = if (isNotifying) Color(0xFFFF1744) else Color(0xFF00E5FF)

        if (isSleeping) {
            // وضع النوم: قوس هادئ ومبتسم بلطف
            val sleepL = Path().apply {
                moveTo(31f, 34f)
                quadraticBezierTo(38f, 39f, 45f, 34f)
            }
            val sleepR = Path().apply {
                moveTo(55f, 34f)
                quadraticBezierTo(62f, 39f, 69f, 34f)
            }
            drawPath(sleepL, color = eyeColor.copy(alpha = 0.85f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
            drawPath(sleepR, color = eyeColor.copy(alpha = 0.85f), style = Stroke(width = 2.4f, cap = StrokeCap.Round))
        } else {
            // توهج العيون النيون الطبيعي من خلف الزجاج
            drawOval(
                color = eyeColor.copy(alpha = 0.17f * glow),
                topLeft = Offset(29f, 26f),
                size = androidx.compose.ui.geometry.Size(18f, 17f)
            )
            drawOval(
                color = eyeColor.copy(alpha = 0.17f * glow),
                topLeft = Offset(53f, 26f),
                size = androidx.compose.ui.geometry.Size(18f, 17f)
            )

            // العينان النيون الرقميتان
            val eyeHeight = 10f * eyeHeightScale
            val eyeTop = 29f + (10f - eyeHeight) / 2f
            val eyeBrushL = Brush.radialGradient(
                colors = listOf(Color(0xFFB8FFFF), eyeColor, Color(0xFF0089C4)),
                center = Offset(38f, 34f),
                radius = 12f
            )
            val eyeBrushR = Brush.radialGradient(
                colors = listOf(Color(0xFFB8FFFF), eyeColor, Color(0xFF0089C4)),
                center = Offset(62f, 34f),
                radius = 12f
            )
            drawOval(eyeBrushL, Offset(31f, eyeTop), androidx.compose.ui.geometry.Size(14f, eyeHeight))
            drawOval(eyeBrushR, Offset(55f, eyeTop), androidx.compose.ui.geometry.Size(14f, eyeHeight))

            // خطوط المسح الرقمية الأفقية
            if (eyeHeightScale > 0.4f) {
                drawLine(
                    color = Color.White.copy(alpha = 0.25f * glow),
                    start = Offset(33f, 34f),
                    end = Offset(43f, 34f),
                    strokeWidth = 0.75f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.25f * glow),
                    start = Offset(57f, 34f),
                    end = Offset(67f, 34f),
                    strokeWidth = 0.75f,
                    cap = StrokeCap.Round
                )
            }
        }

        // انعكاس زجاجي بانحناء الشاشة يظهر العيون وكأنها خلف الزجاج
        drawArc(
            color = Color.White.copy(alpha = 0.20f),
            startAngle = 205f,
            sweepAngle = 65f,
            useCenter = false,
            topLeft = Offset(27f, 19f),
            size = androidx.compose.ui.geometry.Size(46f, 25f),
            style = Stroke(width = 0.8f, cap = StrokeCap.Round)
        )

        // وميض التحدث عند التفاعل
        if (speaking) {
            drawOval(
                color = Color(0xFF26E9FF).copy(alpha = 0.25f),
                topLeft = Offset(42f, 47.5f),
                size = androidx.compose.ui.geometry.Size(16f, 1.3f)
            )
        }
    }
}
