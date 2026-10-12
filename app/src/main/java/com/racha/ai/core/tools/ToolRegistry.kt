package com.racha.ai.core.tools

/**
 * تعريف الأدوات المتاحة لـ Racha.
 */
object ToolRegistry {

    val availableTools = listOf(
        "set_silent",
        "set_normal",
        "set_vibrate",
        "open_camera"
    )

    /**
     * الأدوات الحساسة (تحتاج تدفق خاص).
     */
    val specialTools = setOf("open_camera")

    fun getToolsPrompt(): String = """
        [الأدوات المتاحة]
        
        عندما يطلب المستخدم أحد هذه الإجراءات، أجب بـ JSON فقط (بدون أي نص إضافي):
        
        1. للوضع الصامت:
           {"tool": "set_silent", "params": {}}
        
        2. للوضع العادي (مع صوت):
           {"tool": "set_normal", "params": {}}
        
        3. لوضع الاهتزاز:
           {"tool": "set_vibrate", "params": {}}
        
        4. لفتح الكاميرا أو التقاط صورة:
           {"tool": "open_camera", "params": {}}
        
        [قواعد مهمة]
        - إذا كان الطلب سؤالاً (مثل "لماذا الهاتف صامت؟")، أجب بالعربية طبيعياً ولا تستخدم JSON.
        - إذا كان الطلب أمراً مباشراً (مثل "حولي للصامت")، أجب بـ JSON فقط.
        - لا تخلط بين JSON والنص العادي.
        - استخدم JSON فقط عند اليقين أن المستخدم يريد إجراءً.
    """.trimIndent()
}
