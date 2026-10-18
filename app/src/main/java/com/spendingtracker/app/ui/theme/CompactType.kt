package com.spendingtracker.app.ui.compact

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

/**
 * Compact Material type scale. Material's phone scale is designed for 360dp-wide
 * screens; on a 240dp screen it wastes about a third of every line box. This keeps
 * the same hierarchy with 9-15sp labels/titles and 11-13sp body text.
 */
fun compactTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontSize = 32.sp, lineHeight = 36.sp),
        displayMedium = base.displayMedium.copy(fontSize = 28.sp, lineHeight = 32.sp),
        displaySmall = base.displaySmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
        headlineLarge = base.headlineLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),
        headlineMedium = base.headlineMedium.copy(fontSize = 20.sp, lineHeight = 24.sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 18.sp, lineHeight = 22.sp),
        titleLarge = base.titleLarge.copy(fontSize = 15.sp, lineHeight = 19.sp),
        titleMedium = base.titleMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
        titleSmall = base.titleSmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontSize = 11.sp, lineHeight = 14.sp),
        labelMedium = base.labelMedium.copy(fontSize = 10.sp, lineHeight = 13.sp),
        labelSmall = base.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp)
    )
}

/**
 * Comfortable compact scale: mid-way between [compactTypography] and the regular
 * Material phone scale. Used by every screen except the converter, whose approved
 * tight sizing keeps using [compactTypography].
 */
fun relaxedCompactTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontSize = 44.sp, lineHeight = 48.sp),
        displayMedium = base.displayMedium.copy(fontSize = 36.sp, lineHeight = 40.sp),
        displaySmall = base.displaySmall.copy(fontSize = 30.sp, lineHeight = 34.sp),
        headlineLarge = base.headlineLarge.copy(fontSize = 27.sp, lineHeight = 31.sp),
        headlineMedium = base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 28.sp),
        headlineSmall = base.headlineSmall.copy(fontSize = 21.sp, lineHeight = 25.sp),
        titleLarge = base.titleLarge.copy(fontSize = 18.5.sp, lineHeight = 22.sp),
        titleMedium = base.titleMedium.copy(fontSize = 14.5.sp, lineHeight = 18.sp),
        titleSmall = base.titleSmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 14.5.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 11.25.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
        labelMedium = base.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
        labelSmall = base.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp)
    )
}
