package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MotoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00639B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFF96CBFF),
    tertiary = Color(0xFF8B6100),
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF333333)
)

private val MotoLightColorScheme = lightColorScheme(
    primary = Color(0xFFD32F2F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9EEF5),
    onPrimaryContainer = Color(0xFF14181F),
    secondary = Color(0xFF005A8D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F2FF),
    onSecondaryContainer = Color(0xFF003D66),
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF14181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFE9EEF5),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFD0D8E4)
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = MotoThemeState.themeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MotoThemeState.isDark = useDark

    MaterialTheme(
        colorScheme = if (useDark) MotoDarkColorScheme else MotoLightColorScheme,
        typography = Typography,
        content = content
    )
}
