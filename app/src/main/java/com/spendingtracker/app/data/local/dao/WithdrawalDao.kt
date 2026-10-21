package com.spendingtracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.spendingtracker.app.data.local.entity.Withdrawal
import kotlinx.coroutines.flow.Flow

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals ORDER BY date DESC")
    fun observeAll(): Flow<List<Withdrawal>>

    @Query("SELECT * FROM withdrawals ORDER BY date DESC")
    suspend fun getAllSync(): List<Withdrawal>

    @Insert
    suspend fun insert(withdrawal: Withdrawal): Long

    @Delete
    suspend fun delete(withdrawal: Withdrawal)

    @Query("DELETE FROM withdrawals WHERE fromAccountId = :accountId OR toAccountId = :accountId")
    suspend fun deleteInvolving(accountId: Long)

    @Query("DELETE FROM withdrawals")
    suspend fun deleteAll()
}
