package com.racha.ai.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * تخزين آمن للبيانات الحساسة مثل API Keys.
 * يستخدم EncryptedSharedPreferences مع مفتاح رئيسي في Android Keystore.
 */
class SecurePreferences(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * حفظ API Key مشفراً.
     */
    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_GEMINI_API, apiKey).apply()
    }

    /**
     * قراءة API Key. يعيد null إذا لم يُحفظ بعد.
     */
    fun getApiKey(): String? {
        return prefs.getString(KEY_GEMINI_API, null)
    }

    /**
     * حذف API Key (للاستخدام في "مسح البيانات").
     */
    fun clearApiKey() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    /**
     * هل يوجد API Key محفوظ؟
     */
    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    companion object {
        private const val PREFS_NAME = "racha_secure_prefs"
        private const val KEY_GEMINI_API = "gemini_api_key"
    }
}
