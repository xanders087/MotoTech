package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val label: String, val shortLabel: String) {
    SYSTEM("Predeterminado según sistema", "Sistema"),
    LIGHT("Modo Claro", "Claro"),
    DARK("Modo Oscuro", "Oscuro")
}

object MotoThemeState {
    var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
    var isDark by mutableStateOf(true)
}

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// MotoTech Technical Cockpit Design System Colors (Adaptive Light & Dark)
val MotoBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFF121212) else Color(0xFFF3F5F9)

val MotoSurface: Color
    get() = if (MotoThemeState.isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)

val MotoSurfaceVariant: Color
    get() = if (MotoThemeState.isDark) Color(0xFF262626) else Color(0xFFE9EEF5)

val MotoBorder: Color
    get() = if (MotoThemeState.isDark) Color(0xFF333333) else Color(0xFFD0D8E4)

val MotoPrimary: Color
    get() = if (MotoThemeState.isDark) Color(0xFFE53935) else Color(0xFFD32F2F)

val MotoPrimaryHover: Color
    get() = if (MotoThemeState.isDark) Color(0xFFFF544C) else Color(0xFFE53935)

val MotoAccentCyan: Color
    get() = if (MotoThemeState.isDark) Color(0xFF00639B) else Color(0xFF005A8D)

val MotoAccentBlue: Color
    get() = if (MotoThemeState.isDark) Color(0xFF96CBFF) else Color(0xFF0B57D0)

val MotoTextPrimary: Color
    get() = if (MotoThemeState.isDark) Color(0xFFFFFFFF) else Color(0xFF14181F)

val MotoTextSecondary: Color
    get() = if (MotoThemeState.isDark) Color(0xFFAAAAAA) else Color(0xFF4A5568)

val MotoTextMuted: Color
    get() = if (MotoThemeState.isDark) Color(0xFF747575) else Color(0xFF718096)

// Technical Telemetry & Diagnostics Alert Palettes
val InfoBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFF003355) else Color(0xFFE6F2FF)

val InfoText: Color
    get() = if (MotoThemeState.isDark) Color(0xFFCCE4FF) else Color(0xFF003D66)

val InfoIconBg: Color
    get() = Color(0xFF00639B)

val InfoBorder: Color
    get() = if (MotoThemeState.isDark) Color(0xFF004477) else Color(0xFF99C8FF)

val UrgentBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFF551111) else Color(0xFFFFEBEB)

val UrgentText: Color
    get() = if (MotoThemeState.isDark) Color(0xFFFFCCCC) else Color(0xFF9B1C1C)

val UrgentIconBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFFB3261E) else Color(0xFFC81E1E)

val UrgentBorder: Color
    get() = if (MotoThemeState.isDark) Color(0xFF771111) else Color(0xFFF8B4B4)

val WarningBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFF553300) else Color(0xFFFFF6E0)

val WarningText: Color
    get() = if (MotoThemeState.isDark) Color(0xFFFFEECC) else Color(0xFF723B13)

val WarningIconBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFF8B6100) else Color(0xFFB45309)

val WarningBorder: Color
    get() = if (MotoThemeState.isDark) Color(0xFF774400) else Color(0xFFFCD34D)

val RoutineBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFF003311) else Color(0xFFE8F8EE)

val RoutineText: Color
    get() = if (MotoThemeState.isDark) Color(0xFFCCFFCC) else Color(0xFF0F5132)

val RoutineIconBg: Color
    get() = if (MotoThemeState.isDark) Color(0xFF1B6D32) else Color(0xFF198754)

val RoutineBorder: Color
    get() = if (MotoThemeState.isDark) Color(0xFF005522) else Color(0xFFA3E4BE)
