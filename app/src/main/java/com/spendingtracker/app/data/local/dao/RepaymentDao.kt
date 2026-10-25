package com.spendingtracker.app.data.local.dao

import androidx.room.*
import com.spendingtracker.app.data.local.entity.Repayment
import kotlinx.coroutines.flow.Flow

@Dao
interface RepaymentDao {
    @Query("SELECT * FROM repayments WHERE loanDebtId = :loanDebtId ORDER BY date DESC")
    fun getRepaymentsForLoan(loanDebtId: Long): Flow<List<Repayment>>

    @Query("SELECT * FROM repayments ORDER BY date DESC")
    fun observeAll(): Flow<List<Repayment>>

    @Query("SELECT * FROM repayments WHERE loanDebtId = :loanDebtId ORDER BY date DESC")
    suspend fun getRepaymentsForLoanSync(loanDebtId: Long): List<Repayment>

    @Query("SELECT * FROM repayments ORDER BY date DESC")
    suspend fun getAllRepaymentsSync(): List<Repayment>

    @Query("SELECT * FROM repayments WHERE id = :id")
    suspend fun getRepaymentById(id: Long): Repayment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepayment(repayment: Repayment): Long

    @Update
    suspend fun updateRepayment(repayment: Repayment)

    @Delete
    suspend fun deleteRepayment(repayment: Repayment)

    @Query("DELETE FROM repayments WHERE loanDebtId = :loanDebtId")
    suspend fun deleteRepaymentsForLoan(loanDebtId: Long)

    /**
     * Detaches repayments from a balance that is being deleted. Loans are shared
     * and must survive the account, so the repayments become external records
     * instead of being deleted (deleting them would silently reopen loans).
     */
    @Query("UPDATE repayments SET accountId = NULL WHERE accountId = :accountId")
    suspend fun unlinkAccount(accountId: Long)

    @Query("DELETE FROM repayments")
    suspend fun deleteAll()
}
