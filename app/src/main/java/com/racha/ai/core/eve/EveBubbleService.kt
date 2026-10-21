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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        
        startForegroundWithNotification()
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
            windowManager.addView(composeView, bubbleParams)
        } catch (e: Exception) {
            e.printStackTrace()
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
            .size(90.dp) // ✅ حجم أكبر قليلاً
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onClick() }, // ✅ اللمس يفتح العيون ويوقظ EVE
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

        // ✅ خلفية الفقاعة (تتغير حسب الحالة)
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(
                    when (status) {
                        EveStatus.NOTIFYING -> Color(0xFFFF4D4D) // أحمر
                        EveStatus.AWAKE -> Color(0xFFBD4CFF) // بنفسجي
                        EveStatus.LISTENING -> Color(0xFF00E5FF) // سماوي
                        EveStatus.SPEAKING -> Color(0xFF4CAF50) // أخضر
                        else -> Color(0xFF0EA5E9) // أزرق (عادي)
                    }
                )
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // ✅ رسم الوجه (EVE)
            Canvas(modifier = Modifier.size(60.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                val cy = h / 2
                val eyeRadius = 8f
                val eyeY = cy - 8f
                val eyeX = cx - 15f

                // العين اليسرى (تتأثر بالرمش)
                drawOval(
                    color = Color.White,
                    topLeft = Offset(eyeX - eyeRadius, eyeY - eyeRadius * eyeOpenness),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * eyeOpenness)
                )
                // العين اليمنى
                drawOval(
                    color = Color.White,
                    topLeft = Offset(cx + 15f - eyeRadius, eyeY - eyeRadius * eyeOpenness),
                    size = Size(eyeRadius * 2, eyeRadius * 2 * eyeOpenness)
                )

                // البؤبؤ (يتحرك قليلاً)
                val pupilOffset = when (status) {
                    EveStatus.THINKING -> Offset(2f, -2f)
                    EveStatus.AWAKE -> Offset(0f, -3f)
                    else -> Offset(0f, 0f)
                }
                drawCircle(color = Color.Black, radius = 3f, center = Offset(eyeX + pupilOffset.x, eyeY + pupilOffset.y))
                drawCircle(color = Color.Black, radius = 3f, center = Offset(cx + 15f + pupilOffset.x, eyeY + pupilOffset.y))

                // الابتسامة (تتغير حسب الحالة)
                val smilePath = Path()
                when (status) {
                    EveStatus.SPEAKING -> {
                        // فم مفتوح (يتحدث)
                        drawOval(
                            color = Color.White,
                            topLeft = Offset(cx - 10f, cy + 10f),
                            size = Size(20f, 12f)
                        )
                    }
                    EveStatus.NOTIFYING -> {
                        // ابتسامة عريضة (حماس)
                        smilePath.moveTo(cx - 15f, cy + 10f)
                        smilePath.quadraticBezierTo(cx, cy + 25f, cx + 15f, cy + 10f)
                        drawPath(smilePath, Color.White, style = Stroke(width = 3f))
                    }
                    else -> {
                        // ابتسامة عادية
                        smilePath.moveTo(cx - 10f, cy + 12f)
                        smilePath.quadraticBezierTo(cx, cy + 18f, cx + 10f, cy + 12f)
                        drawPath(smilePath, Color.White, style = Stroke(width = 2.5f))
                    }
                }
            }
        }
    }
}
