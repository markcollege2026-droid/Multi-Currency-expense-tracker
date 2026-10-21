# Extractable components

Candidates that repeat across screens and should be treated as reusable design
entities (either already shared, or worth lifting into `ui/components/`).

## Layout

## AppTopBar
- Source: `ui/components/CompactControls.kt`
- Category: layout
- Description: Status-bar-aware top bar with optional back button and trailing actions
- Extractable props: `title`, `onBack`, `actions`
- Hardcoded: surface colour, heights from `LocalAppDimens`, bold titleMedium style

## BottomNav
- Source: `ui/screens/MainScaffold.kt`
- Category: layout
- Description: Five-tab bottom navigation with D-pad-selectable items
- Extractable props: `selectedTab`, tab items (Home/History/Rates/Loans/Settings)
- Hardcoded: icon vectors (core set only), compact labels, 9/10sp labels, heights from dimens

## ScreenScaffold / pinned save bar
- Source: `ui/screens/transactions/AddExpenseScreen.kt`, `setup/SetupScreen.kt`
- Category: layout
- Description: Screen with fixed bottom action area that respects IME + nav bars
- Extractable props: `title`, `onBack`, primary action label/enabled
- Hardcoded: paddings, elevation 3dp

## Basic

## BalanceHero
- Source: `ui/screens/home/HomeScreen.kt` (`BalanceSummaryCard`)
- Category: basic
- Description: Gradient summary card with balance, period spend, warnings, adjust action
- Extractable props: `balance`, `currency`, `week`, `month`, `isNegative`, `isLow`, `ratesStale`
- Hardcoded: gradient stops, eyebrow label, warning glyph

## SectionCard
- Source: new — proposed `ui/components/AppCards.kt`
- Category: basic
- Description: Bordered container used to group rows under a section header
- Extractable props: `title`, `trailing`, `content`
- Hardcoded: 1dp outline, 16dp radius, 16/12dp padding

## ListRow (transaction / loan / settings)
- Source: `HomeScreen.ExpenseItem`, `TransactionsScreen.ExpenseRow/TopUpRow`, `LoansScreen.LoanItem`, `SettingsScreen` rows
- Category: basic
- Description: Leading badge or dot, title + meta lines, trailing amount/action
- Extractable props: row kind, amount, currency, timestamp, onClick
- Hardcoded: badge size, hairline separators, tabular numerals

## AppButton / AppOutlinedButton
- Source: `ui/components/CompactControls.kt`
- Category: basic
- Description: Filled and outlined actions with amber D-pad focus ring
- Extractable props: `enabled`, `colors`, `shape`, `content`
- Hardcoded: 12dp radius, compact content padding, bold label

## AppTextField / AppSelectorRow
- Source: `ui/components/CompactControls.kt`
- Category: basic
- Description: Form input with label above, and a read-only picker row
- Extractable props: `label`, `value`, `placeholder`, `isError`, `supportingText`, `keyboardType`
- Hardcoded: border weights, 10dp radius, supporting text style

## AppFilterChip / AppPill
- Source: `ui/components/CompactControls.kt`
- Category: basic
- Description: Pill filters and small action/status pills
- Extractable props: `selected`, `label`, `onClick`
- Hardcoded: full radius, 30–40dp heights

## AppDialog
- Source: `ui/components/CompactControls.kt`
- Category: basic
- Description: Screen-height-capped dialog with scrolling body and pinned actions
- Extractable props: `title`, `confirmLabel`, `dismissLabel`, `confirmEnabled`
- Hardcoded: 94% max height, scroll body, action row alignment

## AppEmptyState
- Source: `ui/components/CompactControls.kt`
- Category: basic
- Description: Centered hint shown when a list has no rows
- Extractable props: `text`
- Hardcoded: padding, muted bodySmall colour
