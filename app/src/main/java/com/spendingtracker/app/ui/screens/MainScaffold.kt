package com.spendingtracker.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.spendingtracker.app.ui.navigation.Screen
import com.spendingtracker.app.ui.screens.converter.ConverterScreen
import com.spendingtracker.app.ui.screens.home.HomeScreen
import com.spendingtracker.app.ui.screens.loans.LoansScreen
import com.spendingtracker.app.ui.screens.settings.SettingsScreen
import com.spendingtracker.app.ui.screens.transactions.TransactionsScreen

data class BottomNavItem(val label: String, val route: String, val icon: String)

/**
 * State-based bottom navigation (no nested NavHost back stacks).
 *
 * Nested per-tab back stacks with saveState/restoreState can corrupt across
 * process recreation and app updates (e.g. a restored tab stack that no longer
 * contains the start destination), which made the Home button appear to jump
 * to Settings and made Home unreachable. A single selected-tab state cannot
 * desync: every tab tap shows exactly one screen, and system back always
 * returns to Home first. Each tab's UI state (scroll, search, dialogs) is kept
 * across switches via [rememberSaveableStateHolder].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTopUp: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    onAddLoan: () -> Unit,
    onEditLoan: (Long) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(Screen.Home.route) }
    val stateHolder = rememberSaveableStateHolder()
    val items = listOf(
        BottomNavItem("Home", Screen.Home.route, "🏠"),
        BottomNavItem("Transactions", Screen.Transactions.route, "📋"),
        BottomNavItem("Converter", Screen.Converter.route, "💱"),
        BottomNavItem("Loans", Screen.Loans.route, "💳"),
        BottomNavItem("Settings", Screen.Settings.route, "⚙️")
    )

    // System back: from any tab go to Home first; on Home let the system
    // handle it (the activity finishes), which is standard bottom-nav UX and
    // guarantees Home is always one tap/back away.
    BackHandler(enabled = selectedTab != Screen.Home.route) {
        selectedTab = Screen.Home.route
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item.route,
                        onClick = { selectedTab = item.route },
                        icon = { Text(item.icon) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                Screen.Home.route -> stateHolder.SaveableStateProvider(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToAddExpense = onNavigateToAddExpense,
                        onNavigateToAddTopUp = onNavigateToAddTopUp,
                        onNavigateToRecurring = onNavigateToRecurring,
                        onNavigateToTransactions = { selectedTab = Screen.Transactions.route },
                        onNavigateToSettings = { selectedTab = Screen.Settings.route },
                        onNavigateToLoans = { selectedTab = Screen.Loans.route },
                        onEditExpense = onEditExpense,
                        onEditTopUp = onEditTopUp
                    )
                }
                Screen.Transactions.route -> stateHolder.SaveableStateProvider(Screen.Transactions.route) {
                    TransactionsScreen(
                        onEditExpense = onEditExpense,
                        onEditTopUp = onEditTopUp,
                        onAddExpense = onNavigateToAddExpense,
                        onAddTopUp = onNavigateToAddTopUp
                    )
                }
                Screen.Converter.route -> stateHolder.SaveableStateProvider(Screen.Converter.route) {
                    ConverterScreen()
                }
                Screen.Loans.route -> stateHolder.SaveableStateProvider(Screen.Loans.route) {
                    LoansScreen(
                        onAddLoan = onAddLoan,
                        onEditLoan = onEditLoan
                    )
                }
                Screen.Settings.route -> stateHolder.SaveableStateProvider(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
