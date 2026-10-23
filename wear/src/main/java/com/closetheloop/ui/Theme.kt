package com.closetheloop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val LoopColorScheme = ColorScheme(
    primary = LoopColors.Green,
    primaryDim = Color(0xFF36A84D),
    primaryContainer = Color(0xFF1C4A27),
    onPrimary = Color.Black,
    onPrimaryContainer = Color(0xFFB9F6C5),
)

@Composable
fun LoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LoopColorScheme, content = content)
}
