package com.racha.ai.core.apps

/**
 * حالة عرض حوار اختيار التطبيق.
 */
data class AppChooserState(
    val isVisible: Boolean = false,
    val title: String = "اختر تطبيقاً",
    val apps: List<AppResolver.InstalledApp> = emptyList(),
    val pendingAction: PendingAction? = null
)

/**
 * الإجراء المعلّق (ينتظر اختيار التطبيق).
 */
data class PendingAction(
    val type: String,           // "MAP", "MESSAGE", "EMAIL", "PHONE", "BROWSER"
    val lat: Double? = null,    // للخرائط
    val lon: Double? = null,
    val contactName: String? = null,  // للمراسلة
    val phoneNumber: String? = null,  // للاتصال
    val messageText: String? = null,  // للرسائل
    val url: String? = null     // للمتصفح
)
