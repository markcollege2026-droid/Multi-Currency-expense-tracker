package com.spendingtracker.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Ledger palette — see .superdesign/design-system.md.
 *
 * One forest accent for value and action, warm bone paper surfaces, sage neutrals
 * for secondary chrome, amber for attention (and for the hardware focus ring) and
 * clay for loss. Cards are flat fills with hairline borders instead of shadows.
 */

// Brand ramps ---------------------------------------------------------------
val Forest900 = Color(0xFF0B3A24)
val Forest700 = Color(0xFF0F5132)
val Forest500 = Color(0xFF1B7A50)
val Forest200 = Color(0xFFB9DCC6)
val Forest50 = Color(0xFFEDF6F0)

val Sage900 = Color(0xFF2A352D)
val Sage700 = Color(0xFF4C5148)
val Sage500 = Color(0xFF75857A)
val Sage200 = Color(0xFFD9E2DA)
val Sage50 = Color(0xFFF1F4F0)

val Amber900 = Color(0xFF6B4406)
val Amber700 = Color(0xFF8A5A12)
val Amber500 = Color(0xFFC98A1B)
val Amber200 = Color(0xFFF7DFAE)
val Amber50 = Color(0xFFFDF4E3)

val Clay900 = Color(0xFF6B1512)
val Clay700 = Color(0xFFB3261E)
val Clay500 = Color(0xFFDC5449)
val Clay200 = Color(0xFFF6C9C4)
val Clay50 = Color(0xFFFDEDEB)

// Neutral paper / ink -------------------------------------------------------
val Paper = Color(0xFFF7F5EF)
val PaperSurface = Color(0xFFFBF9F4)
val PaperLowest = Color(0xFFFFFFFF)
val PaperLow = Color(0xFFF7F5EE)
val PaperContainer = Color(0xFFF2F0E8)
val PaperHigh = Color(0xFFECE9E0)
val PaperHighest = Color(0xFFE6E3D9)
val Ink = Color(0xFF171B15)
val InkMuted = Color(0xFF4C5148)
val Hairline = Color(0xFFCDD1C6)
val OutlineGrey = Color(0xFF7C8378)

val NightBackground = Color(0xFF0D1210)
val NightSurface = Color(0xFF101613)
val NightLowest = Color(0xFF0A0F0C)
val NightLow = Color(0xFF141A16)
val NightContainer = Color(0xFF171E1A)
val NightHigh = Color(0xFF1E2621)
val NightHighest = Color(0xFF253029)
val NightInk = Color(0xFFE2E6DF)
val NightInkMuted = Color(0xFFBFCBC1)
val NightHairline = Color(0xFF3F4A42)
val NightOutline = Color(0xFF8A958C)

val Mint = Color(0xFF7EDCA4)
val MintDeep = Color(0xFF14532D)
val MintInk = Color(0xFFA9F0C4)

/** The amber D-pad focus ring. Deliberately outside the Material scheme. */
val FocusAmber = Color(0xFFFFC400)

val LightColorScheme = lightColorScheme(
    primary = Forest700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7ECDD),
    onPrimaryContainer = Color(0xFF08361F),
    inversePrimary = Forest200,
    secondary = Color(0xFF5C6B60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EDE4),
    onSecondaryContainer = Color(0xFF2A352D),
    tertiary = Amber700,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE7C6),
    onTertiaryContainer = Color(0xFF4A2F05),
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperHighest,
    onSurfaceVariant = InkMuted,
    surfaceTint = Forest700,
    surfaceBright = Color(0xFFFDFBF6),
    surfaceDim = Color(0xFFDEDBD1),
    surfaceContainerLowest = PaperLowest,
    surfaceContainerLow = PaperLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,
    inverseSurface = Color(0xFF2C302A),
    inverseOnSurface = Color(0xFFF2F1EA),
    outline = OutlineGrey,
    outlineVariant = Hairline,
    error = Clay700,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000)
)

val DarkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00391C),
    primaryContainer = MintDeep,
    onPrimaryContainer = MintInk,
    inversePrimary = Forest700,
    secondary = Color(0xFFB8C9BB),
    onSecondary = Color(0xFF23342A),
    secondaryContainer = Color(0xFF33463A),
    onSecondaryContainer = Color(0xFFD4E6D6),
    tertiary = Color(0xFFF0C27B),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDFA6),
    background = NightBackground,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightHairline,
    onSurfaceVariant = NightInkMuted,
    surfaceTint = Mint,
    surfaceBright = Color(0xFF2B342E),
    surfaceDim = Color(0xFF0A0F0C),
    surfaceContainerLowest = NightLowest,
    surfaceContainerLow = NightLow,
    surfaceContainer = NightContainer,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
    inverseSurface = NightInk,
    inverseOnSurface = NightBackground,
    outline = NightOutline,
    outlineVariant = NightHairline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color(0xFF000000)
)

/**
 * Extra semantic colours that Material's [androidx.compose.material3.ColorScheme]
 * does not model: signed amounts, the single hero gradient, and card borders.
 */
@Immutable
data class AppPalette(
    val income: Color,
    val expense: Color,
    val warning: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val onHeroFaint: Color,
    val cardBorder: Color,
    val cardFill: Color,
    val rowFill: Color,
    val focus: Color
)

val LightAppPalette = AppPalette(
    income = Color(0xFF1F7A4C),
    expense = Clay700,
    warning = Color(0xFFB97C10),
    heroStart = Forest700,
    heroEnd = Color(0xFF1D6B45),
    onHero = Color.White,
    onHeroMuted = Color(0xCCFFFFFF),
    onHeroFaint = Color(0x66FFFFFF),
    cardBorder = Hairline,
    cardFill = PaperLowest,
    rowFill = PaperLow,
    focus = FocusAmber
)

val DarkAppPalette = AppPalette(
    income = Color(0xFF79D6A4),
    expense = Color(0xFFFFB4AB),
    warning = Color(0xFFF0C27B),
    heroStart = Color(0xFF10301F),
    heroEnd = Color(0xFF1A4A31),
    onHero = Color(0xFFEAF4EC),
    onHeroMuted = Color(0xB3EAF4EC),
    onHeroFaint = Color(0x59EAF4EC),
    cardBorder = NightHairline,
    cardFill = NightLow,
    rowFill = NightContainer,
    focus = FocusAmber
)

val LocalAppPalette = staticCompositionLocalOf { LightAppPalette }
