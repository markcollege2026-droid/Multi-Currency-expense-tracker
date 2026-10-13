package com.spendingtracker.app.domain

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceCalculator @Inject constructor() {

    data class BalanceResult(
        val balance: BigDecimal,
        val totalTopUps: BigDecimal,
        val totalExpenses: BigDecimal,
        val startingBalance: BigDecimal
    )

    fun calculate(
        startingBalance: BigDecimal,
        topUps: List<BigDecimal>,
        expensesConverted: List<BigDecimal>
    ): BalanceResult {
        val totalTopUps = topUps.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val totalExpenses = expensesConverted.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val balance = startingBalance + totalTopUps - totalExpenses
        return BalanceResult(
            balance = balance,
            totalTopUps = totalTopUps,
            totalExpenses = totalExpenses,
            startingBalance = startingBalance
        )
    }

    fun isLowBalance(balance: BigDecimal, threshold: BigDecimal?): Boolean {
        if (threshold == null) return false
        return balance < threshold
    }

    fun isNegative(balance: BigDecimal): Boolean {
        return balance < BigDecimal.ZERO
    }
}
