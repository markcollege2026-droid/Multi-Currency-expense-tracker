package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.LoanDebt
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDebtDao {
    @Query("SELECT * FROM loan_debts ORDER BY date DESC")
    fun getAllLoanDebts(): Flow<List<LoanDebt>>

    @Query("SELECT * FROM loan_debts WHERE type = :type ORDER BY date DESC")
    fun getLoanDebtsByType(type: String): Flow<List<LoanDebt>>

    @Query("SELECT * FROM loan_debts WHERE id = :id")
    suspend fun getLoanDebtById(id: Long): LoanDebt?

    @Query("SELECT * FROM loan_debts ORDER BY date DESC")
    suspend fun getAllLoanDebtsSync(): List<LoanDebt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoanDebt(loanDebt: LoanDebt): Long

    @Update
    suspend fun updateLoanDebt(loanDebt: LoanDebt)

    @Delete
    suspend fun deleteLoanDebt(loanDebt: LoanDebt)

    @Query("DELETE FROM loan_debts")
    suspend fun deleteAll()
}
