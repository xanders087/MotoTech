package com.example

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

object ThemePreferences {
    private const val PREFS_NAME = "mototech_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    fun loadThemeMode(context: Context): AppThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        val mode = runCatching { AppThemeMode.valueOf(savedName ?: AppThemeMode.SYSTEM.name) }
            .getOrDefault(AppThemeMode.SYSTEM)
        MotoThemeState.themeMode = mode
        return mode
    }

    fun saveThemeMode(context: Context, mode: AppThemeMode) {
        MotoThemeState.themeMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
    }
}

fun iconForThemeMode(mode: AppThemeMode): ImageVector = when (mode) {
    AppThemeMode.LIGHT -> Icons.Default.LightMode
    AppThemeMode.DARK -> Icons.Default.DarkMode
    AppThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
}

@Composable
fun ThemeModeTopBarButton(
    currentMode: AppThemeMode,
    onSelectMode: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    FilledTonalButton(
        onClick = { showDialog = true },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MotoSurfaceVariant,
            contentColor = MotoAccentBlue
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = modifier.testTag("theme_selector_button")
    ) {
        Icon(
            imageVector = iconForThemeMode(currentMode),
            contentDescription = "Cambiar tema de apariencia",
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = currentMode.shortLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }

    if (showDialog) {
        ThemeModeDialog(
            currentMode = currentMode,
            onDismiss = { showDialog = false },
            onSelectMode = { chosen ->
                onSelectMode(chosen)
                showDialog = false
            }
        )
    }
}

@Composable
fun ThemeModeDialog(
    currentMode: AppThemeMode,
    onDismiss: () -> Unit,
    onSelectMode: (AppThemeMode) -> Unit
) {
    val items = listOf(
        Triple(
            AppThemeMode.LIGHT,
            "Modo Claro",
            "Interfaz luminosa de alto contraste ideal para exteriores y luz de día."
        ),
        Triple(
            AppThemeMode.DARK,
            "Modo Oscuro",
            "Estética de cabina técnica oscura que reduce el cansancio visual."
        ),
        Triple(
            AppThemeMode.SYSTEM,
            "Predeterminado según sistema",
            "Se adapta automáticamente al tema claro u oscuro configurado en tu Android."
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MotoBorder, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MotoSurface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tema de Apariencia",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MotoTextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MotoTextSecondary
                        )
                    }
                }

                Text(
                    text = "Elige cómo deseas visualizar MotoTech:",
                    fontSize = 12.sp,
                    color = MotoTextSecondary
                )

                items.forEach { (mode, title, subtitle) ->
                    val selected = currentMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) MotoPrimary.copy(alpha = 0.14f) else MotoSurfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) MotoPrimary else MotoBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectMode(mode) }
                            .padding(14.dp)
                            .testTag("dialog_theme_${mode.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = iconForThemeMode(mode),
                            contentDescription = null,
                            tint = if (selected) MotoPrimary else MotoTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MotoTextPrimary
                            )
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                color = MotoTextSecondary
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = MotoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
