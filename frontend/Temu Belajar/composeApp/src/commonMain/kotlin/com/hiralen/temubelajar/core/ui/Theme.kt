package com.hiralen.temubelajar.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import temubelajar.composeapp.generated.resources.Res
import temubelajar.composeapp.generated.resources.outfit_bold
import temubelajar.composeapp.generated.resources.outfit_medium
import temubelajar.composeapp.generated.resources.outfit_regular
import temubelajar.composeapp.generated.resources.outfit_semibold

/**
 * Linear-flavored dark design system for TemuBelajar.
 *
 * Canvas is #010102 (deep near-black with blue tint). A four-step surface ladder
 * lifts panels off the canvas. A single lavender-blue accent (#5e6ad2) is used
 * scarcely on the primary CTA, focus ring, and the brand mark. There is no
 * second chromatic color on this surface — section depth is carried by surface
 * ladder + hairline borders, not gradients or shadows.
 *
 * Spec mirror: design-system tokens map directly to Compose values:
 *   colors.canvas #010102 · colors.surface-1 #0d0e11 · colors.surface-2 #16171a
 *   colors.surface-3 #1f2023 · colors.surface-4 #26272a
 *   colors.hairline #23252a · colors.hairline-strong #313438 · colors.hairline-tertiary #1b1d1f
 *   colors.primary #5e6ad2 · colors.primary-hover #828fff · colors.primary-focus #5e69d1
 *   colors.brand-secure #7a7fad · colors.semantic-success #27a644
 *   colors.ink #f7f8f8 · ink-muted #d0d6e0 · ink-subtle #8a8f98 · ink-tertiary #62666d
 */
object LinearColors {
    val Canvas            = Color(0xFF010102)
    val Surface1          = Color(0xFF0D0E11)
    val Surface2          = Color(0xFF16171A)
    val Surface3          = Color(0xFF1F2023)
    val Surface4          = Color(0xFF26272A)

    val Hairline          = Color(0xFF23252A)
    val HairlineStrong    = Color(0xFF313438)
    val HairlineTertiary  = Color(0xFF1B1D1F)

    val Primary           = Color(0xFF5E6AD2)   // Lavender-blue brand accent
    val PrimaryHover      = Color(0xFF828FFF)  // Hover of primary CTA
    val PrimaryFocus      = Color(0xFF5E69D1)  // Focus ring tint
    val BrandSecure       = Color(0xFF7A7FAD)  // Muted lavender-gray

    val InverseCanvas     = Color(0xFFFFFFFF)

    val Ink               = Color(0xFFF7F8F8)  // Headlines, emphasized body
    val InkMuted          = Color(0xFFD0D6E0)  // Secondary body, meta
    val InkSubtle         = Color(0xFF8A8F98)  // Deselected tabs, footer cols
    val InkTertiary       = Color(0xFF62666D)  // Disabled, footnotes

    val Success           = Color(0xFF27A644)  // The only semantic color
    val Overlay           = Color(0xFF000000)  // Modal scrim

    // UX-only semantic tints (not on marketing canvas). Linear uses these only
    // inside product UI for validation feedback.
    val Error             = Color(0xFFE5484D)

    /**
     * Linear single-accent alias. Linear carries depth via surface ladder +
     * hairlines rather than multiple chromatic colours, so the lavender
     * `Primary` is the one accent used throughout the product UI (OmeTV-style
     * "Next" CTA, focus rings, brand mark). Mirrors the `TBColors.AccentPurple`
     * back-compat alias and gives screens a self-documenting call site:
     * `LinearColors.Accent` reads as "the design-system accent" — intent over
     * hue. Phase 5.2 / 5.9 reference this token.
     */
    val Accent            = Primary
}

private val LinearDarkColorScheme = darkColorScheme(
    primary = LinearColors.Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LinearColors.PrimaryFocus,
    onPrimaryContainer = LinearColors.Ink,
    secondary = LinearColors.Ink,
    onSecondary = LinearColors.Canvas,
    tertiary = LinearColors.BrandSecure,
    background = LinearColors.Canvas,
    onBackground = LinearColors.Ink,
    surface = LinearColors.Surface1,
    onSurface = LinearColors.Ink,
    surfaceVariant = LinearColors.Surface2,
    onSurfaceVariant = LinearColors.InkMuted,
    surfaceTint = LinearColors.Primary,
    inverseSurface = LinearColors.Ink,
    inverseOnSurface = LinearColors.Canvas,
    error = LinearColors.Error,
    onError = Color(0xFFFFFFFF),
    outline = LinearColors.Hairline,
    outlineVariant = LinearColors.HairlineStrong,
)

