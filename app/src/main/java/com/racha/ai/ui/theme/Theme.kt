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

private val DayColorScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = Color.White,
    primaryContainer = DaySecondary,
    onPrimaryContainer = DayOnSurface,
    secondary = DaySecondary,
    onSecondary = Color.White,
    background = DayBackgroundStart,
    onBackground = DayOnSurface,
    surface = DaySurface,
    onSurface = DayOnSurface,
    surfaceVariant = DayBackgroundEnd,
    onSurfaceVariant = DayOnSurface
)

private val NightColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = Color.White,
    primaryContainer = NightSecondary,
    onPrimaryContainer = NightOnSurface,
    secondary = NightSecondary,
    onSecondary = Color.White,
    background = NightBackgroundStart,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightBackgroundEnd,
    onSurfaceVariant = NightOnSurface
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
