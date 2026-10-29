package com.jrustam.neonrush.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF5CF8EF)
val Pink = Color(0xFFFF508D)
val Violet = Color(0xFFA78BFA)
val Ink = Color(0xFF080B19)
val Muted = Color(0xFF8795B3)

@Composable
fun NeonRushTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, onPrimary = Ink, secondary = Violet, tertiary = Pink,
        background = Ink, surface = Color(0xFF11182B), onSurface = Color(0xFFEAF3FF),
        onSurfaceVariant = Muted
    ), typography = Typography, content = content)
}
