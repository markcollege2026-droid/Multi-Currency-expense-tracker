package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.ExchangeRate
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates")
    fun getAllRates(): Flow<List<ExchangeRate>>

    @Query("SELECT * FROM exchange_rates")
    suspend fun getAllRatesSync(): List<ExchangeRate>

    @Query("SELECT * FROM exchange_rates WHERE baseCurrency = :base AND targetCurrency = :target LIMIT 1")
    suspend fun getRate(base: String, target: String): ExchangeRate?

    @Query("SELECT * FROM exchange_rates WHERE baseCurrency = :base")
    suspend fun getRatesForBase(base: String): List<ExchangeRate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: ExchangeRate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRates(rates: List<ExchangeRate>)

    /**
     * Replaces every rate of [base] with [rates] in one transaction. A crash
     * between the delete and the insert used to leave the base with no rates at
     * all; an atomic replace keeps the cache either fully old or fully new.
     */
    @Transaction
    suspend fun replaceRatesForBase(base: String, rates: List<ExchangeRate>) {
        deleteByBase(base)
        insertRates(rates)
    }

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAll()

    @Query("DELETE FROM exchange_rates WHERE baseCurrency = :base")
    suspend fun deleteByBase(base: String)
}
