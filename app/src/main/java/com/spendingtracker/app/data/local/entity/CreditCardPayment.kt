package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@kotlinx.serialization.Serializable
@Entity(tableName = "credit_card_payments", indices = [Index(value = ["cardId", "autopayPeriod"], unique = true)])
data class CreditCardPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val sourceAccountId: Long,
    val amount: String,
    val date: Long,
    val status: String,
    val autopayPeriod: String? = null,
    val dismissed: Boolean = false
)
