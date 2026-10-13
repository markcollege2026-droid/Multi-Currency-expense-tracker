package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    val balanceCurrency: String = "USD",
    val startingBalance: String = "0", // BigDecimal
    val lowBalanceThreshold: String? = null,
    val theme: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val defaultCategoryId: Long? = null,
    val lastRatesUpdate: Long? = null,
    val isSetupComplete: Boolean = false
)
