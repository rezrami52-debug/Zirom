package com.racha.ai.core.apps

/**
 * الإجراء المعلّق (ينتظر اختيار التطبيق).
 */
data class PendingAction(
    val type: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val contactName: String? = null,
    val contactDetails: String? = null
)
