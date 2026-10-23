package com.closetheloop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PhoneColors = darkColorScheme(
    primary = LoopColors.Green,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1C4A27),
    onPrimaryContainer = Color(0xFFB9F6C5),
    background = Color.Black,
    surface = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    error = LoopColors.Red,
)

/** Always dark, matching the watch. */
@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PhoneColors, content = content)
}
