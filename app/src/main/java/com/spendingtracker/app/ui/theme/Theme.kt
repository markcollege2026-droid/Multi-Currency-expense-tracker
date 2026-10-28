package com.spendingtracker.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.spendingtracker.app.ui.compact.CompactDimens
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.compact.QinFocusIndication
import com.spendingtracker.app.ui.compact.QinLayout
import com.spendingtracker.app.ui.compact.RegularDimens
import com.spendingtracker.app.ui.compact.relaxedCompactTypography
import com.spendingtracker.app.util.DeviceInput

/**
 * Ledger theme.
 *
 * The app ships its own colour system ([LightColorScheme] / [DarkColorScheme]) so
 * every device looks the same; Material You dynamic colour stays available behind
 * [dynamicColor] for users who want it. Colours Material does not model (signed
 * amounts, the hero gradient, card borders) are provided through [LocalAppPalette].
 */
@Composable
fun SpendingTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val palette = if (darkTheme) DarkAppPalette else LightAppPalette

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val compact = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        QinLayout.isCompactScreen(configuration.screenWidthDp, configuration.screenHeightDp)
    }
    val hardwareKeypad = remember(context) { DeviceInput.hasPhysicalKeypad(context) }
    val dimens = if (compact) CompactDimens else RegularDimens
    val focusIndication = remember(colorScheme, darkTheme) {
        QinFocusIndication(
            focusColor = FocusAmber,
            pressedColor = if (darkTheme) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f)
            else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.07f)
        )
    }
    val typography = remember(compact) { if (compact) relaxedCompactTypography() else Typography }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalCompactUi provides compact,
        LocalAppDimens provides dimens,
        LocalHardwareKeypad provides hardwareKeypad,
        LocalIndication provides focusIndication,
        LocalAppPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}

/**
 * Maps a stored theme preference (LIGHT / DARK / SYSTEM, anything else treated
 * as SYSTEM) to the dark flag the theme needs. Kept pure so the mapping is
 * unit-testable without composition.
 */
fun resolveDarkTheme(themeMode: String?, systemDark: Boolean): Boolean = when (themeMode) {
    "LIGHT" -> false
    "DARK" -> true
    else -> systemDark
}
