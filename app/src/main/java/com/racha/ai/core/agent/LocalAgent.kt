package com.racha.ai.core.agent

import android.content.Context

/**
 * الوكيل المحلي: يتعامل مع الأوامر البسيطة والمباشرة.
 * إذا لم يفهم الأمر، يعيد NeedsLLM ليتم معالجته بواسطة IntentHandler أو LLM.
 */
class LocalAgent(private val context: Context) {

    fun processCommand(text: String): CommandResult {
        val lower = text.lowercase().trim()

        // ✅ السماح لـ IntentHandler بالتعامل مع جميع أوامر فتح التطبيقات
        // حتى لا يعترضها LocalAgent ويرفضها
        if (lower.contains("افتح") || lower.contains("شغل") || lower.contains("فيسبوك") || 
            lower.contains("انستغرام") || lower.contains("واتساب") || lower.contains("يوتيوب") ||
            lower.contains("facebook") || lower.contains("instagram") || lower.contains("whatsapp")) {
            return CommandResult.NeedsLLM // سيتم معالجته بواسطة IntentHandler بنجاح
        }

        // حالياً نترك كل شيء للذكاء الاصطناعي و IntentHandler لضمان المرونة وعدم التعطل
        return CommandResult.NeedsLLM
    }
}
