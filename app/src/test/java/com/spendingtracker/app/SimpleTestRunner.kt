package com.spendingtracker.app

import com.spendingtracker.app.domain.BalanceCalculator
import com.spendingtracker.app.domain.CurrencyConverter
import com.spendingtracker.app.domain.LoanCalculator
import java.math.BigDecimal

fun main() {
    println("Running unit tests...")
    
    // CurrencyConverter tests
    val converter = CurrencyConverter()
    val amount = BigDecimal("20.00")
    val rate = BigDecimal("4.02")
    val converted = converter.convert(amount, "EUR", "ILS", rate)
    check(converted.setScale(2, java.math.RoundingMode.HALF_EVEN) == BigDecimal("80.40")) { "Currency conversion failed" }
    println("✓ CurrencyConverter test passed: 20 EUR * 4.02 = 80.40 ILS")

    // BalanceCalculator tests
    val balanceCalc = BalanceCalculator()
    val starting = BigDecimal("1000")
    val topUps = listOf(BigDecimal("200"), BigDecimal("300"))
    val expenses = listOf(BigDecimal("150"), BigDecimal("50"))
    val balanceResult = balanceCalc.calculate(starting, topUps, expenses)
    check(balanceResult.balance == BigDecimal("1300")) { "Balance calculation failed: expected 1300, got ${balanceResult.balance}" }
    println("✓ BalanceCalculator test passed: 1000 + 500 - 200 = 1300")

    // Negative balance allowed
    val negativeResult = balanceCalc.calculate(BigDecimal("100"), emptyList(), listOf(BigDecimal("200")))
    check(negativeResult.balance == BigDecimal("-100")) { "Negative balance failed" }
    check(balanceCalc.isNegative(negativeResult.balance)) { "isNegative failed" }
    println("✓ Negative balance test passed")

    // Low balance warning
    check(balanceCalc.isLowBalance(BigDecimal("50"), BigDecimal("100"))) { "Low balance check failed" }
    println("✓ Low balance warning test passed")

    // LoanCalculator tests
    val loanCalc = LoanCalculator()
    val loanResult = loanCalc.calculateRemaining(BigDecimal("1000"), listOf(BigDecimal("200"), BigDecimal("300")))
    check(loanResult.remaining == BigDecimal("500")) { "Loan remaining failed" }
    check(loanResult.progress == 0.5f) { "Loan progress failed" }
    println("✓ LoanCalculator test passed: 1000 - 500 = 500 remaining, 50% progress")

    // Repayment
    val settled = loanCalc.calculateRemaining(BigDecimal("500"), listOf(BigDecimal("500")))
    check(settled.remaining == BigDecimal.ZERO) { "Settled check failed" }
    check(loanCalc.isSettled(settled.remaining)) { "isSettled failed" }
    println("✓ Loan settled test passed")

    println("\nAll unit tests passed!")
}
