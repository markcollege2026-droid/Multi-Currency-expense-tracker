package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "topups")
data class TopUp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: String, // BigDecimal in balance currency
    val date: Long,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun amountAsBigDecimal(): BigDecimal = BigDecimal(amount)
}
