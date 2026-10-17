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
        if (lower.contains("افتح") || lower.contains("شغل") || lower.contains("فيسبوك") || 
            lower.contains("انستغرام") || lower.contains("واتساب") || lower.contains("يوتيوب") ||
            lower.contains("facebook") || lower.contains("instagram") || lower.contains("whatsapp")) {
            
            // ✅ تمرير النص الأصلي كمعامل مطلوب حسب تعريف مشروعك
            return CommandResult.NeedsLLM(text) 
        }

        // ✅ تمرير النص الأصلي كمعامل مطلوب هنا أيضاً
        return CommandResult.NeedsLLM(text)
    }
}
