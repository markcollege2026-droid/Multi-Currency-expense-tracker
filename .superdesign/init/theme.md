# Theme — Spending Tracker (Jetpack Compose / Material 3)

No CSS / Tailwind here: the product is a native Android app written in Kotlin +
Jetpack Compose. The design tokens below are the Compose equivalents.

## Part 1 — Compact token summary (read this first)

**Previous state (before this design pass)**

| Token | Value |
|---|---|
| Color scheme | Material 3 defaults (purple `#4F378B` / `#D0BCFF`) plus **dynamic color on** (`dynamicLightColorScheme`) on Android 12+ |
| Typography | `Typography()` defaults, plus `compactTypography()` (9–22sp) and `relaxedCompactTypography()` (10–44sp) for the Qin F21 |
| Shapes | Material defaults (Card `12.dp`, Button full pill) |
| Density | `AppDimens` — `RegularDimens` 16/16/16dp, `CompactDimens` 11/11/12dp, `TightDimens` 6/6/8dp |
| Focus ring | Amber `#FFC400`, 2dp ring, drawn by `QinFocusIndication` |
| Cards | `Card` default (`surfaceContainerHighest`) with **no border** |
| Radius | 5–8dp everywhere (`RoundedCornerShape(6.dp)` in lists) |

**New design system — "Ledger" (`.superdesign/design-system.md` is the source of truth)**

Colors (light / dark):

| Role | Light | Dark |
|---|---|---|
| primary | `#0F5132` forest | `#7EDCA4` mint |
| onPrimary | `#FFFFFF` | `#00391C` |
| primaryContainer | `#D7ECDD` | `#14532D` |
| onPrimaryContainer | `#08361F` | `#A9F0C4` |
| secondary | `#5C6B60` sage | `#B8C9BB` |
| secondaryContainer | `#E7EDE4` | `#33463A` |
| tertiary | `#8A5A12` amber-brown | `#F0C27B` |
| tertiaryContainer | `#FBE7C6` | `#5F4100` |
| error | `#B3261E` | `#FFB4AB` |
| background | `#F7F5EF` bone | `#0D1210` |
| surface | `#FBF9F4` paper | `#101613` |
| surfaceContainerLowest → Highest | `#FFFFFF · #F7F5EE · #F2F0E8 · #ECE9E0 · #E6E3D9` | `#0A0F0C · #141A16 · #171E1A · #1E2621 · #253029` |
| onSurface | `#171B15` | `#E2E6DF` |
| onSurfaceVariant | `#4C5148` | `#BFCBC1` |
| outline / outlineVariant | `#7C8378` / `#CDD1C6` | `#8A958C` / `#3F4A42` |
| income | `#1F7A4C` | `#79D6A4` |
| warning | `#B97C10` | `#F0C27B` |
| hero gradient | `#0F5132 → #1D6B45` | `#10301F → #1A4A31` |

Shape scale: `extraSmall 6 · small 10 · medium 14 · large 20 · extraLarge 28` dp.
Spacing: 4pt grid — 4 / 8 / 12 / 16 / 20 / 24 / 32. Card padding 16dp (12dp compact), screen gutter 16dp (11dp compact).
Type: system sans; headings −0.2sp tracking, eyebrow labels 11sp uppercase +0.8sp tracking; all money uses tabular figures.

## Part 2 — Raw source dumps

### `app/src/main/java/com/spendingtracker/app/ui/theme/Theme.kt` (before)
```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF), secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF1C1B1F), surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF2B2930), onSurfaceVariant = Color(0xFFD6D0DA)
)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F378B), secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260),
    background = Color(0xFFFFFBFE), surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EB), onSurfaceVariant = Color(0xFF49454E)
)
// dynamicColor defaults to true -> Material You purple on most devices
```

### `ui/theme/Type.kt`
```kotlin
val Typography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
)
```

### `ui/theme/CompactType.kt`
`compactTypography()` (Qin F21 tight scale: display 32/36 … labelSmall 9/12) and
`relaxedCompactTypography()` (display 44/48 … labelSmall 10/13). Both derive from
`Typography()` defaults, so a custom `Typography` in `Type.kt` does not affect them.

### `ui/compact/QinLayout.kt`
```kotlin
object QinLayout {
    const val COMPACT_MAX_WIDTH_DP = 340
    const val COMPACT_MAX_HEIGHT_DP = 520
    fun isCompact(widthDp: Int, heightDp: Int) = widthDp <= 340 || heightDp <= 520
}
val RegularDimens = AppDimens(screenPadding = 16.dp, sectionSpacing = 16.dp, cardPadding = 16.dp, rowSpacing = 8.dp, topBarHeight = 56.dp, navBarHeight = 72.dp, iconButtonSize = 44.dp, iconSize = 22.dp)
val CompactDimens = AppDimens(11.dp, 11.dp, 12.dp, 6.dp, 45.dp, 57.dp, 37.dp, 19.dp)
val TightDimens   = AppDimens(6.dp, 6.dp, 8.dp, 4.dp, 34.dp, 42.dp, 30.dp, 16.dp)
val QinFocusColor = Color(0xFFFFC400)
```

### Theme composition locals (`Theme.kt`)
`LocalCompactUi`, `LocalAppDimens`, `LocalHardwareKeypad`, `LocalIndication` (amber
focus ring), plus new `LocalAppPalette` (income / warning / hero gradient / card border).
