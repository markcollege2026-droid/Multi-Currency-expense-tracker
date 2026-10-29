package com.spendingtracker.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.spendingtracker.app.ui.screens.loans.AddLoanScreen
import com.spendingtracker.app.ui.screens.recurring.RecurringScreen
import com.spendingtracker.app.ui.screens.setup.SetupScreen
import com.spendingtracker.app.ui.screens.transactions.AddExpenseScreen
import com.spendingtracker.app.ui.screens.transactions.AddTopUpScreen
import com.spendingtracker.app.util.AppEntry

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    /**
     * Entry action requested from outside the app (widget). Applied once the main
     * graph is on screen and reported back through [onActionConsumed].
     */
    pendingAction: String? = null,
    onActionConsumed: () -> Unit = {}
) {
    // Back from a form/overlay screen: pop, and if there is nothing to pop
    // (e.g. after a stale state restore) fall back to Main instead of dead-end.
    val safeOnBack: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigate(Screen.Main.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

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
            // The widget's "add expense" button lands here. Consumed first so a
            // recomposition (or rotation) cannot navigate twice.
            LaunchedEffect(pendingAction) {
                if (pendingAction == AppEntry.ACTION_ADD_EXPENSE) {
                    onActionConsumed()
                    navController.navigate(Screen.AddExpense.createRoute())
                }
            }
            com.spendingtracker.app.ui.screens.MainScaffold(
                onNavigateToAddExpense = { navController.navigate(Screen.AddExpense.createRoute()) },
                onNavigateToAddTopUp = { navController.navigate(Screen.AddTopUp.createRoute()) },
                onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                onEditExpense = { id -> navController.navigate(Screen.AddExpense.createRoute(id)) },
                onEditTopUp = { id -> navController.navigate(Screen.AddTopUp.createRoute(id)) },
                onAddLoan = { type -> navController.navigate(Screen.AddLoan.createRoute(loanType = type)) },
                onEditLoan = { id -> navController.navigate(Screen.AddLoan.createRoute(id)) }
            )
        }
        // Legacy routes from before the persistent bottom nav. They are no
        // longer navigated to, but stale saved state (process death, app
        // update) can still restore onto them - redirect to Main so users can
        // never get stranded on a tab screen without the bottom navigation.
        composable(Screen.Home.route) { RedirectHomeToMain(navController, Screen.Home.route) }
        composable(Screen.Transactions.route) { RedirectHomeToMain(navController, Screen.Transactions.route) }
        composable(Screen.Converter.route) { RedirectHomeToMain(navController, Screen.Converter.route) }
        composable(Screen.Loans.route) { RedirectHomeToMain(navController, Screen.Loans.route) }
        composable(Screen.Settings.route) { RedirectHomeToMain(navController, Screen.Settings.route) }
        composable(
            route = Screen.AddExpense.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId")?.takeIf { it != -1L }
            AddExpenseScreen(
                expenseId = expenseId,
                onBack = safeOnBack
            )
        }
        composable(
            route = Screen.AddTopUp.route,
            arguments = listOf(navArgument("topUpId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val topUpId = backStackEntry.arguments?.getLong("topUpId")?.takeIf { it != -1L }
            AddTopUpScreen(
                topUpId = topUpId,
                onBack = safeOnBack
            )
        }
        composable(Screen.Recurring.route) {
            RecurringScreen(
                onBack = safeOnBack
            )
        }
        composable(
            route = Screen.AddLoan.route,
            arguments = listOf(
                navArgument("loanId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("loanType") { type = NavType.StringType; defaultValue = ""; nullable = true }
            )
        ) { backStackEntry ->
            val loanId = backStackEntry.arguments?.getLong("loanId")?.takeIf { it != -1L }
            // Null when editing or on stale restores; the form then keeps its default.
            val loanType = backStackEntry.arguments?.getString("loanType")?.takeIf { it.isNotBlank() }
            AddLoanScreen(
                loanId = loanId,
                initialType = loanType,
                onBack = safeOnBack
            )
        }
    }
}

/**
 * Heals stale restores onto legacy routes: replaces the entry with Main so the
 * bottom navigation is always available.
 */
@Composable
private fun RedirectHomeToMain(navController: NavHostController, legacyRoute: String) {
    LaunchedEffect(Unit) {
        navController.navigate(Screen.Main.route) {
            popUpTo(legacyRoute) { inclusive = true }
            launchSingleTop = true
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
