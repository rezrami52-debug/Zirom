package com.racha.ai.core.apps

/**
 * حالة عرض حوار اختيار التطبيق.
 * ملاحظة: PendingAction معرّف في IntentHandler.kt
 */
data class AppChooserState(
    val isVisible: Boolean = false,
    val title: String = "اختر تطبيقاً",
    val apps: List<AppResolver.InstalledApp> = emptyList(),
    val pendingAction: PendingAction? = null
)
