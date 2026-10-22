package com.racha.ai.core.eve

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class EveBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()

        // 1. تهيئة SavedStateRegistry أولاً
        savedStateRegistryController.performRestore(null)

        // 2. تهيئة Lifecycle
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // 3. إعداد الإشعار
        startForegroundWithNotification()

        // 4. إعداد WindowManager وعرض الفقاعة
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun startForegroundWithNotification() {
        val channelId = "eve_bubble_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "EVE Assistant", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("EVE جاهزة")
            .setContentText("المساعد الشخصي يعمل في الخلفية")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1001, notification)
    }

    private fun showBubble() {
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 500
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@EveBubbleService)
            setViewTreeSavedStateRegistryOwner(this@EveBubbleService)
            setContent {
                EveBubbleContent(
                    onClick = {
                        EveState.setStatus(EveStatus.AWAKE)
                        val intent = Intent(this@EveBubbleService, com.racha.ai.ui.screens.chat.ChatOverlayActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    },
                    onDrag = { dx, dy ->
                        bubbleParams?.let { params ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            bubbleView?.let { windowManager.updateViewLayout(it, params) }
                        }
                    },
                    onClose = { stopSelf() }
                )
            }
        }
        bubbleView = composeView
        try {
            android.util.Log.d("EveBubble", "Attempting to add bubble view")
            windowManager.addView(composeView, bubbleParams)
            android.util.Log.d("EveBubble", "Bubble added successfully")
        } catch (e: Exception) {
            android.util.Log.e("EveBubble", "Failed to add bubble: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun EveBubbleContent(onClick: () -> Unit, onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    val status by EveState.status.collectAsState()
    var isEyesOpen by remember { mutableStateOf(false) }

    // أنيميشن فتح/إغلاق العيون
    val eyeHeight by animateFloatAsState(
        targetValue = if (isEyesOpen) 18f else 4f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "eyeHeight"
    )
    val eyeColor by animateColorAsState(
        targetValue = if (isEyesOpen) Color(0xFF00E5FF) else Color(0xFF0A1A2F),
        animationSpec = tween(durationMillis = 200),
        label = "eyeColor"
    )

    // أنيميشن نبض القلب (عند وجود إشعار)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == EveStatus.NOTIFYING) 1.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == EveStatus.NOTIFYING) 0.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(90.dp, 110.dp) // حجم الفقاعة (مطابق لـ XML: 90x110)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable {
                isEyesOpen = !isEyesOpen
                if (isEyesOpen) {
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // هالة النبض (عند الإشعار)
        if (status == EveStatus.NOTIFYING) {
            Canvas(modifier = Modifier.fillMaxSize().scale(pulseScale)) {
                drawCircle(
                    color = Color.Red.copy(alpha = pulseAlpha),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 8f)
                )
            }
        }

        // جسم EVE (بيضاوي أبيض مع حدود)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50, 50, 15, 15)) // مطابق لـ XML: 15dp أعلى، 2dp أسفل
                .background(Color(0xFFF5F5F7)) // مطابق لـ XML: #F5F5F7
                .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(50, 50, 15, 15)),
            contentAlignment = Alignment.Center
        ) {
            // شاشة الوجه السوداء (مطابقة لـ XML)
            Box(
                modifier = Modifier
                    .width(65.dp)
                    .height(45.dp)
                    .offset(y = (-5).dp)
                    .clip(RoundedCornerShape(15.dp)) // مطابق لـ XML
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // العيون (مع أنيميشن الفتح/الإغلاق)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // العين اليسرى
                    Canvas(modifier = Modifier.size(16.dp, eyeHeight.dp)) {
                        drawRoundRect(
                            color = eyeColor,
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                    // العين اليمنى
                    Canvas(modifier = Modifier.size(16.dp, eyeHeight.dp)) {
                        drawRoundRect(
                            color = eyeColor,
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }
                }
            }
        }

        // زر الإغلاق (X)
        IconButton(
            onClick = { onClose() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Red.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "إغلاق",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
