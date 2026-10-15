package com.spendingtracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "topups",
    indices = [Index(value = ["accountId"])]
)
data class TopUp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "1")
    val accountId: Long = 1,
    val amount: String, // BigDecimal in the account's balance currency
    val date: Long,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun amountAsBigDecimal(): BigDecimal = BigDecimal(amount)
}
