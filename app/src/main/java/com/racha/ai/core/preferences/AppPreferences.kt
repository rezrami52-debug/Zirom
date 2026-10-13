package com.racha.ai.core.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppPreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "racha_app_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ===== Onboarding =====
    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun isLocationEnabled(): Boolean = prefs.getBoolean(KEY_LOCATION_ENABLED, false)
    fun setLocationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION_ENABLED, enabled).apply()
    }

    // ===== اللغة =====
    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    // ===== الأمان — محاولات الفتح =====
    fun getIntruderThreshold(): Int = prefs.getInt(KEY_INTRUDER_THRESHOLD, 5)
    fun setIntruderThreshold(value: Int) {
        prefs.edit().putInt(KEY_INTRUDER_THRESHOLD, value.coerceIn(1, 10)).apply()
    }

    fun isIntruderPhotoEnabled(): Boolean = prefs.getBoolean(KEY_INTRUDER_ENABLED, true)
    fun setIntruderPhotoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTRUDER_ENABLED, enabled).apply()
    }

    fun getIntruderRetentionDays(): Int = prefs.getInt(KEY_INTRUDER_RETENTION_DAYS, 7)
    fun setIntruderRetentionDays(days: Int) {
        prefs.edit().putInt(KEY_INTRUDER_RETENTION_DAYS, days.coerceIn(1, 30)).apply()
    }

    // ===== SleepGuardian =====
    /**
     * هل حارس النوم مفعّل؟
     */
    fun isSleepGuardianEnabled(): Boolean = prefs.getBoolean(KEY_SLEEP_GUARDIAN_ENABLED, true)
    fun setSleepGuardianEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SLEEP_GUARDIAN_ENABLED, enabled).apply()
    }

    /**
     * ساعة بدء التذكير (بعد 22:00 افتراضياً).
     */
    fun getSleepGuardianStartHour(): Int = prefs.getInt(KEY_SLEEP_GUARDIAN_START_HOUR, 22)
    fun setSleepGuardianStartHour(hour: Int) {
        prefs.edit().putInt(KEY_SLEEP_GUARDIAN_START_HOUR, hour.coerceIn(20, 23)).apply()
    }

    /**
     * هل أرسل إشعار أمس فقط؟
     * (نستخدمه لمنع التكرار في نفس الليلة)
     */
    fun getLastSleepGuardianSentDate(): Long = prefs.getLong(KEY_SLEEP_GUARDIAN_LAST_SENT, 0L)
    fun setLastSleepGuardianSentDate(date: Long) {
        prefs.edit().putLong(KEY_SLEEP_GUARDIAN_LAST_SENT, date).apply()
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_LOCATION_ENABLED = "location_enabled"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_INTRUDER_THRESHOLD = "intruder_threshold"
        private const val KEY_INTRUDER_ENABLED = "intruder_enabled"
        private const val KEY_INTRUDER_RETENTION_DAYS = "intruder_retention_days"
        private const val KEY_SLEEP_GUARDIAN_ENABLED = "sleep_guardian_enabled"
        private const val KEY_SLEEP_GUARDIAN_START_HOUR = "sleep_guardian_start_hour"
        private const val KEY_SLEEP_GUARDIAN_LAST_SENT = "sleep_guardian_last_sent"
    }
}
