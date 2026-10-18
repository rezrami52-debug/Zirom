package com.racha.ai.core.tools

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
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

    fun isToolCall(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            JSONObject(trimmed).has("tool")
        } catch (e: Exception) {
            false
        }
    }

    fun isSpecialTool(text: String): Boolean {
        return try {
            val json = JSONObject(text.trim())
            val tool = json.getString("tool")
            tool == "open_camera"
        } catch (e: Exception) {
            false
        }
    }

    fun getToolName(text: String): String? {
        return try {
            JSONObject(text.trim()).getString("tool")
        } catch (e: Exception) {
            null
        }
    }

    fun execute(jsonText: String): ToolResult {
        return try {
            val json = JSONObject(jsonText.trim())
            val tool = json.getString("tool")

            Log.d(TAG, "تنفيذ أداة: $tool")

            when (tool) {
                "set_silent" -> setSilent()
                "set_normal" -> setNormal()
                "set_vibrate" -> setVibrate()
                "open_app" -> openApp(json.optString("package_name", ""))
                "search_places" -> searchPlaces(json.optString("query", "مساجد"))
                "open_maps" -> openMaps()
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
            ToolResult(true, "🔇 تم تحويل الهاتف للوضع الصامت")
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

    private fun openApp(packageName: String): ToolResult {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                ToolResult(true, "🚀 جاري فتح التطبيق...")
            } else {
                ToolResult(false, "⚠️ لم يتم العثور على التطبيق المطلوب")
            }
        } catch (e: Exception) {
            ToolResult(false, "⚠️ تعذر فتح التطبيق: ${e.message}")
        }
    }

    private fun searchPlaces(query: String): ToolResult {
        return try {
            val gmmIntentUri = Uri.parse("geo:35.8256,10.6369?q=" + Uri.encode(query))
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(mapIntent)
            ToolResult(true, "📍 جاري البحث عن $query القريبة منك على الخريطة...")
        } catch (e: Exception) {
            ToolResult(false, "⚠️ تعذر فتح الخرائط: ${e.message}")
        }
    }

    private fun openMaps(): ToolResult {
        return try {
            val gmmIntentUri = Uri.parse("geo:35.8256,10.6369?z=15")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(mapIntent)
            ToolResult(true, "🗺️ تم فتح موقعك الحالي على الخريطة")
        } catch (e: Exception) {
            ToolResult(false, "⚠️ فشل فتح موقعك: ${e.message}")
        }
    }
}
