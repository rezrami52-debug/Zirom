package com.racha.ai.core.apps

data class PendingAction(
    val type: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val contactName: String? = null,
    val contactDetails: String? = null,
    val phoneNumber: String? = null,
    val alarmHour: Int? = null,
    val alarmMinute: Int? = null,
    val correctionText: String? = null
)
