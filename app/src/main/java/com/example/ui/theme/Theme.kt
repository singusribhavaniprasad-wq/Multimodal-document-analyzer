package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObsidianColorScheme = darkColorScheme(
    primary = CosmicPurple,
    onPrimary = Color.White,
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    tertiary = LaserPink,
    background = ObsidianMain,
    surface = ObsidianSurface,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark theme for futuristic HUD aesthetics
  dynamicColor: Boolean = false, // Enforce unified design branding
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = ObsidianColorScheme, typography = Typography, content = content)
}
