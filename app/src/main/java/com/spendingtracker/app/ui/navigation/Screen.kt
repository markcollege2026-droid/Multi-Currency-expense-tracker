package com.spendingtracker.app.ui.navigation

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Main : Screen("main")
    object Home : Screen("home")
    object Transactions : Screen("transactions")
    object Converter : Screen("converter")
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
    object AddLoan : Screen("add_loan?loanId={loanId}&loanType={loanType}") {
        /**
         * [loanType] preselects "I owe" vs "Owed to me" for a new loan, chosen
         * before the form opens. Editing always uses the stored loan's type.
         */
        fun createRoute(loanId: Long? = null, loanType: String? = null): String {
            val parts = mutableListOf<String>()
            if (loanId != null) parts.add("loanId=$loanId")
            if (loanType != null) parts.add("loanType=$loanType")
            return if (parts.isEmpty()) "add_loan" else "add_loan?" + parts.joinToString("&")
        }
    }
    object Recurring : Screen("recurring")
}
