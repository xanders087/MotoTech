package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MotoDarkColorScheme = darkColorScheme(
    primary = MotoPrimary,
    onPrimary = Color.White,
    primaryContainer = MotoSurfaceVariant,
    onPrimaryContainer = MotoTextPrimary,
    secondary = MotoAccentCyan,
    onSecondary = Color.Black,
    secondaryContainer = MotoSurfaceVariant,
    onSecondaryContainer = MotoAccentCyan,
    tertiary = WarningIconBg,
    background = MotoBg,
    onBackground = MotoTextPrimary,
    surface = MotoSurface,
    onSurface = MotoTextPrimary,
    surfaceVariant = MotoSurfaceVariant,
    onSurfaceVariant = MotoTextSecondary,
    outline = MotoBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MotoDarkColorScheme,
        typography = Typography,
        content = content
    )
}
