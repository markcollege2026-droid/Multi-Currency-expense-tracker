package com.spendingtracker.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.spendingtracker.app.ui.navigation.Screen
import com.spendingtracker.app.ui.screens.home.HomeScreen
import com.spendingtracker.app.ui.screens.loans.LoansScreen
import com.spendingtracker.app.ui.screens.settings.SettingsScreen
import com.spendingtracker.app.ui.screens.transactions.TransactionsScreen

data class BottomNavItem(val label: String, val route: String, val icon: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTopUp: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    onAddLoan: () -> Unit,
    onEditLoan: (Long) -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem("Home", Screen.Home.route, "🏠"),
        BottomNavItem("Transactions", Screen.Transactions.route, "📋"),
        BottomNavItem("Loans", Screen.Loans.route, "💳"),
        BottomNavItem("Settings", Screen.Settings.route, "⚙️")
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(item.icon) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = Screen.Home.route, modifier = Modifier.padding(padding)) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToAddExpense = onNavigateToAddExpense,
                    onNavigateToAddTopUp = onNavigateToAddTopUp,
                    onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToLoans = { navController.navigate(Screen.Loans.route) },
                    onEditExpense = onEditExpense,
                    onEditTopUp = onEditTopUp
                )
            }
            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    onBack = { navController.popBackStack() },
                    onEditExpense = onEditExpense,
                    onEditTopUp = onEditTopUp,
                    onAddExpense = onNavigateToAddExpense,
                    onAddTopUp = onNavigateToAddTopUp
                )
            }
            composable(Screen.Loans.route) {
                LoansScreen(
                    onBack = { navController.popBackStack() },
                    onAddLoan = onAddLoan,
                    onEditLoan = onEditLoan
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