/**
 * Light color scheme. Linear ships dark-first by default, but TemuBelajar is
 * a study-buddy product, not a marketing page — callers may opt into a light
 * surface for daylight reading. Ink is muted near-black (`#1a1c20`) on a
 * two-step warm white ladder; the lavender accent and hairlines stay.
 */
private val LinearLightColorScheme = lightColorScheme(
    primary = LinearColors.Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LinearColors.PrimaryFocus,
    onPrimaryContainer = Color(0xFF1A1C20),
    secondary = LinearColors.Surface3,
    onSecondary = LinearColors.Ink,
    tertiary = LinearColors.BrandSecure,
    background = Color(0xFFFBFBFC),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFF1F2F4),
    onSurfaceVariant = Color(0xFF4A4E55),
    surfaceTint = LinearColors.Primary,
    inverseSurface = Color(0xFF1A1C20),
    inverseOnSurface = LinearColors.InverseCanvas,
    error = LinearColors.Error,
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFE2E4E8),
    outlineVariant = Color(0xFFCBD0D6),
)

/**
 * `TBColors` — backward-compatible names that existing screens reference.
 * Values are repointed onto the Linear dark palette so all consumers inherit
 * the new system automatically.
 */
object TBColors {
    // Brand & accent
    val Primary           get() = LinearColors.Primary
    val Secondary         get() = LinearColors.InkSubtle        // No second chromatic; alias to subtle slate
    val Background        get() = LinearColors.Canvas
    val TextPrimary       get() = LinearColors.Ink

    // Surface ladder
    val Surface           get() = LinearColors.Surface1
    val SurfaceVariant    get() = LinearColors.Surface2
    val PrimaryContainer  get() = LinearColors.PrimaryHover
    val PrimaryContainerDark get() = LinearColors.PrimaryFocus

    // Semantic
    val Success           get() = LinearColors.Success
    val Error             get() = LinearColors.Error

    // "Dark" suffixed aliases — the system is dark-only, so these mirror their non-Dark siblings
    val BackgroundDark    get() = LinearColors.Canvas
    val SurfaceDark       get() = LinearColors.Surface3
    val TextPrimaryDark   get() = LinearColors.Ink

    // Cards
    val CardBg            get() = LinearColors.Surface1
    val CardBgDark        get() = LinearColors.Surface2
    val CardBorder        get() = LinearColors.Hairline
    val CardBorderDark    get() = LinearColors.HairlineStrong

    // Aliases
    val AccentPurple get() = LinearColors.Primary
    val AccentBlue   get() = LinearColors.BrandSecure
    val DarkBg       get() = LinearColors.Canvas

    val TextSecondary      get() = LinearColors.InkMuted
    val TextSecondaryDark  get() = LinearColors.InkMuted
    val TextMuted          get() = LinearColors.InkSubtle
    val TextMutedDark      get() = LinearColors.InkSubtle
    val TextHint           get() = LinearColors.InkTertiary
    val TextHintDark        get() = LinearColors.InkTertiary

    // Hairline ladder aliases
    val Hairline           get() = LinearColors.Hairline
    val HairlineStrong     get() = LinearColors.HairlineStrong
    val HairlineTertiary   get() = LinearColors.HairlineTertiary

    val InverseCanvas      get() = LinearColors.InverseCanvas
    val InverseInk         get() = LinearColors.Canvas
}

// ── Typography ──────────────────────────────────────────────────────────────

/**
 * Outfit font stack bundled at `composeResources/font/`. Loaded with explicit
 * `FontWeight` so each {@link Font} declaration is a single-weight entry in
 * the family fallback chain — Compose Multiplatform then resolves a weight
 * close to the requested one for any style that doesn't match exactly.
 *
 * Phase 6.1 — bundled Outfit Regular/Medium/SemiBold/Bold replaces the fake
 * `FontFamily.Default` placeholder. Phase 6.3 — explicit weight assignments
 * keep the fallback chain deterministic across Android (where Roboto would
 * otherwise round 600→700) and Desktop.
 *
 * NB: {@link org.jetbrains.compose.resources.Font} is `@Composable`, so the
 * family is materialized inside a {@code @Composable} getter rather than a
 * plain {@code by lazy { … }} initializer.
 */
@Composable
private fun displayFamily(): FontFamily = FontFamily(
    Font(Res.font.outfit_regular,  FontWeight.W400),
    Font(Res.font.outfit_medium,   FontWeight.W500),
    Font(Res.font.outfit_semibold, FontWeight.W600),
    Font(Res.font.outfit_bold,     FontWeight.W700),
)

