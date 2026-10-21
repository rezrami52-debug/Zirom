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

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

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

    fun isSleepGuardianEnabled(): Boolean = prefs.getBoolean(KEY_SLEEP_GUARDIAN_ENABLED, true)
    fun setSleepGuardianEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SLEEP_GUARDIAN_ENABLED, enabled).apply()
    }

    fun getSleepGuardianStartHour(): Int = prefs.getInt(KEY_SLEEP_GUARDIAN_START_HOUR, 22)
    fun setSleepGuardianStartHour(hour: Int) {
        prefs.edit().putInt(KEY_SLEEP_GUARDIAN_START_HOUR, hour.coerceIn(20, 23)).apply()
    }

    fun getLastSleepGuardianSentDate(): Long = prefs.getLong(KEY_SLEEP_GUARDIAN_LAST_SENT, 0L)
    fun setLastSleepGuardianSentDate(date: Long) {
        prefs.edit().putLong(KEY_SLEEP_GUARDIAN_LAST_SENT, date).apply()
    }

    // ===== LLM Provider =====
    /**
     * مزوّد LLM المختار:
     * - "gemini": Gemini (سحابي)
     * - "qwen": Qwen (محلي)
     */
    fun getLLMProvider(): String = prefs.getString(KEY_LLM_PROVIDER, "gemini") ?: "gemini"
    fun setLLMProvider(provider: String) {
        prefs.edit().putString(KEY_LLM_PROVIDER, provider).apply()
    }


    // ===== التخصيص (Customization) =====
    fun getUserBubbleColor(): Int = prefs.getInt(KEY_USER_BUBBLE_COLOR, 0xFF0EA5E9.toInt())
    fun setUserBubbleColor(color: Int) { prefs.edit().putInt(KEY_USER_BUBBLE_COLOR, color).apply() }

    fun getAiBubbleColor(): Int = prefs.getInt(KEY_AI_BUBBLE_COLOR, 0xFFFFFFFF.toInt())
    fun setAiBubbleColor(color: Int) { prefs.edit().putInt(KEY_AI_BUBBLE_COLOR, color).apply() }

    fun getPrimaryGlowColor(): Int = prefs.getInt(KEY_PRIMARY_GLOW, 0xFF0EA5E9.toInt())
    fun setPrimaryGlowColor(color: Int) { prefs.edit().putInt(KEY_PRIMARY_GLOW, color).apply() }

    fun getGlassAlpha(): Float = prefs.getFloat(KEY_GLASS_ALPHA, 0.7f)
    fun setGlassAlpha(alpha: Float) { prefs.edit().putFloat(KEY_GLASS_ALPHA, alpha.coerceIn(0.1f, 1f)).apply() }

    fun getGlowIntensity(): Float = prefs.getFloat(KEY_GLOW_INTENSITY, 1f)
    fun setGlowIntensity(intensity: Float) { prefs.edit().putFloat(KEY_GLOW_INTENSITY, intensity.coerceIn(0f, 2f)).apply() }

    fun getBackgroundImagePath(): String? = prefs.getString(KEY_BG_IMAGE_PATH, null)
    fun setBackgroundImagePath(path: String?) { prefs.edit().putString(KEY_BG_IMAGE_PATH, path).apply() }

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
        private const val KEY_LLM_PROVIDER = "llm_provider"

        private const val KEY_USER_BUBBLE_COLOR = "user_bubble_color"
        private const val KEY_AI_BUBBLE_COLOR = "ai_bubble_color"
        private const val KEY_PRIMARY_GLOW = "primary_glow"
        private const val KEY_GLASS_ALPHA = "glass_alpha"
        private const val KEY_GLOW_INTENSITY = "glow_intensity"
        private const val KEY_BG_IMAGE_PATH = "bg_image_path"
    }
}
