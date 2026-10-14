package com.spendingtracker.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.spendingtracker.app.ui.screens.home.HomeScreen
import com.spendingtracker.app.ui.screens.loans.AddLoanScreen
import com.spendingtracker.app.ui.screens.loans.LoansScreen
import com.spendingtracker.app.ui.screens.recurring.RecurringScreen
import com.spendingtracker.app.ui.screens.settings.SettingsScreen
import com.spendingtracker.app.ui.screens.setup.SetupScreen
import com.spendingtracker.app.ui.screens.transactions.AddExpenseScreen
import com.spendingtracker.app.ui.screens.transactions.AddTopUpScreen
import com.spendingtracker.app.ui.screens.transactions.TransactionsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }
        // Main scaffold with persistent bottom nav for Home/Transactions/Loans/Settings
        composable(Screen.Main.route) {
            com.spendingtracker.app.ui.screens.MainScaffold(
                onNavigateToAddExpense = { navController.navigate(Screen.AddExpense.createRoute()) },
                onNavigateToAddTopUp = { navController.navigate(Screen.AddTopUp.createRoute()) },
                onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                onEditExpense = { id -> navController.navigate(Screen.AddExpense.createRoute(id)) },
                onEditTopUp = { id -> navController.navigate(Screen.AddTopUp.createRoute(id)) },
                onAddLoan = { navController.navigate(Screen.AddLoan.createRoute()) },
                onEditLoan = { id -> navController.navigate(Screen.AddLoan.createRoute(id)) }
            )
        }
        // Keep old routes for deep links/back compat - redirect to Main
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAddExpense = { navController.navigate(Screen.AddExpense.createRoute()) },
                onNavigateToAddTopUp = { navController.navigate(Screen.AddTopUp.createRoute()) },
                onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                onNavigateToTransactions = { navController.navigate(Screen.Main.route) },
                onNavigateToSettings = { navController.navigate(Screen.Main.route) },
                onNavigateToLoans = { navController.navigate(Screen.Main.route) },
                onEditExpense = { id -> navController.navigate(Screen.AddExpense.createRoute(id)) },
                onEditTopUp = { id -> navController.navigate(Screen.AddTopUp.createRoute(id)) }
            )
        }
        composable(Screen.Transactions.route) {
            TransactionsScreen(
                onBack = { navController.popBackStack() },
                onEditExpense = { id -> navController.navigate(Screen.AddExpense.createRoute(id)) },
                onEditTopUp = { id -> navController.navigate(Screen.AddTopUp.createRoute(id)) },
                onAddExpense = { navController.navigate(Screen.AddExpense.createRoute()) },
                onAddTopUp = { navController.navigate(Screen.AddTopUp.createRoute()) }
            )
        }
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId")?.takeIf { it != -1L }
            AddExpenseScreen(
                expenseId = expenseId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.AddTopUp.route,
            arguments = listOf(navArgument("topUpId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val topUpId = backStackEntry.arguments?.getLong("topUpId")?.takeIf { it != -1L }
            AddTopUpScreen(
                topUpId = topUpId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Loans.route) {
            LoansScreen(
                onBack = { navController.popBackStack() },
                onAddLoan = { navController.navigate(Screen.AddLoan.createRoute()) },
                onEditLoan = { id -> navController.navigate(Screen.AddLoan.createRoute(id)) }
            )
        }
        composable(Screen.Recurring.route) {
            RecurringScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.AddLoan.route,
            arguments = listOf(navArgument("loanId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val loanId = backStackEntry.arguments?.getLong("loanId")?.takeIf { it != -1L }
            AddLoanScreen(
                loanId = loanId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
