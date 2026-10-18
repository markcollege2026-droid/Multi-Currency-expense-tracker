package com.spendingtracker.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.spendingtracker.app.ui.compact.CompactDimens
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.compact.QinFocusColor
import com.spendingtracker.app.ui.compact.QinFocusIndication
import com.spendingtracker.app.ui.compact.QinLayout
import com.spendingtracker.app.ui.compact.RegularDimens
import com.spendingtracker.app.ui.compact.relaxedCompactTypography
import com.spendingtracker.app.util.DeviceInput

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFFD6D0DA)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F378B),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E)
)

@Composable
fun SpendingTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val compact = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        QinLayout.isCompactScreen(configuration.screenWidthDp, configuration.screenHeightDp)
    }
    val hardwareKeypad = remember(context) { DeviceInput.hasPhysicalKeypad(context) }
    val dimens = if (compact) CompactDimens else RegularDimens
    val focusIndication = remember(colorScheme, darkTheme) {
        QinFocusIndication(
            focusColor = QinFocusColor,
            pressedColor = if (darkTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f)
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
        LocalIndication provides focusIndication
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
