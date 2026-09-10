package com.easton.saltbubble.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Night,
    primaryContainer = CyanDim,
    onPrimaryContainer = CyanSoft,
    secondary = CyanSoft,
    onSecondary = Night,
    background = Night,
    onBackground = Ink,
    surface = NightElevated,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = InkMuted,
    outline = Stroke,
    error = Danger,
    onError = Night,
)

@Composable
fun SaltBubbleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = SaltTypography,
        content = content,
    )
}
