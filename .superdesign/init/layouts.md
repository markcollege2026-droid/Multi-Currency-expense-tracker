# Layouts — app shell and shared chrome

## App root

`app/src/main/java/com/spendingtracker/app/MainActivity.kt`
```kotlin
setContent {
    SpendTrackerTheme(themeMode = "SYSTEM" | "LIGHT" | "DARK") { darkTheme ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppNavHost(navController, startDestination)
        }
    }
}
```
Edge-to-edge (`enableEdgeToEdge()`), splash held until the first DB read completes.

## `ui/navigation/AppNavHost.kt`
NavHost with `setup`, `main`, `add_expense?expenseId`, `add_topup?topUpId`,
`add_loan?loanId`, `recurring`. Legacy routes (`home`, `transactions`, `converter`,
`loans`, `settings`) exist only to heal restored state and redirect to `main`.

## `ui/screens/MainScaffold.kt` — persistent bottom navigation
```kotlin
Scaffold(bottomBar = {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(dims.navBarHeight)) {
            items.forEach { item ->
                Column(Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected, interactionSource, LocalIndication.current, role = Role.Tab) { selectedTab = item.route }
                    .padding(vertical = 1.dp),
                    horizontalAlignment = CenterHorizontally, verticalArrangement = Center) {
                    Icon(item.icon, null, Modifier.size(18/20.dp), tint = selected/primary else onSurfaceVariant)
                    Text(compact ? item.compactLabel : item.label, fontSize = 9/10.sp)
                }
            }
        }
    }
}) { padding -> Box(Modifier.padding(padding).consumeWindowInsets(padding)) { /* tab content, SaveableStateProvider per tab */ } }
```
Tabs: Home · History (Transactions) · Rates (Converter) · Loans · Settings.
`BackHandler` returns to Home from any other tab. Tabs keep state via
`rememberSaveableStateHolder`; there is **no** nested NavHost.

## `ui/screens/home/HomeScreen.kt` — screen skeleton
```
Scaffold(topBar = AppTopBar("Spending", actions = { account pill, quick-add, settings })) {
    if (loading) AppLoadingScreen() else LazyColumn(
        contentPadding = PaddingValues(start/end = dims.screenPadding, top = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
    ) {
        BalanceSummaryCard(...)            // hero, gradient
        Row { AppButton("+ Expense") · AppButton("+ Money", secondaryContainer) }
        BalanceAccountsPanel(...)          // total available + cash + cards
        Header("Recent transactions", trailing = AppPill("See all"))
        ExpenseItem(…) × n  |  AppEmptyState
        Header("Top categories") + rows
        SpendingTrendCard(...)             // 7-day bars
        AppSelectorRow("Recurring", …)
        AppSelectorRow("Loans", …)
    }
}
```

## Form screens (`AddExpenseScreen`, `AddTopUpScreen`, `AddLoanScreen`)
`Scaffold(topBar = AppTopBar(title, onBack), bottomBar = pinned AppButton("Save"))`
+ `LazyColumn`/`Column` of `AppTextField`, `AppSelectorRow`, chips and grids.

## Converter tab
Its own tight shell (`LocalTightUi`, `TightDimens`, `compactTypography`) with a
`ConverterBlue = Color(0xFF2563EB)` accent that predates the theme — new work should
use `MaterialTheme.colorScheme.primary` instead.

## Dense-screen rules (Qin F21, 240×320dp)
- Never exceed `dims.topBarHeight` / `dims.navBarHeight`.
- Every interactive element must be reachable by D-pad and show the amber focus ring.
- Bottom actions are pinned and use `navigationBarsPadding()` + `imePadding()`.
