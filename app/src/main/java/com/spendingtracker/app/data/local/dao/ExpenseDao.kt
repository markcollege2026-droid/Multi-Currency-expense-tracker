package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpensesSync(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getExpensesByDateRange(start: Long, end: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY date DESC")
    fun getExpensesByCategory(categoryId: Long): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT SUM(CAST(amountConverted AS REAL)) FROM expenses")
    suspend fun getTotalConverted(): Double?

    @Query("SELECT * FROM expenses WHERE date >= :since ORDER BY date DESC")
    suspend fun getExpensesSince(since: Long): List<Expense>
}
