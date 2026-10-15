package com.spendingtracker.app.ui.navigation

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Main : Screen("main")
    object Home : Screen("home")
    object Transactions : Screen("transactions")
    object Loans : Screen("loans")
    object Settings : Screen("settings")
    object AddExpense : Screen("add_expense?expenseId={expenseId}") {
        fun createRoute(expenseId: Long? = null): String {
            return if (expenseId != null) "add_expense?expenseId=$expenseId" else "add_expense"
        }
    }
    object AddTopUp : Screen("add_topup?topUpId={topUpId}") {
        fun createRoute(topUpId: Long? = null): String {
            return if (topUpId != null) "add_topup?topUpId=$topUpId" else "add_topup"
        }
    }
    object AddLoan : Screen("add_loan?loanId={loanId}") {
        fun createRoute(loanId: Long? = null): String {
            return if (loanId != null) "add_loan?loanId=$loanId" else "add_loan"
        }
    }
    object Recurring : Screen("recurring")
}
