package com.racha.ai.theme

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream

data class CustomThemeConfig(
    val userBubbleColor: Color = Color(0xFF00A3FF),
    val rachaBubbleColor: Color = Color(0xFFFFFFFF),
    val glowColor: Color = Color(0xFF00E5FF),
    val elementsAlpha: Float = 0.95f,
    val glowIntensity: Float = 0.7f,
    val customBackgroundPath: String? = null
)

class AppThemeManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("racha_theme_prefs", Context.MODE_PRIVATE)

    private val _themeState = MutableStateFlow(loadTheme())
    val themeState: StateFlow<CustomThemeConfig> = _themeState

    private fun loadTheme(): CustomThemeConfig {
        return CustomThemeConfig(
            userBubbleColor = Color(prefs.getInt("user_bubble", Color(0xFF00A3FF).toArgb())),
            rachaBubbleColor = Color(prefs.getInt("racha_bubble", Color(0xFFFFFFFF).toArgb())),
            glowColor = Color(prefs.getInt("glow_color", Color(0xFF00E5FF).toArgb())),
            elementsAlpha = prefs.getFloat("elements_alpha", 0.95f),
            glowIntensity = prefs.getFloat("glow_intensity", 0.7f),
            customBackgroundPath = prefs.getString("bg_image_path", null)
        )
    }

    fun updateUserBubbleColor(color: Color) {
        prefs.edit().putInt("user_bubble", color.toArgb()).apply()
        _themeState.value = _themeState.value.copy(userBubbleColor = color)
    }

    fun updateRachaBubbleColor(color: Color) {
        prefs.edit().putInt("racha_bubble", color.toArgb()).apply()
        _themeState.value = _themeState.value.copy(rachaBubbleColor = color)
    }

    fun updateGlowColor(color: Color) {
        prefs.edit().putInt("glow_color", color.toArgb()).apply()
        _themeState.value = _themeState.value.copy(glowColor = color)
    }

    fun updateAlpha(alpha: Float) {
        prefs.edit().putFloat("elements_alpha", alpha).apply()
        _themeState.value = _themeState.value.copy(elementsAlpha = alpha)
    }

    fun updateGlowIntensity(intensity: Float) {
        prefs.edit().putFloat("glow_intensity", intensity).apply()
        _themeState.value = _themeState.value.copy(glowIntensity = intensity)
    }

    fun saveBackgroundImage(uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "custom_background.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
            prefs.edit().putString("bg_image_path", file.absolutePath).apply()
            _themeState.value = _themeState.value.copy(customBackgroundPath = file.absolutePath)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun clearBackgroundImage() {
        val file = File(context.filesDir, "custom_background.jpg")
        if (file.exists()) file.delete()
        prefs.edit().remove("bg_image_path").apply()
        _themeState.value = _themeState.value.copy(customBackgroundPath = null)
    }
}
