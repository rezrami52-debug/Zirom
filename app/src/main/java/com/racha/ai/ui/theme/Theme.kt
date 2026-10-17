package com.racha.ai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ✅ الوضع الليلي - بنفسجي نيون
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
    tertiary = Color(0xFFE94560)
)

// الوضع النهاري (للاحتفاظ به)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A90E2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7BB3E8),
    onPrimaryContainer = Color(0xFF1A3A52),
    secondary = Color(0xFF7BB3E8),
    onSecondary = Color.White,
    background = Color(0xFFE8F4F8),
    onBackground = Color(0xFF1A3A52),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A3A52),
    surfaceVariant = Color(0xFFE8F4F8),
    onSurfaceVariant = Color(0xFF1A3A52)
)

@Composable
fun RachaTheme(
    darkTheme: Boolean = true, // ✅ افتراضياً الوضع الليلي
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color(0xFF0A0A0F).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
