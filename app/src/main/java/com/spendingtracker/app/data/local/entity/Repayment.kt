package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "repayments",
    foreignKeys = [
        ForeignKey(
            entity = LoanDebt::class,
            parentColumns = ["id"],
            childColumns = ["loanDebtId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Repayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loanDebtId: Long,
    val amount: String, // BigDecimal in same currency as loan
    val date: Long,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun amountAsBigDecimal(): BigDecimal = BigDecimal(amount)
}
