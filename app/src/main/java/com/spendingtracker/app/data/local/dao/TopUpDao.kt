package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.TopUp
import kotlinx.coroutines.flow.Flow

@Dao
interface TopUpDao {
    @Query("SELECT * FROM topups ORDER BY date DESC")
    fun getAllTopUps(): Flow<List<TopUp>>

    @Query("SELECT * FROM topups ORDER BY date DESC")
    suspend fun getAllTopUpsSync(): List<TopUp>

    @Query("SELECT * FROM topups WHERE id = :id")
    suspend fun getTopUpById(id: Long): TopUp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopUp(topUp: TopUp): Long

    @Update
    suspend fun updateTopUp(topUp: TopUp)

    @Delete
    suspend fun deleteTopUp(topUp: TopUp)

    @Query("DELETE FROM topups")
    suspend fun deleteAll()
}
