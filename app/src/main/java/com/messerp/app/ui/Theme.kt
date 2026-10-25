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
    primaryContainer = Color(0xFFDFF3E6),
    onPrimaryContainer = Color(0xFF14532D),
    secondary = Color(0xFF2563A8),
    background = Color(0xFFF4F7F3),
    onBackground = Color(0xFF12261C),
    surface = Color.White,
    onSurface = Color(0xFF12261C),
    surfaceVariant = Color(0xFFE6EFE9),
    onSurfaceVariant = Color(0xFF4B5B52),
    outline = Color(0xFF9DB0A5),
    outlineVariant = Color(0xFFDDE6DF),
    error = Color(0xFFB42318)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4CC38A),
    onPrimary = Color(0xFF00210F),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFBDF0D3),
    secondary = Color(0xFF8DB8E8),
    background = Color(0xFF0F1A14),
    onBackground = Color(0xFFE6F0EA),
    surface = Color(0xFF17261D),
    onSurface = Color(0xFFE6F0EA),
    surfaceVariant = Color(0xFF22362A),
    onSurfaceVariant = Color(0xFFA9BCB0),
    outline = Color(0xFF5E7A6A),
    outlineVariant = Color(0xFF2A3D32),
    error = Color(0xFFFF8A80)
)

@Composable
fun MessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
