# Routes

Navigation is Compose Navigation (`ui/navigation/Screen.kt` + `AppNavHost.kt`).
There is no URL routing, no web layer.

| Route pattern | Screen file | Layout | Notes |
|---|---|---|---|
| `setup` | `ui/screens/setup/SetupScreen.kt` | own `Scaffold` + pinned Continue bar | First launch only: account name, starting balance, currency picker |
| `main` | `ui/screens/MainScaffold.kt` | bottom-nav shell | Host of the five tabs below, state kept per tab |
| └ tab Home | `ui/screens/home/HomeScreen.kt` | shell tab | Hero balance, quick actions, accounts, recent, top categories, trend |
| └ tab Transactions | `ui/screens/transactions/TransactionsScreen.kt` | shell tab | Search, type chips, day groups, expense/top-up/card-payment rows |
| └ tab Converter | `ui/screens/converter/ConverterScreen.kt` | shell tab (tight density) | Base/target currencies, keypad, rate chart, quick pairs |
| └ tab Loans | `ui/screens/loans/LoansScreen.kt` | shell tab | Summary, “I owe”, “Owed to me”, progress rows |
| └ tab Settings | `ui/screens/settings/SettingsScreen.kt` | shell tab | Accounts, appearance, data/backup, rates, about |
| `add_expense?expenseId={id}` | `ui/screens/transactions/AddExpenseScreen.kt` | full screen + Save bar | Create or edit an expense |
| `add_topup?topUpId={id}` | `ui/screens/transactions/AddTopUpScreen.kt` | full screen + Save bar | Create or edit income |
| `add_loan?loanId={id}` | `ui/screens/loans/AddLoanScreen.kt` | full screen + Save bar | Create or edit a loan/debt |
| `recurring` | `ui/screens/recurring/RecurringScreen.kt` | full screen | Scheduled payments list + pause/resume |
| legacy `home`/`transactions`/`converter`/`loans`/`settings` | — | — | Redirect to `main` (stale saved state healing) |

Dialogs (not routes): `QuickAddBottomSheet`, `AdjustBalanceDialog`,
`CurrencyPickerDialog`, category picker/creation, card payment & autopay dialogs in
`BalanceAccountsPanel`, backup save/restore dialogs in `BackupControls`.
