package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.AppSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsSync(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettings)

    @Update
    suspend fun updateSettings(settings: AppSettings)

    /**
     * Targeted write for the active account. Whole-row REPLACE (insertSettings)
     * from a stale snapshot could put an old activeAccountId back after a rate
     * refresh or theme change, so anything that only moves the pointer uses this.
     */
    @Query("UPDATE app_settings SET activeAccountId = :accountId WHERE id = 1")
    suspend fun setActiveAccount(accountId: Long)

    @Query("UPDATE app_settings SET isSetupComplete = 1 WHERE id = 1")
    suspend fun markSetupComplete()

    /**
     * Field-level writes for the settings that change on their own (theme, rate
     * refresh). Rewriting the whole row from a snapshot read earlier can put back an
     * old activeAccountId or setup flag.
     */
    @Query("UPDATE app_settings SET theme = :theme WHERE id = 1")
    suspend fun setTheme(theme: String)

    @Query("UPDATE app_settings SET lastRatesUpdate = :timestamp WHERE id = 1")
    suspend fun setLastRatesUpdate(timestamp: Long)

    @Query("UPDATE app_settings SET currencySortMode = :mode WHERE id = 1")
    suspend fun setCurrencySortMode(mode: String)

    @Query("UPDATE app_settings SET favoriteCurrencies = :json WHERE id = 1")
    suspend fun setFavoriteCurrencies(json: String?)

    @Query("UPDATE app_settings SET customCurrencyOrder = :json, currencySortMode = :mode WHERE id = 1")
    suspend fun setCustomCurrencyOrder(json: String?, mode: String)

    @Query("UPDATE app_settings SET recentCurrencies = :json WHERE id = 1")
    suspend fun setRecentCurrencies(json: String?)

    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()
}
