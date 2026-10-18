package com.racha.ai.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.Calendar

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBD4CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9B59B6),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFF8E44AD),
    onSecondary = Color.White,
    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFE0E0FF),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE0E0FF),
    surfaceVariant = Color(0xFF2D2D44),
    onSurfaceVariant = Color(0xFFE0E0FF),
    tertiary = Color(0xFFE94560),
    error = Color(0xFFE94560),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A90E2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF5BA3E8),
    onSecondary = Color.White,
    background = Color(0xFF0A1929),
    onBackground = Color(0xFFE3F2FD),
    surface = Color(0xFF1E3A5F),
    onSurface = Color(0xFFE3F2FD),
    surfaceVariant = Color(0xFF1A2F4A),
    onSurfaceVariant = Color(0xFFB8D4F0),
    tertiary = Color(0xFF4FC3F7),
    error = Color(0xFFEF5350),
    onError = Color.White
)

fun isNightTime(): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return hour >= 18 || hour < 6
}

@Composable
fun RachaTheme(
    darkTheme: Boolean = isNightTime(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
