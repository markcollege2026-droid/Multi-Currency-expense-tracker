package com.spendingtracker.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    val id: Int = 1,
    // Legacy mirrors of the first account (kept for schema compatibility);
    // money values now live on the Account entity.
    val balanceCurrency: String = "USD",
    val startingBalance: String = "0", // BigDecimal
    val lowBalanceThreshold: String? = null,
    val theme: String = "SYSTEM", // LIGHT, DARK, SYSTEM
    val defaultCategoryId: Long? = null,
    val lastRatesUpdate: Long? = null,
    val isSetupComplete: Boolean = false,
    // Currency order customization
    val currencySortMode: String = "ALPHABETICAL", // ALPHABETICAL, CUSTOM, RECENT, FAVORITES
    val customCurrencyOrder: String? = null, // JSON array of currency codes in custom order
    val favoriteCurrencies: String? = null, // JSON array of favorite codes (shown at top)
    val recentCurrencies: String? = null, // JSON array of recently used codes (most recent first)
    @ColumnInfo(defaultValue = "1")
    val activeAccountId: Long = 1
)
