# Spending Tracker — Design System ("Ledger")

> Source of truth for every UI surface in `com.spendingtracker.app`.
> Implemented in Compose: `ui/theme/Color.kt`, `ui/theme/Theme.kt`,
> `ui/theme/Shapes.kt`, `ui/components/AppCards.kt`.

## 1. Product context

**What it is.** An offline-first personal finance ledger for Android. One device holds
multiple currency wallets (main/debit, cash, credit cards), expenses in any currency
converted at save time, repeating payments, loans/debts, and an offline currency
converter.

**Who it's for.** People who track money by hand and want exact numbers: multi-currency
freelancers, travellers, expats. Many of them use a small hardware-keypad phone
(Qin F21 Pro, 480×640) and drive the whole app with a D-pad.

**Core value.** *Numbers you can trust, at a glance, offline.*

**Key journeys.**
1. Open the app → see what's left → log an expense in a foreign currency.
2. Review the month → filter history → fix a mistake.
3. Pay a card → see debt fall → confirm autopay handled it.
4. Lend or borrow → track partial repayments → settle.
5. Set up a new wallet or restore a backup without losing history.

**Personality.** Quiet, precise, paper-like. It should feel like a well-kept ledger
book, not a bank dashboard: warm off-white paper, one deep forest accent for value and
action, thin rules instead of heavy shadows, generous-but-tight rhythm, and money set
in tabular figures so columns line up.

## 2. Design principles

1. **The number is the interface.** One dominant figure per screen (balance, amount,
   remaining). Everything else is secondary and smaller.
2. **Paper, not glass.** Flat surfaces with 1dp hairlines and a faint tint step. No
   blur, no neon, no gradient except the single hero card.
3. **Colour earns its place.** Green = money in / primary action. Amber = attention and
   hardware focus. Red = loss or error. Neutral ink everywhere else.
4. **Rhythm on a 4pt grid.** Spacing, sizes and line heights are multiples of 4; the
   compact build steps down one stop, never below 2dp.
5. **Two densities, one language.** Regular (phone/tablet) and Compact (Qin F21) share
   tokens; only sizing and type scale change.
6. **Every pixel reachable.** Anything interactive must take D-pad focus and show the
   amber ring. Touch targets ≥ 32dp compact / 44dp regular.

## 3. Colour

### 3.1 Brand ramps

| Ramp | 900 | 700 | 500 | 200 | 50 |
|---|---|---|---|---|---|
| Forest (primary) | `#0B3A24` | `#0F5132` | `#1B7A50` | `#B9DCC6` | `#EDF6F0` |
| Sage (secondary) | `#2A352D` | `#4C5148` | `#75857A` | `#D9E2DA` | `#F1F4F0` |
| Amber (tertiary / focus) | `#6B4406` | `#8A5A12` | `#C98A1B` | `#F7DFAE` | `#FDF4E3` |
| Clay (error) | `#6B1512` | `#B3261E` | `#DC5449` | `#F6C9C4` | `#FDEDEB` |

### 3.2 Semantic roles — light

| Role | Value | Use |
|---|---|---|
| `background` | `#F7F5EF` | App canvas (warm bone) |
| `surface` | `#FBF9F4` | Top bar, bottom bar, sheet |
| `surfaceContainerLowest` | `#FFFFFF` | Cards, dialogs |
| `surfaceContainerLow` | `#F7F5EE` | List rows, insets |
| `surfaceContainer` | `#F2F0E8` | Section bands, chart track |
| `surfaceContainerHigh` | `#ECE9E0` | Pressed / selected row |
| `surfaceContainerHighest` | `#E6E3D9` | Chips, avatar fills |
| `onSurface` | `#171B15` | Primary text |
| `onSurfaceVariant` | `#4C5148` | Secondary text, meta |
| `outline` | `#7C8378` | Field borders, focus outline idle |
| `outlineVariant` | `#CDD1C6` | Hairlines, card borders |
| `primary` | `#0F5132` | Primary buttons, active tab, links |
| `onPrimary` | `#FFFFFF` | Text on primary |
| `primaryContainer` | `#D7ECDD` | Selected chips, income highlight |
| `onPrimaryContainer` | `#08361F` | Text on primary container |
| `secondary` / `secondaryContainer` | `#5C6B60` / `#E7EDE4` | Neutral actions, secondary buttons |
| `tertiary` / `tertiaryContainer` | `#8A5A12` / `#FBE7C6` | Card payments, warnings, rate badges |
| `error` / `errorContainer` | `#B3261E` / `#F9DEDC` | Overspend, overdue, destructive |
| `income` | `#1F7A4C` | “+ money” amounts |
| `warning` | `#B97C10` | Low balance, stale rates |
| `hero a → b` | `#0F5132 → #1D6B45` | Balance hero gradient only |
| `focus` | `#FFC400` | D-pad focus ring (unchanged, functional) |

