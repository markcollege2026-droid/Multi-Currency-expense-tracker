package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "loan_debts")
data class LoanDebt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "DEBT" (I owe) or "LOAN" (owed to me)
    val personName: String,
    val amountOriginal: String, // BigDecimal
    val currencyOriginal: String,
    val date: Long,
    val dueDate: Long?,
    val note: String?,
    val isSettled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun amountAsBigDecimal(): BigDecimal = BigDecimal(amountOriginal)

    fun isOverdue(): Boolean {
        if (isSettled) return false
        val due = dueDate ?: return false
        return due < System.currentTimeMillis()
    }

    companion object {
        const val TYPE_DEBT = "DEBT"
        const val TYPE_LOAN = "LOAN"
    }
}
