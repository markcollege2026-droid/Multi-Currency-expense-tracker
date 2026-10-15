package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.RecurringPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringPaymentDao {
    @Query("SELECT * FROM recurring_payments ORDER BY nextDueAt ASC")
    fun getAll(): Flow<List<RecurringPayment>>

    @Query("SELECT * FROM recurring_payments WHERE accountId = :accountId ORDER BY nextDueAt ASC")
    fun getForAccount(accountId: Long): Flow<List<RecurringPayment>>

    @Query("SELECT * FROM recurring_payments ORDER BY nextDueAt ASC")
    suspend fun getAllSync(): List<RecurringPayment>

    @Query("SELECT * FROM recurring_payments WHERE id = :id")
    suspend fun getById(id: Long): RecurringPayment?

    @Insert
    suspend fun insert(payment: RecurringPayment): Long

    @Update
    suspend fun update(payment: RecurringPayment)

    @Delete
    suspend fun delete(payment: RecurringPayment)

    @Query("DELETE FROM recurring_payments WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)

    @Query("DELETE FROM recurring_payments")
    suspend fun deleteAll()
}
