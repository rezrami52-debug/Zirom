package com.racha.ai.core.agent

import android.content.Context
import com.racha.ai.core.apps.AppResolver
import com.racha.ai.core.apps.ArabicAppNames

/**
 * الوكيل المحلي: يتعامل مع الأوامر البسيطة والمباشرة التي لا تحتاج لذكاء اصطناعي.
 * إذا لم يفهم الأمر، يعيد NeedsLLM ليتم معالجته بواسطة IntentHandler أو LLM.
 */
class LocalAgent(private val context: Context) {

    private val appResolver = AppResolver(context)

    sealed class CommandResult {
        data class Executed(val message: String) : CommandResult()
        data class NeedsAction(val action: String) : CommandResult()
        data class Failed(val message: String) : CommandResult()
        object NeedsLLM : CommandResult()
    }

    fun processCommand(text: String): CommandResult {
        val lower = text.lowercase().trim()

        // ✅ السماح لـ IntentHandler بالتعامل مع جميع أوامر فتح التطبيقات
        // حتى لا يعترضها LocalAgent ويرفضها
        if (lower.contains("افتح") || lower.contains("شغل") || lower.contains("فيسبوك") || 
            lower.contains("انستغرام") || lower.contains("واتساب") || lower.contains("يوتيوب") ||
            lower.contains("facebook") || lower.contains("instagram") || lower.contains("whatsapp")) {
            return CommandResult.NeedsLLM // سيتم معالجته بواسطة IntentHandler
        }

        // أوامر محلية بسيطة يمكن إضافتها هنا مستقبلاً
        // حالياً نترك كل شيء للذكاء الاصطناعي و IntentHandler لضمان المرونة

        return CommandResult.NeedsLLM
    }
}
