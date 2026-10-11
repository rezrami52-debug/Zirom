package com.racha.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RachaColorScheme = lightColorScheme(
    primary = RachaPurple,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = RachaPurpleLight,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,

    secondary = RachaTeal,
    onSecondary = androidx.compose.ui.graphics.Color.White,

    tertiary = RachaAmber,
    onTertiary = androidx.compose.ui.graphics.Color.White,

    background = BackgroundLight,
    onBackground = OnBackgroundLight,

    surface = SurfaceLight,
    onSurface = OnSurfaceLight,

    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight
)

@Composable
fun RachaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RachaColorScheme,
        content = content
    )
}