/**
 * FontScale-aware letter spacing. Negative tracking on display/headline
 * tokens is fine at 1.0× fontScale, but at large accessibility scales
 * (fontScale > 1.3) the negative tracking compounds the larger glyph size and
 * letter crowding begins to clamp glyphs together (WCAG 1.4.4 risk). Gating
 * the negative offset to 0 above the threshold keeps the visual styling
 * intact for normal users while keeping the text legible for users on
 * aggressive font scales.
 *
 * Phase 6.4 fix. Use {@link #negativeLetterSpacing} from inside a
 * {@code @Composable} scope so {@link LocalDensity} resolves.
 */
private const val FONT_SCALE_TRACKING_THRESHOLD = 1.3f

@Composable
private fun negativeLetterSpacing(raw: Float): androidx.compose.ui.unit.TextUnit {
    val scale = LocalDensity.current.fontScale
    return if (scale > FONT_SCALE_TRACKING_THRESHOLD) 0.sp else raw.sp
}

object TBTypography {
    val Mono          = FontFamily.Monospace   // Linear Mono     ⤳ ui-monospace / SF Mono

    @get:Composable
    val DisplayXL    get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.SemiBold, fontSize = 80.sp, lineHeight = 84.sp, letterSpacing = negativeLetterSpacing(-3.0f))
    @get:Composable
    val DisplayLG    get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = negativeLetterSpacing(-1.8f))
    @get:Composable
    val DisplayMD    get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = negativeLetterSpacing(-1.0f))
    @get:Composable
    val Headline     get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = negativeLetterSpacing(-0.6f))
    @get:Composable
    val CardTitle    get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.Medium,  fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = negativeLetterSpacing(-0.4f))
    @get:Composable
    val Subhead      get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.Normal,   fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = negativeLetterSpacing(-0.2f))
    @get:Composable
    val BodyLG       get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.Normal,   fontSize = 18.sp, lineHeight = 27.sp, letterSpacing = negativeLetterSpacing(-0.1f))
    @get:Composable
    val Body         get() = TextStyle(fontFamily = displayFamily(), fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = negativeLetterSpacing(-0.05f))
    val BodySM       get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp)
    val Caption      get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp)
    /**
     * Phase 6.7 — sub-caption size for chips, badges, and "loading video…"
     * placeholders. Was previously ~12 hardcoded `10.sp` literals spread
     * across [VideoChatScreen] unread badge + per-platform `VideoViews`
     * waiting labels. Coalescing them here gives one source of truth for
     * the smaller-than-Caption tier.
     */
    val Badge        get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,    fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.sp)
    val Button       get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,    fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 0.sp)
    val Eyebrow      get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,    fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.4.sp)
    val MonoSM       get() = TextStyle(fontFamily = Mono,   fontWeight = FontWeight.Normal,    fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
    /**
     * Phase 6.7 — size for emoji-only chat messages (which were `32.sp`).
     * Sits between [Headline] (28sp) and [DisplayMD] (40sp) — none of the
     * canonical Material3 typography slots matched so we mint our own.
     */
    @get:Composable
    val EmojiXL      get() = TextStyle(fontFamily = displayFamily(),  fontWeight = FontWeight.Normal,    fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 0.sp)

    /**
     * Non-composable aliases for code paths that cannot observe LocalDensity
     * (e.g. constructing a static {@link Typography}). These always return the
     * configured negative tracking; callers that need the fontScale clamp
     * should consume the @Composable accessors above instead. Phase 6.4.
     */
    object Static {
        val DisplayXL    get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 80.sp, lineHeight = 84.sp, letterSpacing = (-3.0).sp)
        val DisplayLG    get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 56.sp, lineHeight = 62.sp, letterSpacing = (-1.8).sp)
        val DisplayMD    get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-1.0).sp)
        val Headline     get() = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp)
    }
}

@Composable
private fun linearTypography(): Typography = Typography(
    displayLarge     = TBTypography.DisplayXL,
    displayMedium    = TBTypography.DisplayLG,
    displaySmall     = TBTypography.DisplayMD,
    headlineLarge    = TBTypography.Headline,
    headlineMedium   = TBTypography.Headline,
    headlineSmall    = TBTypography.CardTitle,
    titleLarge       = TBTypography.CardTitle,
    titleMedium      = TBTypography.Subhead,
    titleSmall       = TBTypography.BodySM,
    bodyLarge        = TBTypography.BodyLG,
    bodyMedium       = TBTypography.Body,
    bodySmall        = TBTypography.BodySM,
    labelLarge       = TBTypography.Button,
    labelMedium      = TBTypography.Button,
    labelSmall       = TBTypography.Caption,
)

