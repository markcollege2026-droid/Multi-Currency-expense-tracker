package com.spendingtracker.app.domain

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanCalculator @Inject constructor() {

    data class LoanBalance(
        val originalAmount: BigDecimal,
        val totalRepaid: BigDecimal,
        val remaining: BigDecimal,
        val progress: Float // 0..1
    )

    fun calculateRemaining(
        originalAmount: BigDecimal,
        repayments: List<BigDecimal>
    ): LoanBalance {
        val totalRepaid = repayments.fold(BigDecimal.ZERO) { acc, v -> acc + v }
        val remaining = originalAmount - totalRepaid
        val progress = if (originalAmount.compareTo(BigDecimal.ZERO) == 0) {
            1f
        } else {
            try {
                (totalRepaid.toDouble() / originalAmount.toDouble()).toFloat().coerceIn(0f, 1f)
            } catch (e: Exception) {
                0f
            }
        }
        return LoanBalance(
            originalAmount = originalAmount,
            totalRepaid = totalRepaid,
            remaining = remaining.max(BigDecimal.ZERO),
            progress = progress
        )
    }

    fun isSettled(remaining: BigDecimal): Boolean {
        return remaining <= BigDecimal.ZERO
    }
}
