package com.racha.ai.core.tools

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.json.JSONObject

/**
 * ينفذ الأدوات المطلوبة من Gemini.
 */
class ToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "RachaToolExecutor"
    }

    /**
     * البيانات الراجعة من تنفيذ أداة.
     */
    data class ToolResult(
        val success: Boolean,
        val message: String
    )

    /**
     * هل هذا النص JSON لأداة؟
     */
    fun isToolCall(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val json = JSONObject(trimmed)
            json.has("tool")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * تنفيذ أداة من JSON.
     */
    fun execute(jsonText: String): ToolResult {
        return try {
            val json = JSONObject(jsonText.trim())
            val tool = json.getString("tool")

            Log.d(TAG, "تنفيذ أداة: $tool")

            when (tool) {
                "set_silent" -> setSilent()
                "set_normal" -> setNormal()
                "set_vibrate" -> setVibrate()
                else -> ToolResult(false, "⚠️ أداة غير معروفة: $tool")
            }
        } catch (e: Exception) {
            Log.e(TAG, "فشل تنفيذ الأداة", e)
            ToolResult(false, "⚠️ فشل تنفيذ الأمر: ${e.message}")
        }
    }

    /**
     * الوضع الصامت.
     */
    private fun setSilent(): ToolResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            ToolResult(true, "✅ تم تفعيل الوضع الصامت")
        } catch (e: Exception) {
            Log.e(TAG, "فشل تفعيل الصامت", e)
            ToolResult(false, "⚠️ فشل تفعيل الصامت: ${e.message}")
        }
    }

    /**
     * الوضع العادي (مع صوت).
     */
    private fun setNormal(): ToolResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            ToolResult(true, "🔔 تم تفعيل الوضع العادي (مع صوت)")
        } catch (e: Exception) {
            Log.e(TAG, "فشل تفعيل العادي", e)
            ToolResult(false, "⚠️ فشل تفعيل العادي: ${e.message}")
        }
    }

    /**
     * وضع الاهتزاز.
     */
    private fun setVibrate(): ToolResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            ToolResult(true, "📳 تم تفعيل وضع الاهتزاز")
        } catch (e: Exception) {
            Log.e(TAG, "فشل تفعيل الاهتزاز", e)
            ToolResult(false, "⚠️ فشل تفعيل الاهتزاز: ${e.message}")
        }
    }
}
