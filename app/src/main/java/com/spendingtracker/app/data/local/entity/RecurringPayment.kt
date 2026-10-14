package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.Calendar

/**
 * A scheduled payment (money out) that is automatically posted as an Expense
 * on its due dates. Lives inside a single account; loans/debts stay shared.
 */
@Entity(
    tableName = "recurring_payments",
    indices = [Index(value = ["accountId"])]
)
data class RecurringPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val title: String,
    val amountOriginal: String, // BigDecimal
    val currencyOriginal: String,
    val categoryId: Long? = null,
    val note: String? = null,
    val paymentMethod: String? = null,
    val frequency: String, // DAILY, WEEKLY, MONTHLY, YEARLY
    val startDate: Long,
    val endDate: Long? = null,
    val nextDueAt: Long,
    val lastRunAt: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun amountAsBigDecimal(): BigDecimal = try {
        BigDecimal(amountOriginal)
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

    companion object {
        const val FREQ_DAILY = "DAILY"
        const val FREQ_WEEKLY = "WEEKLY"
        const val FREQ_MONTHLY = "MONTHLY"
        const val FREQ_YEARLY = "YEARLY"

        val FREQUENCIES = listOf(FREQ_DAILY, FREQ_WEEKLY, FREQ_MONTHLY, FREQ_YEARLY)

        fun nextAfter(current: Long, frequency: String): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = current }
            when (frequency) {
                FREQ_DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                FREQ_WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7)
                FREQ_MONTHLY -> cal.add(Calendar.MONTH, 1)
                FREQ_YEARLY -> cal.add(Calendar.YEAR, 1)
            }
            return cal.timeInMillis
        }
    }
}
