package com.racha.ai.data.llm

import com.racha.ai.data.local.ChatMessageEntity

/**
 * واجهة موحّدة لأي مزوّد LLM (Gemini، Qwen، غيره).
 */
interface LLMProvider {

    /**
     * اسم المزوّد (للعرض).
     */
    val name: String

    /**
     * إرسال رسالة مع سياق.
     */
    suspend fun sendMessageWithContext(
        history: List<ChatMessageEntity>,
        userMessage: String,
        userProfileContext: String = ""
    ): Result<String>

    /**
     * هل المزوّد جاهز للاستخدام؟
     */
    fun isReady(): Boolean

    /**
     * تحرير الموارد (إذا لزم).
     */
    fun close() {}
}
