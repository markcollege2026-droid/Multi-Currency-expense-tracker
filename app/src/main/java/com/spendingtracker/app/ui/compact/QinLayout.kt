package com.spendingtracker.app.ui.compact

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout density used by the Qin F21 Pro build.
 *
 * The F21 Pro is a 2.8" 480x640 portrait panel (about 286 ppi). Android usually
 * exposes it as either 240x320dp (density 320) or 320x427dp (density 240).
 * Both are treated as compact so the app is laid out for the narrower case.
 */
object QinLayout {
    const val COMPACT_MAX_WIDTH_DP = 340
    const val COMPACT_MAX_HEIGHT_DP = 520

    fun isCompact(widthDp: Int, heightDp: Int): Boolean =
        widthDp <= COMPACT_MAX_WIDTH_DP || heightDp <= COMPACT_MAX_HEIGHT_DP

    /** Resolves the screen width reported by Config changes, ignoring zero placeholders. */
    fun isCompactScreen(widthDp: Int, heightDp: Int): Boolean =
        widthDp > 0 && heightDp > 0 && isCompact(widthDp, heightDp)
}

@Immutable
data class AppDimens(
    val screenPadding: Dp,
    val sectionSpacing: Dp,
    val cardPadding: Dp,
    val rowSpacing: Dp,
    val topBarHeight: Dp,
    val navBarHeight: Dp,
    val iconButtonSize: Dp,
    val iconSize: Dp
)

val RegularDimens = AppDimens(
    screenPadding = 16.dp,
    sectionSpacing = 16.dp,
    cardPadding = 16.dp,
    rowSpacing = 8.dp,
    topBarHeight = 56.dp,
    navBarHeight = 72.dp,
    iconButtonSize = 44.dp,
    iconSize = 22.dp
)

/** Sized for 240x320dp: 6dp gutters, 34dp app bar and 42dp tab bar. */
val CompactDimens = AppDimens(
    screenPadding = 6.dp,
    sectionSpacing = 6.dp,
    cardPadding = 8.dp,
    rowSpacing = 4.dp,
    topBarHeight = 34.dp,
    navBarHeight = 42.dp,
    iconButtonSize = 30.dp,
    iconSize = 16.dp
)

val LocalCompactUi = staticCompositionLocalOf { false }
val LocalAppDimens = staticCompositionLocalOf { RegularDimens }

/** True when the device reports a 12-key/QWERTY hardware keyboard or is a known Qin handset. */
val LocalHardwareKeypad = staticCompositionLocalOf { false }

/** Amber is deliberately not part of the Material scheme: it stays visible on both themes. */
val QinFocusColor = Color(0xFFFFC400)