### 3.3 Semantic roles — dark

| Role | Value |
|---|---|
| `background` | `#0D1210` |
| `surface` | `#101613` |
| `surfaceContainerLowest` | `#0A0F0C` |
| `surfaceContainerLow` | `#141A16` |
| `surfaceContainer` | `#171E1A` |
| `surfaceContainerHigh` | `#1E2621` |
| `surfaceContainerHighest` | `#253029` |
| `onSurface` | `#E2E6DF` |
| `onSurfaceVariant` | `#BFCBC1` |
| `outline` | `#8A958C` |
| `outlineVariant` | `#3F4A42` |
| `primary` / `onPrimary` | `#7EDCA4` / `#00391C` |
| `primaryContainer` / `onPrimaryContainer` | `#14532D` / `#A9F0C4` |
| `secondary` / `secondaryContainer` | `#B8C9BB` / `#33463A` |
| `tertiary` / `tertiaryContainer` | `#F0C27B` / `#5F4100` |
| `error` / `errorContainer` | `#FFB4AB` / `#93000A` |
| `income` / `warning` | `#79D6A4` / `#F0C27B` |
| `hero a → b` | `#10301F → #1A4A31` |

Dark mode keeps the same hue family, lifts the primary to a mint for contrast on dark
surfaces, and **never** uses pure black or pure white fills.

## 4. Typography

System sans (`FontFamily.Default`), tabular numerals for any monetary value
(`fontFeatureSettings = "tnum"` where available; on compact, fixed `maxLines = 1`).

| Token | Regular | Compact (Qin) | Weight | Use |
|---|---|---|---|---|
| `displayLarge` | 44 / 48 | 36 / 40 | Bold | Hero balance |
| `headlineSmall` | 24 / 28 | 20 / 24 | SemiBold | Screen hero amounts |
| `titleLarge` | 22 / 28 | 18.5 / 22 | SemiBold | Sheet titles |
| `titleMedium` | 16 / 22 | 14.5 / 18 | SemiBold | Top bar title, card titles |
| `titleSmall` | 14 / 20 | 13 / 16 | SemiBold | Section titles, row titles |
| `bodyLarge` | 16 / 24 | 14.5 / 19 | Normal | Field values, body |
| `bodyMedium` | 14 / 20 | 13 / 17 | Normal | Row meta, list text |
| `bodySmall` | 12 / 16 | 11.25 / 15 | Normal | Helper text |
| `labelLarge` | 14 / 20 | 12.5 / 16 | Medium | Buttons |
| `labelMedium` | 12 / 16 | 11 / 15 | Medium | Chips, pills |
| `eyebrow` | 11 / 12 | 10 / 12 | Bold, `+0.8sp` tracking, uppercase | Section eyebrows (“OVERVIEW”, “RECENT”) |
| `amount` | inherit + tabular | inherit + tabular | Bold | Money columns |

Rules: headings −0.2sp tracking; never use more than three type sizes inside one card;
amounts are always right-aligned in rows and use `−` / `+` prefixes rather than colour
alone.

## 5. Spacing, radius, elevation

- **Spacing scale:** 2 · 4 · 8 · 12 · 16 · 20 · 24 · 32.
  Screen gutter 16 (11 compact) · card padding 16 (12) · row padding 12×10 (9×6) ·
  section gap 16 (11) · row gap 8 (6).