// ── Theme ───────────────────────────────────────────────────────────────────

/**
 * Surface for runtime dark-mode opt-in/out. Read via {@link LocalIsDarkMode}.
 * Defaults to true — TemuBelajar ships dark-on-dark to match Linear's
 * "single-voice dark" marketing aesthetic, but callers can pin the value via
 * {@link TemuBelajarTheme#isDarkMode}. Phase 6.5.
 */
val LocalIsDarkMode = staticCompositionLocalOf { true }

/**
 * Single-voice theme wrapper. Defaults to dark (Linear aesthetic); callers
 * that want a light surface pair pass {@code isDarkMode = false}. The host
 * CompositionLocal {@link LocalIsDarkMode} propagates the choice to nested
 * composables that need to react to the mode without re-querying the theme.
 *
 * Phase 6.5.
 */
@Composable
fun TemuBelajarTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkMode) LinearDarkColorScheme else LinearLightColorScheme
    CompositionLocalProvider(
        LocalIsDarkMode provides isDarkMode,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = linearTypography(),
            content = content
        )
    }
}

// ── Design tokens (back-compat object names survive) ────────────────────────

object TBShapes {
    val XS    = RoundedCornerShape(4.dp)
    val SM    = RoundedCornerShape(6.dp)
    val MD    = RoundedCornerShape(8.dp)
    val LG    = RoundedCornerShape(12.dp)
    val XL    = RoundedCornerShape(16.dp)
    val XXL   = RoundedCornerShape(24.dp)
    val Pill  = RoundedCornerShape(50)
    /** Back-compat alias for {@link #Pill}. {@link Full} and {@link Pill} were
     *  identical (both 50% pill); they have been collapsed — callers should
     *  prefer {@link #Pill}. Kept here so existing screens keep compiling.
     *  Phase 6.10. */
    val Full  get() = Pill

    // Back-compat members used by existing screens
    val Button  get() = MD     // 8px — primary CTAs (Linear)
    val Card    get() = LG     // 12px — feature / pricing cards (Linear)
    val Input   get() = MD     // 8px — form inputs (Linear)
}

object TBElevation {
    // Linear resists shadows on dark; depth comes from the surface ladder.
    val Card   = 0.dp
    val Button = 0.dp
}

/**
 * Bundled Outfit font aliases. Phase 6.1 / 6.6 — `outfit_*` .ttf files in
 * `composeResources/font/` are now real bundled Outfit (Regular 400 / Medium
 * 500 / SemiBold 600 / Bold 700) instead of the placeholder
 * {@link FontFamily#Default}. Callers preferring the design-system display
 * stack should consume {@link TBTypography} rather than this object — the
 * typed tokens wire weight/letterSpacing/fontScale handling together.
 *
 * NB: {@link org.jetbrains.compose.resources.Font} is `@Composable`, so the
 * {@link Outfit} accessor is a `@Composable get()` — read it inside a
 * composition scope (the typical {@code Text(..., fontFamily = TBFonts.Outfit)}
 * case automatically is).
 */
object TBFonts {
    @get:Composable
    val Outfit   get() = FontFamily(
        Font(Res.font.outfit_regular,  FontWeight.W400),
        Font(Res.font.outfit_medium,   FontWeight.W500),
        Font(Res.font.outfit_semibold, FontWeight.W600),
        Font(Res.font.outfit_bold,     FontWeight.W700),
    )
    @get:Composable
    val Display  get() = Outfit
    @get:Composable
    val Text     get() = Outfit
    val Mono     get() = FontFamily.Monospace
}

/** 4px-base spacing ladder: xxs/xs/sm/md/lg/xl/xxl/section. */
object TBSpace {
    val XXS  = 4.dp
    val XS   = 8.dp
    val SM   = 12.dp
    val MD   = 16.dp
    val LG   = 24.dp
    val XL   = 32.dp
    val XXL  = 48.dp
    val Section = 96.dp

    /**
     * Material3-aligned touch-target height (48dp) for primary/secondary
     * CTAs. Replaces the legacy fixed 40dp height on {@link TBPrimaryButton}
     * / {@link TBSecondaryButton}. Pair with {@link TBShapes#Button} corners.
     * Phase 6.9.
     */
    val ButtonHeight = 48.dp
}
