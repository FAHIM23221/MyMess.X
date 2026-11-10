package com.messerp.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F7A4D),
    onPrimary = Color.White,
    background = Color(0xFFF4F7F3),
    surface = Color.White
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4CC38A),
    onPrimary = Color(0xFF00210F),
    background = Color(0xFF0F1A14),
    surface = Color(0xFF17261D),
    onBackground = Color(0xFFE3EDE6),
    onSurface = Color(0xFFE3EDE6),
    primaryContainer = Color(0xFF1F4D36),
    onPrimaryContainer = Color(0xFFBFF0D5),
    surfaceVariant = Color(0xFF22352A),
    onSurfaceVariant = Color(0xFFB6C9BD),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5A1F1F),
    onErrorContainer = Color(0xFFFFDAD6)
)

/** mode: "SYSTEM" follows the phone, "LIGHT" and "DARK" force one look. */
@Composable
fun MessTheme(mode: String = "SYSTEM", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
