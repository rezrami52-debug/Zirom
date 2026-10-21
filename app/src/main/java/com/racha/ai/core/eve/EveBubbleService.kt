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
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
        android.util.Log.d("EveBubble", "onCreate: Service started")
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        
        startForegroundWithNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        android.util.Log.d("EveBubble", "windowManager initialized")
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
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    },
                    onDrag = { dx, dy ->
                        bubbleParams?.let { params ->
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            bubbleView?.let { windowManager.updateViewLayout(it, params) }
                        }
                    }
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
fun EveBubbleContent(onClick: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val status by EveState.status.collectAsState()
    
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

    // أنيميشن الرمش (العيون)
    val blinkTransition = rememberInfiniteTransition(label = "blink")
    val eyeOpenness by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                1f at 0
                1f at 3900
                0.1f at 3950
                1f at 4000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "eyeOpenness"
    )

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .size(90.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // ✅ هالة خارجية (تتوهج عند الإشعار)
        if (status == EveStatus.NOTIFYING) {
            Canvas(modifier = Modifier.fillMaxSize().scale(pulseScale)) {
                drawCircle(
                    color = Color.Red.copy(alpha = pulseAlpha),
                    radius = size.minDimension / 2,
                    style = Stroke(width = 8f)
                )
            }
        }

        // ✅ جسم EVE (بيضاوي أبيض)
        Box(
            modifier = Modifier
                .width(70.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(50, 50, 35, 35)) // شكل بيضاوي مع قاع مسطح
                .background(
                    when (status) {
                        EveStatus.NOTIFYING -> Color(0xFFFF4D4D)
                        EveStatus.AWAKE -> Color(0xFFBD4CFF)
                        EveStatus.LISTENING -> Color(0xFF00E5FF)
                        EveStatus.SPEAKING -> Color(0xFF4CAF50)
                        else -> Color.White
                    }
                )
                .border(2.dp, if (status == EveStatus.IDLE) Color(0xFF0EA5E9) else Color.White.copy(alpha = 0.5f), RoundedCornerShape(50, 50, 35, 35)),
            contentAlignment = Alignment.Center
        ) {
            // ✅ رسم الوجه (EVE)
            Canvas(modifier = Modifier.size(60.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                val cy = h / 2
                val eyeRadius = 7f
                val eyeY = cy - 10f
                val eyeX = cx - 14f
                
                // لون العيون: أزرق متوهج (إذا كانت نشطة) أو أسود (إذا كانت نائمة)
                val eyeColor = when (status) {
                    EveStatus.IDLE -> Color.Black
                    EveStatus.NOTIFYING -> Color.White
                    else -> Color(0xFF00E5FF)
                }

                // العين اليسرى
                drawOval(
                    color = eyeColor,
                    topLeft = Offset(eyeX - eyeRadius, eyeY - eyeRadius * eyeOpenness),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * eyeOpenness)
                )
                // العين اليمنى
                drawOval(
                    color = eyeColor,
                    topLeft = Offset(cx + 14f - eyeRadius, eyeY - eyeRadius * eyeOpenness),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * eyeOpenness)
                )

                // ابتسامة رقيقة
                val smilePath = Path()
                smilePath.moveTo(cx - 12f, cy + 15f)
                smilePath.quadraticBezierTo(cx, cy + 22f, cx + 12f, cy + 15f)
                drawPath(
                    smilePath,
                    color = if (status == EveStatus.IDLE) Color.Black else Color(0xFF00E5FF),
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}
