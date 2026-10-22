package com.racha.ai.core.media

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.ui.platform.ComposeView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.racha.ai.core.overlay.OverlayLifecycleOwner
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ActiveMediaWindow(
    val id: String = UUID.randomUUID().toString(),
    val player: ExoPlayer,
    val composeView: ComposeView,
    val lifecycleOwner: OverlayLifecycleOwner,
    val layoutParams: WindowManager.LayoutParams,
    var isExpanded: Boolean = false
)

class MediaWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val activeWindows = ConcurrentHashMap<String, ActiveMediaWindow>()

    @OptIn(UnstableApi::class)
    fun createMediaWindow(mediaUri: Uri, title: String) {
        val windowId = UUID.randomUUID().toString()

        val player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = true
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val offsetMultiplier = activeWindows.size
        val layoutParams = WindowManager.LayoutParams(
            dpToPx(240), dpToPx(160), layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 + (offsetMultiplier * 40)
            y = 200 + (offsetMultiplier * 40)
        }

        val lifecycleOwner = OverlayLifecycleOwner()

        val composeView = ComposeView(context).apply {
            lifecycleOwner.attachToComposeView(this)
            setContent {
                MediaPlayerContent(
                    title = title,
                    player = player,
                    onDrag = { dx, dy -> updateWindowPosition(windowId, dx, dy) },
                    onToggleResize = { toggleWindowSize(windowId) },
                    onClose = { closeWindow(windowId) }
                )
            }
        }

        val activeWindow = ActiveMediaWindow(
            id = windowId, player = player, composeView = composeView,
            lifecycleOwner = lifecycleOwner, layoutParams = layoutParams
        )

        try {
            windowManager.addView(composeView, layoutParams)
            lifecycleOwner.handleResume()
            activeWindows[windowId] = activeWindow
        } catch (e: Exception) {
            e.printStackTrace()
            player.release()
            lifecycleOwner.handleDestroy()
        }
    }

    fun updateWindowPosition(windowId: String, dx: Float, dy: Float) {
        activeWindows[windowId]?.let { window ->
            window.layoutParams.x += dx.toInt()
            window.layoutParams.y += dy.toInt()
            try { windowManager.updateViewLayout(window.composeView, window.layoutParams) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun toggleWindowSize(windowId: String) {
        activeWindows[windowId]?.let { window ->
            window.isExpanded = !window.isExpanded
            if (window.isExpanded) {
                window.layoutParams.width = dpToPx(340); window.layoutParams.height = dpToPx(240)
            } else {
                window.layoutParams.width = dpToPx(240); window.layoutParams.height = dpToPx(160)
            }
            try { windowManager.updateViewLayout(window.composeView, window.layoutParams) } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun closeWindow(windowId: String) {
        activeWindows.remove(windowId)?.let { window ->
            try {
                window.player.stop()
                window.player.release()
                window.lifecycleOwner.handleDestroy()
                windowManager.removeView(window.composeView)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun releaseAll() {
        val keys = activeWindows.keys().toList()
        for (id in keys) closeWindow(id)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
