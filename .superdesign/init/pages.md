# Pages — dependency trees

Compose screens pull UI primitives, dimension/palette composition locals, a Hilt
ViewModel and repository-backed state. Trees below are the useful `--context-file`
candidate sets for each screen.

## Home
Entry: `ui/screens/home/HomeScreen.kt`
```
- ui/screens/home/HomeScreen.kt
  - ui/components/CompactControls.kt        (AppTopBar, AppPill, AppButton, AppIconButton, AppTextField, AppSelectorRow, AppDialog, AppEmptyState, AppHairline, AppValueRow, AppSectionHeader, AppDot)
  - ui/components/CategoryIcon.kt
  - ui/components/DatePickers.kt            (via BalanceAccountsPanel dialogs)
  - ui/components/CompactControls.kt        (AppDialog reuse)
  - ui/screens/home/BalanceAccountsPanel.kt (total available, cash, cards, autopay)
  - ui/screens/home/HomeViewModel.kt        (HomeUiState: balance, week, month, accounts, recent, categories, trend)
  - ui/compact/QinLayout.kt                 (LocalAppDimens, LocalCompactUi, AppDimens)
  - ui/theme/Theme.kt · ui/theme/Type.kt · ui/theme/CompactType.kt
  - util/CurrencyFormatter.kt
```

## Transactions / History
Entry: `ui/screens/transactions/TransactionsScreen.kt`
```
- ui/screens/transactions/TransactionsScreen.kt
  - ui/components/CompactControls.kt (AppTopBar, AppTextField, AppPill, AppFilterChip, AppIconButton, AppEmptyState, AppDot, AppLoadingScreen)
  - ui/screens/transactions/TransactionsViewModel.kt (filters, search, grouped source lists)
  - ui/compact/QinLayout.kt · ui/theme/*
  - util/CurrencyFormatter.kt
  - data/local/entity/{Expense,TopUp,CreditCardPayment}.kt
```

## Converter
Entry: `ui/screens/converter/ConverterScreen.kt`
```
- ui/screens/converter/ConverterScreen.kt (1.4k lines: cards, keypad, canvas chart, pair strip, currency dialog)
  - ui/screens/converter/ConverterViewModel.kt
  - ui/components/CompactControls.kt
  - ui/theme/CompactType.kt (compactTypography), ui/compact/QinLayout.kt (TightDimens, LocalTightUi)
  - data/repository/ExchangeRateRepository.kt (HistoricalRateSeries)
```

## Loans
Entry: `ui/screens/loans/LoansScreen.kt`
```
- ui/screens/loans/LoansScreen.kt (summary card, section headers, LoanItem with progress)
  - ui/components/CompactControls.kt (AppTopBar, AppIconButton, AppSectionHeader, AppValueRow, AppEmptyState)
  - ui/screens/loans/LoansViewModel.kt (LoanWithBalance)
  - util/CurrencyFormatter.kt
- ui/screens/loans/AddLoanScreen.kt (form + Save bar)
  - ui/components/DatePickers.kt
```

## Settings
Entry: `ui/screens/settings/SettingsScreen.kt`
```
- ui/screens/settings/SettingsScreen.kt
  - ui/screens/settings/SettingsViewModel.kt (accounts CRUD, theme mode, rates, backup state)
  - ui/screens/settings/BackupControls.kt  (file export/import dialogs)
  - ui/screens/transactions/AddExpenseScreen.kt (shared CurrencyPickerDialog)
  - ui/components/CompactControls.kt (AppSectionHeader, AppFilterChip, AppValueRow, AppHairline, AppDialog, AppTextField, AppButton, AppOutlinedButton, AppTopBar)
  - ui/theme/*
```

## Add expense / Add income
Entry: `ui/screens/transactions/AddExpenseScreen.kt`, `AddTopUpScreen.kt`
```
- AddExpenseScreen.kt
  - AddExpenseViewModel.kt (amount, currency, rate override, category, account, date)
  - ui/components/CompactControls.kt · CategoryIcon.kt · DatePickers.kt
  - util/{CurrencyFormatter,CurrencyProvider,KeypadInput}.kt
- AddTopUpScreen.kt → AddTopUpViewModel.kt (same primitives)
```

## Setup
Entry: `ui/screens/setup/SetupScreen.kt`
```
- ui/screens/setup/SetupScreen.kt
  - SetupViewModel.kt
  - ui/components/CompactControls.kt (AppTopBar, AppButton, AppTextField)
  - util/CurrencyProvider.kt
```
