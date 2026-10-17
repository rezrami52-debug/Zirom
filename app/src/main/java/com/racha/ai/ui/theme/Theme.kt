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
import androidx.compose.ui.graphics.Color // ✅ هذا هو الإصلاح الحاسم
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 🌸 ثيم النهار (ألوان الورود)
private val DayColorScheme = lightColorScheme(
    primary = RosePurplePrimary,
    onPrimary = Color.White,
    primaryContainer = LavenderLight,
    onPrimaryContainer = Color.Black,
    secondary = LavenderPrimary,
    onSecondary = Color.White,
    secondaryContainer = LavenderLight,
    onSecondaryContainer = Color.Black,
    tertiary = RosePurpleDark,
    onTertiary = Color.White,
    background = Color(0xFFFFFBFF),
    onBackground = Color.Black,
    surface = Color(0xFFFFF9FF),
    onSurface = Color.Black,
    surfaceVariant = LavenderLight,
    onSurfaceVariant = Color.Black,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

// 🌳 ثيم الليل (ألوان الأشجار)
private val NightColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = Color.White,
    primaryContainer = DeepPurpleNight,
    onPrimaryContainer = Color.White,
    secondary = NightSecondary,
    onSecondary = Color.White,
    secondaryContainer = MidnightPurple,
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF9B7FD9),
    onTertiary = Color.White,
    background = Color(0xFF1A1230),
    onBackground = Color.White,
    surface = Color(0xFF241840),
    onSurface = Color.White,
    surfaceVariant = NightSurface,
    onSurfaceVariant = Color.White,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun RachaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NightColorScheme
        else -> DayColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
