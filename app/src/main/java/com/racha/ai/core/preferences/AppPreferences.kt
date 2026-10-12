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
    /**
     * عدد المحاولات الفاشلة قبل التقاط صورة.
     * الافتراضي: 5
     */
    fun getIntruderThreshold(): Int = prefs.getInt(KEY_INTRUDER_THRESHOLD, 5)
    fun setIntruderThreshold(value: Int) {
        val clamped = value.coerceIn(1, 10)
        prefs.edit().putInt(KEY_INTRUDER_THRESHOLD, clamped).apply()
    }

    /**
     * هل التقاط صورة المتسلل مفعّل؟
     */
    fun isIntruderPhotoEnabled(): Boolean = prefs.getBoolean(KEY_INTRUDER_ENABLED, true)
    fun setIntruderPhotoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTRUDER_ENABLED, enabled).apply()
    }

    /**
     * كم يوماً تُحفظ الصور قبل الحذف التلقائي؟
     * الافتراضي: 7
     */
    fun getIntruderRetentionDays(): Int = prefs.getInt(KEY_INTRUDER_RETENTION_DAYS, 7)
    fun setIntruderRetentionDays(days: Int) {
        val clamped = days.coerceIn(1, 30)
        prefs.edit().putInt(KEY_INTRUDER_RETENTION_DAYS, clamped).apply()
    }

    companion object {
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_LOCATION_ENABLED = "location_enabled"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_INTRUDER_THRESHOLD = "intruder_threshold"
        private const val KEY_INTRUDER_ENABLED = "intruder_enabled"
        private const val KEY_INTRUDER_RETENTION_DAYS = "intruder_retention_days"
    }
}
