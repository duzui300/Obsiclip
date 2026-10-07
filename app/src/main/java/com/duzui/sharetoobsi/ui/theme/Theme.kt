package com.duzui.sharetoobsi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Violet = Color(0xFF6D5AE6)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9AEFF),
    onPrimary = Color(0xFF2A1E6B),
)

@Composable
fun ShareTransTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
