package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Aura Visual Redesign — Basic Light & Dark Themes
 * Free for all users. Built on Material 3 semantic color slots.
 */
private val LightColorScheme = lightColorScheme(
    primary = DiscoveryViolet,
    onPrimary = AuraCrispWhite,
    primaryContainer = AuraPurpleContainer,
    onPrimaryContainer = DiscoveryViolet,

    secondary = DiscoveryMagenta,
    onSecondary = AuraCrispWhite,
    secondaryContainer = Color(0xFFFDE8FF),
    onSecondaryContainer = DiscoveryMagenta,

    tertiary = DiscoveryHotPink,
    onTertiary = AuraCrispWhite,

    background = AuraCrispWhite,
    onBackground = AuraMidnight,

    surface = AuraSubtleSurface,
    onSurface = AuraMidnight,

    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = AuraMutedSlate,

    outline = AuraSubtleBorder,
    outlineVariant = Color(0xFFF3F4F6)
)

private val DarkColorScheme = darkColorScheme(
    primary = DiscoveryViolet,
    onPrimary = AuraCrispWhite,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFE9D5FF),

    secondary = DiscoveryMagenta,
    onSecondary = AuraCrispWhite,
    secondaryContainer = Color(0xFF831843),
    onSecondaryContainer = Color(0xFFFCE7F3),

    tertiary = DiscoveryHotPink,
    onTertiary = AuraCrispWhite,

    background = AuraDarkBackground,
    onBackground = AuraDarkOnSurface,

    surface = AuraDarkSurface,
    onSurface = AuraDarkOnSurface,

    surfaceVariant = AuraDarkSurfaceVariant,
    onSurfaceVariant = AuraDarkOnSurfaceVariant,

    outline = AuraDarkBorder,
    outlineVariant = AuraDarkSubtleBorder
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            val activity = context as? Activity
                ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()

                val insetsController = WindowCompat.getInsetsController(window, view)
                // Light icons on dark theme, dark icons on light theme
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
