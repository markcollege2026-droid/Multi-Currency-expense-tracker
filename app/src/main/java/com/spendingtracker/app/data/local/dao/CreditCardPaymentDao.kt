package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.CreditCardPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardPaymentDao {
    @Query("SELECT * FROM credit_card_payments ORDER BY date DESC")
    fun observeAll(): Flow<List<CreditCardPayment>>
    @Query("SELECT * FROM credit_card_payments ORDER BY date DESC")
    suspend fun getAll(): List<CreditCardPayment>
    @Insert suspend fun insert(payment: CreditCardPayment): Long
    @Update suspend fun update(payment: CreditCardPayment)
    @Query("DELETE FROM credit_card_payments") suspend fun deleteAll()
    @Query("DELETE FROM credit_card_payments WHERE sourceAccountId = :accountId OR cardId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}
