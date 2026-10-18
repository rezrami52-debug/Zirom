package com.racha.ai.core.agent

import android.content.Context
import com.racha.ai.core.tools.ToolExecutor

class LocalAgent(private val context: Context) {
    private val toolExecutor = ToolExecutor(context)

    fun processCommand(text: String): CommandResult {
        val lower = text.lowercase().trim()

        return when {
            // أوامر التحكم بأوضاع الصوت
            lower.contains("صامت") || lower.contains("كتم") -> {
                val res = toolExecutor.execute("{\"tool\":\"set_silent\"}")
                CommandResult.Executed("🔇 لقد تم تحويل وضع الهاتف إلى الصامت بنجاح.")
            }
            lower.contains("عادي") || lower.contains("رنين") -> {
                val res = toolExecutor.execute("{\"tool\":\"set_normal\"}")
                CommandResult.Executed("🔔 لقد تم تفعيل وضع الرنين العادي بنجاح.")
            }
            lower.contains("هزاز") || lower.contains("اهتزاز") -> {
                val res = toolExecutor.execute("{\"tool\":\"set_vibrate\"}")
                CommandResult.Executed("📳 لقد تم تفعيل وضع الاهتزاز بنجاح.")
            }
            
            // أوامر الخرائط والأماكن
            lower.contains("موقع") || lower.contains("خريطه") -> {
                val res = toolExecutor.execute("{\"tool\":\"open_maps\"}")
                CommandResult.Executed("🗺️ تم فتح موقعك الحالي على الخريطة.")
            }
            lower.contains("مقهى") || lower.contains("مقاهي") || lower.contains("فندق") || lower.contains("مسجد") -> {
                val res = toolExecutor.execute("{\"tool\":\"search_places\", \"query\":\"$text\"}")
                CommandResult.Executed("📍 جاري البحث عن الأماكن القريبة منك...")
            }

            // أوامر فتح التطبيقات الشائعة محلياً بدون إنترنت
            lower.contains("فيسبوك") || lower.contains("facebook") -> {
                val res = toolExecutor.execute("{\"tool\":\"open_app\", \"package_name\":\"com.facebook.katana\"}")
                if (res.success) CommandResult.Executed("🚀 تم فتح تطبيق فيسبوك بنجاح.")
                else CommandResult.Executed(res.message)
            }
            lower.contains("واتساب") || lower.contains("whatsapp") -> {
                val res = toolExecutor.execute("{\"tool\":\"open_app\", \"package_name\":\"com.whatsapp\"}")
                if (res.success) CommandResult.Executed("🚀 تم فتح تطبيق واتساب بنجاح.")
                else CommandResult.Executed(res.message)
            }
            lower.contains("يوتيوب") || lower.contains("youtube") -> {
                val res = toolExecutor.execute("{\"tool\":\"open_app\", \"package_name\":\"com.google.android.youtube\"}")
                if (res.success) CommandResult.Executed("🚀 تم فتح تطبيق يوتيوب بنجاح.")
                else CommandResult.Executed(res.message)
            }
            lower.contains("انستغرام") || lower.contains("instagram") -> {
                val res = toolExecutor.execute("{\"tool\":\"open_app\", \"package_name\":\"com.instagram.android\"}")
                if (res.success) CommandResult.Executed("🚀 تم فتح تطبيق انستغرام بنجاح.")
                else CommandResult.Executed(res.message)
            }

            else -> CommandResult.NeedsLLM(text)
        }
    }
}
