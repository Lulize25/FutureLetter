package com.mydiary.futureletter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = WarmPrimary,
    onPrimary = WarmOnPrimary,
    primaryContainer = WarmPrimaryContainer,
    onPrimaryContainer = WarmOnPrimaryContainer,
    secondary = WarmSecondary,
    onSecondary = WarmOnSecondary,
    secondaryContainer = WarmSecondaryContainer,
    onSecondaryContainer = WarmOnSecondaryContainer,
    tertiary = WarmTertiary,
    onTertiary = WarmOnTertiary,
    tertiaryContainer = WarmTertiaryContainer,
    onTertiaryContainer = WarmOnTertiaryContainer,
    background = WarmBackground,
    onBackground = WarmOnBackground,
    surface = WarmSurface,
    onSurface = WarmOnSurface,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = WarmOnSurfaceVariant,
    outline = WarmOutline
)

private val DarkColors = darkColorScheme(
    primary = WarmDarkPrimary,
    onPrimary = WarmDarkOnPrimary,
    primaryContainer = WarmDarkPrimaryContainer,
    onPrimaryContainer = WarmDarkOnPrimaryContainer,
    secondary = WarmDarkSecondary,
    onSecondary = WarmDarkOnSecondary,
    secondaryContainer = WarmDarkSecondaryContainer,
    onSecondaryContainer = WarmDarkOnSecondaryContainer,
    tertiary = WarmDarkTertiary,
    onTertiary = WarmDarkOnTertiary,
    tertiaryContainer = WarmDarkTertiaryContainer,
    onTertiaryContainer = WarmDarkOnTertiaryContainer,
    background = WarmDarkBackground,
    onBackground = WarmDarkOnBackground,
    surface = WarmDarkSurface,
    onSurface = WarmDarkOnSurface,
    surfaceVariant = WarmDarkSurfaceVariant,
    onSurfaceVariant = WarmDarkOnSurfaceVariant,
    outline = WarmDarkOutline
)

@Composable
fun FutureLetterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
