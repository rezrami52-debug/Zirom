package com.racha.ai.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * إدارة اللغة المختارة.
 */
object LocaleManager {

    const val LANG_ARABIC = "ar"
    const val LANG_ENGLISH = "en"
    const val LANG_FRENCH = "fr"

    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getDisplayName(languageCode: String): String = when (languageCode) {
        LANG_ARABIC -> "العربية"
        LANG_ENGLISH -> "English"
        LANG_FRENCH -> "Français"
        else -> languageCode
    }

    fun getSupportedLanguages(): List<Pair<String, String>> = listOf(
        LANG_ARABIC to "العربية",
        LANG_ENGLISH to "English",
        LANG_FRENCH to "Français"
    )
}
