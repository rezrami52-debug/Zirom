package com.racha.ai.core.agent

import android.content.Context
import com.racha.ai.core.apps.AppResolver

/**
 * ✅ CommandResult كـ top-level sealed class (كما كان في الأصل)
 * لضمان التوافق مع ChatViewModel.kt وأي ملفات أخرى تستخدمه
 */
sealed class CommandResult {
    data class Executed(val message: String) : CommandResult()
    data class NeedsAction(val action: String) : CommandResult()
    data class Failed(val message: String) : CommandResult()
    object NeedsLLM : CommandResult()
}

/**
 * الوكيل المحلي: يتعامل مع الأوامر البسيطة والمباشرة.
 * إذا لم يفهم الأمر، يعيد NeedsLLM ليتم معالجته بواسطة IntentHandler أو LLM.
 */
class LocalAgent(private val context: Context) {

    private val appResolver = AppResolver(context)

    fun processCommand(text: String): CommandResult {
        val lower = text.lowercase().trim()

        // ✅ السماح لـ IntentHandler بالتعامل مع جميع أوامر فتح التطبيقات
        if (lower.contains("افتح") || lower.contains("شغل") || lower.contains("فيسبوك") || 
            lower.contains("انستغرام") || lower.contains("واتساب") || lower.contains("يوتيوب") ||
            lower.contains("facebook") || lower.contains("instagram") || lower.contains("whatsapp") ||
            lower.contains("lite") || lower.contains("appgallery") || lower.contains("snapchat") ||
            lower.contains("tiktok") || lower.contains("twitter") || lower.contains("telegram")) {
            return CommandResult.NeedsLLM // سيتم معالجته بواسطة IntentHandler
        }

        // أوامر محلية بسيطة يمكن إضافتها هنا مستقبلاً
        return CommandResult.NeedsLLM
    }
}
