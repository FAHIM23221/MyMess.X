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
    surface = Color(0xFF17261D)
)

@Composable
fun MessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
