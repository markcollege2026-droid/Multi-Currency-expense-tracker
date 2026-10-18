# Qin F21 Pro build

This branch adapts the existing Spending Tracker app to the Qin F21 Pro without
changing the database, calculations or feature set.

## Target device

| Item | Value |
| --- | --- |
| Display | 2.8" 480×640 portrait (reported as 240×320dp at 320dpi or 320×427dp at 240dpi) |
| SoC / RAM | MediaTek MT6761 quad-core, 3 GB (some units MT8766 / 4 GB) |
| OS | Android 11 (API 30) stock; app keeps minSdk 26 / targetSdk 35 |
| Input | Touchscreen + 21-key pad: Back, Call, 0–9, `*`, `#`, D-pad, centre/OK, Menu |

Density varies between units, so the compact layout is used for **either** width
when the window is at most 340dp wide **or** 520dp tall. Normal phones keep the
original Material layout.

## What changed

### Layout and typography
- Compact breakpoints, dimensions (`6dp` gutters, `34dp` top bar, `42dp` tab bar,
  `30dp` icon buttons) and a smaller Material type scale (9–15sp labels/titles).
- Home: balance + weekly/monthly totals, adjust-balance action, quick expense /
  money buttons, collapsible accounts panel (total available always visible),
  recent transactions, top categories, 7-day trend, recurring/loans shortcuts.
- Transactions: search and add actions share one row, compact filters and day
  groups, delete action on each row. The two 120dp ExtendedFABs are gone.
- Converter: compact currency cards and dialogs; fixed 440dp/380dp dialogs were
  replaced with height-capped `AppDialog`s and scrollable lists.
- Settings, Loans, Add loan/debt, Recurring, Setup, Add expense and Add money all
  use the compact controls; primary actions are pinned in a bottom bar with
  `imePadding()` so the keyboard cannot hide them.
- Every dialog has a 94% screen-height cap, a scrollable body and pinned buttons.

### Physical-key navigation
- Amber focus ring (`QinFocusIndication`) replaces the ripple as `LocalIndication`,
  so Material buttons, custom rows, chips, tabs and cards all show focus.
- D-pad moves focus; centre/OK activates; hold/tap behaviour of the original
  screens is unchanged.
- Back: dialogs dismiss first; the bottom-nav screens return to Home; form screens
  pop back to the previous screen (system Back, no custom global interception).
- Numeric fields accept hardware digits directly while the soft keyboard is
  hidden. `*`, `.`, `,` and numpad decimal keys all produce a decimal point;
  a second decimal point is ignored. Up/Down leave a single-line field instead of
  being swallowed as cursor movement.
- On a detected Qin/keypad device the IME stays hidden when a form opens and when
  moving with the D-pad. **Tapping any field always raises the soft keyboard**, so
  touch-screen entry still works. Text fields (notes, names) can use the keypad's
  own T9 IME by tapping.
- `adjustResize` + `imePadding()` keep focused fields and Save/Cancel visible.

### Data and behaviour preserved
- Room version 4 and the JSON schema are untouched; no migrations and no data
  reset. All repository/calculation code is unchanged except comma-tolerant
  parsing of amount/rate input in `AddExpenseViewModel` and `AddTopUpViewModel`.
- Categories, budgets/thresholds, reports, recurring payments, loans, backups,
  exports, autopay and card payments keep their existing logic and screens.
- No new permissions, backend, telemetry or dependency at runtime. The only
  dependency additions are test-only Compose UI test artifacts.

## Build and test results

All results below come from the GitHub Actions **Build APKs** workflow
(`./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease`):

- `d37df1a` — success: unit tests, lint, debug APK, signed release APK and AAB.
- `9dc5ff2` — success (same task set).

New tests:
- `KeypadInputTest` — hardware digits, one decimal separator, comma/star
  normalisation, minus handling.
- `DateInputTest` — valid/impossible dates, leap day, year bounds, end-of-day.
- `DeviceInputTest` — Qin model detection.
- `QinLayoutTest` — 480×640 window sizes use compact layout, regular phones do not,
  compact dimensions/type scale.
- `CompactKeypadUiTest` (Robolectric + Compose) — compact theme at
  `w240dp-h320dp`, hardware number keys fill the focused amount field, a second
  decimal key is rejected.

## Known issues and limitations

- **No physical Qin F21 Pro was available.** The APK was compiled and tested with
  JVM/Robolectric tests in CI; it has not been installed on hardware. Real-device
  checks (focus order, keycodes, IME behaviour, display density) still need to be
  done on a unit. No emulator was available in the build environment either.
- The **Menu key** (reported as keycode 82 on some units) is deliberately not
  assigned a global action; its availability varies between variants and no
  verified action was needed for the primary flows.
- Density (240 vs 320dpi) is not confirmed for every variant. The UI is designed
  for the narrower 240dp case, so it stays usable at 320dp.
- Some dense controls are 28–36dp tall (pills/chips) instead of Material's 48dp
  touch target to fit 320dp of height. Physical-key focus makes them reachable;
  touch targets in forms remain 48dp.
- Charts keep their original drawing code; on very narrow widths labels may be
  dense. They remain scrollable/legible but were not redesigned.

## Install

Download `spending-tracker-apks` from the latest successful **Build APKs** run:
`app-debug.apk` (debug key, installs beside a different signature only after
uninstalling) or `app-release.apk` (signed with the repository's release key,
versionCode 8). Install with `adb install -r app-release.apk`.
