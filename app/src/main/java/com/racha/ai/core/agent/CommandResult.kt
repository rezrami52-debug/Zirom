package com.racha.ai.core.agent

/**
 * نتيجة معالجة أمر.
 */
sealed class CommandResult {

    /**
     * أمر محلي نُفّذ بنجاح.
     */
    data class Executed(
        val message: String,
        val shouldSpeak: Boolean = true
    ) : CommandResult()

    /**
     * أمر محلي يحتاج تدفقاً خاصاً (مثل فتح نشاط).
     */
    data class NeedsAction(
        val action: String,        // "open_camera"
        val message: String
    ) : CommandResult()

    /**
     * لم يُفهم محلياً — يجب إرساله للـ LLM.
     */
    data class NeedsLLM(
        val originalInput: String
    ) : CommandResult()

    /**
     * خطأ في المعالجة.
     */
    data class Failed(
        val message: String
    ) : CommandResult()
}