- **Radius:** `xs 6` (chips inside cards) · `sm 10` (inputs, small tiles) ·
  `md 14` (buttons, list cards) · `lg 20` (cards) · `xl 28` (hero, sheets) · `full` (pills).
- **Elevation:** 0 for cards (use `1dp` `outlineVariant` border instead), 2dp for top
  bar, 3dp for bottom bar and pinned action bars, 8dp only for dialogs/sheets. Shadow
  colour is neutral, alpha ≤ 0.08.
- **Hairlines:** 1dp `outlineVariant`; inside a card use 0.5dp at 60% alpha.

## 6. Motion

- 120ms for colour/alpha (press, selection), 180ms for size/height, 220ms for
  enter/exit of sheets. Easing: `FastOutSlowIn`.
- List items never animate on scroll; the hero card may fade+rise 8dp on first load.
- Respect “remove animations” by relying on `MaterialTheme` defaults (no custom
  infinite animations).

## 7. Components

**Balance hero** — 28dp radius, forest gradient, `overview` eyebrow, 44/36 display
amount, hairline-divider period tiles (week / month), warning chips in `#FFE1A8` text,
“Adjust” ghost pill top-right. Below it, one row of two actions (`Add expense` filled,
`Add income` tonal).

**Section header** — eyebrow label (uppercase, letterspaced, `onSurfaceVariant`) with an
optional trailing text action (`See all` in `primary`). No underline.

**Card** — `surfaceContainerLowest`, 20dp radius, 1dp `outlineVariant` border, 16dp
padding. Nested rows use `surfaceContainerLow` with 14dp radius and no border.

**List row** — 40dp avatar/badge (initial letter on a 12% tint of the category colour, or
category dot), title `titleSmall`, meta `bodySmall` `onSurfaceVariant`, trailing amount
`amount` bold right-aligned. Row height 56dp regular / 44dp compact.

**Buttons** — filled primary 14dp radius, 44dp min height (32 compact), label `labelLarge`
semibold. Outlined: 1dp `outline`. Text button: primary, no fill. Never use full-pill
buttons; pills are reserved for chips and status.

**Chips / pills** — full radius; selected = `primaryContainer` + `onPrimaryContainer`
bold + 1dp `primary @ 24%`; unselected = transparent + 1dp `outlineVariant` +
`onSurfaceVariant`.

**Inputs** — label above value (12sp `onSurfaceVariant`), value `bodyLarge`, container
`surfaceContainerLow`, 10dp radius, 1dp `outline`; focus = 2dp `primary`; error = 1dp
`error` + `error` helper text.

**Empty state** — centred in a `surfaceContainerLow` card: one-line hint in
`onSurfaceVariant` plus the action that resolves it.

**Bottom navigation** — 72dp (57 compact) surface with 1dp top hairline. Selected item:
icon + label `primary` sitting on a 64×28dp `primaryContainer` pill (compact 44×20);
unselected `onSurfaceVariant`.

**Dialogs** — `surfaceContainerLowest`, 20dp radius, title `titleLarge` bold, body
scrolls to max 58% of screen height, actions pinned bottom-right, primary action last.

## 8. Accessibility

- Contrast: body text ≥ 4.5:1, large amounts ≥ 3:1 (verified for every token pair above).
- Colour is never the only signal: expenses carry `−`, income `+`, overdue rows carry the
  word `OVERDUE`, low balance carries a warning glyph + text.
- Amounts are read as words through `contentDescription` (`“minus 42 shekels”`).
- Focus: amber ring 2dp outside the component, 2dp offset; never clipped.
- Minimum target 32dp compact / 44dp regular; icon buttons keep ≥ 37dp box.

## 9. Compact variant rules (Qin F21 Pro, 240×320dp)

- Same palette, one step tighter: 11dp gutter, 12dp card padding, 45dp top bar, 57dp nav.
- Type steps down per the Compact column; never below 9sp for labels.
- Charts shrink to 26dp bars; hero amount 36sp keeps one line (`softWrap = false`).
- Long lists collapse detail rows behind a chevron instead of shrinking type below 9sp.
