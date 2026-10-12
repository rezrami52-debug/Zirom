package com.racha.ai.core.tools

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.json.JSONObject

class ToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "RachaToolExecutor"
    }

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
            JSONObject(trimmed).has("tool")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * هل هذه أداة تحتاج تدفقاً خاصاً (مثل الكاميرا)؟
     */
    fun isSpecialTool(text: String): Boolean {
        return try {
            val json = JSONObject(text.trim())
            val tool = json.getString("tool")
            ToolRegistry.specialTools.contains(tool)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * استخراج اسم الأداة.
     */
    fun getToolName(text: String): String? {
        return try {
            JSONObject(text.trim()).getString("tool")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * تنفيذ أداة عادية (فورية).
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

    private fun setSilent(): ToolResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
            ToolResult(true, "✅ تم تفعيل الوضع الصامت")
        } catch (e: Exception) {
            ToolResult(false, "⚠️ فشل: ${e.message}")
        }
    }

    private fun setNormal(): ToolResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            ToolResult(true, "🔔 تم تفعيل الوضع العادي")
        } catch (e: Exception) {
            ToolResult(false, "⚠️ فشل: ${e.message}")
        }
    }

    private fun setVibrate(): ToolResult {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            ToolResult(true, "📳 تم تفعيل وضع الاهتزاز")
        } catch (e: Exception) {
            ToolResult(false, "⚠️ فشل: ${e.message}")
        }
    }
}
