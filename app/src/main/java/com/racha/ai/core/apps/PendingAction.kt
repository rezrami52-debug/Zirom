package com.racha.ai.core.apps

/**
 * الإجراء المعلّق (ينتظر اختيار التطبيق).
 * ملف مستقل — لا يُعرَّف في أي ملف آخر.
 */
data class PendingAction(
    val type: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val contactName: String? = null,
    val contactDetails: String? = null,
    val phoneNumber: String? = null
)
