package com.hiralen.temubelajar.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.Font
import temubelajar.core.generated.resources.*

// ─── Color Palette ────────────────────────────────────────────────────────────

object TBColors {
    // ── New Brand Palette ────────────────────────────────────────────────────────
    val Primary = Color(0xFFE35336)         // Burnt Orange / Coral Red
    val Secondary = Color(0xFFF4A460)       // Sandy Brown
    val Background = Color(0xFFF5F5DC)      // Beige
    val TextPrimary = Color(0xFFA0522D)     // Sienna / Dark Brown
    
    // Derived surfaces
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFFBFAF0)
    val PrimaryContainer = Color(0xFFFFEBE6)
    val PrimaryContainerDark = Color(0xFFBD3D23)

    // Status colors
    val Success = Color(0xFF2E7D32)
    val Error = Color(0xFFC62828)
    val Warning = Color(0xFFEF6C00)
    
    // Dark mode versions (adjusted for contrast)
    val BackgroundDark = Color(0xFF1A1A1A)
    val SurfaceDark = Color(0xFF2D2D2D)
    val TextPrimaryDark = Color(0xFFF5F5DC)
    
    // Cards
    val CardBg = Color(0xFFFFFFFF)
    val CardBgDark = Color(0xFF2D2D2D)
    val CardBorder = Color(0xFFE0E0E0)
    val CardBorderDark = Color(0xFF404040)

    // ── Legacy Compatibility ──────────────────────────────────────────────────
    val AccentPurple = Primary
    val AccentBlue = Secondary

    // Backward-compat aliases
    val DarkBg = BackgroundDark
    val TextSecondary = TextPrimary.copy(alpha = 0.7f)
    val TextSecondaryDark = TextPrimaryDark.copy(alpha = 0.7f)
    val TextMuted = TextPrimary.copy(alpha = 0.5f)
    val TextMutedDark = TextPrimaryDark.copy(alpha = 0.5f)
    val TextHint = TextPrimary.copy(alpha = 0.3f)
    val TextHintDark = TextPrimaryDark.copy(alpha = 0.3f)
}

private val LightColorScheme = lightColorScheme(
    background = TBColors.Background,
    surface = TBColors.Surface,
    surfaceVariant = TBColors.SurfaceVariant,
    primary = TBColors.Primary,
    primaryContainer = TBColors.PrimaryContainer,
    onPrimaryContainer = TBColors.PrimaryContainerDark,
    secondary = TBColors.Secondary,
    secondaryContainer = TBColors.Secondary.copy(alpha = 0.12f),
    error = TBColors.Error,
    onBackground = TBColors.TextPrimary,
    onSurface = TBColors.TextPrimary,
    onSurfaceVariant = TBColors.TextSecondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    outline = TBColors.CardBorder,
    outlineVariant = TBColors.TextHint
)


private val DarkColorScheme = darkColorScheme(
    background = TBColors.BackgroundDark,
    surface = TBColors.SurfaceDark,
    surfaceVariant = TBColors.SurfaceDark,
    primary = TBColors.Primary,
    primaryContainer = TBColors.PrimaryContainerDark,
    onPrimaryContainer = TBColors.Primary,
    secondary = TBColors.Secondary,
    secondaryContainer = TBColors.Secondary.copy(alpha = 0.2f),
    error = TBColors.Error,
    onBackground = TBColors.TextPrimaryDark,
    onSurface = TBColors.TextPrimaryDark,
    onSurfaceVariant = TBColors.TextPrimaryDark.copy(alpha = 0.7f),
    onPrimary = Color.White,
    onSecondary = TBColors.BackgroundDark,
    outline = TBColors.CardBorderDark,
    outlineVariant = TBColors.CardBorderDark
)

// ─── Theme ────────────────────────────────────────────────────────────────────────────

@Composable
fun TemuBelajarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
// ─── Design Tokens ───────────────────────────────────────────────────────────

object TBShapes {
    val Button = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(24.dp)
    val Input = RoundedCornerShape(16.dp)
}

object TBElevation {
    val Card = 8.dp
    val Button = 4.dp
}

object TBFonts {
    val Outfit = FontFamily.Default // Fallback to avoid resource errors for now
}
