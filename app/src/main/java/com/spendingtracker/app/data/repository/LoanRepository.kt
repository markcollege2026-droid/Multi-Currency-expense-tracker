package com.spendingtracker.app.data.repository

import com.spendingtracker.app.data.local.dao.LoanDebtDao
import com.spendingtracker.app.data.local.dao.RepaymentDao
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.Repayment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanRepository @Inject constructor(
    private val loanDebtDao: LoanDebtDao,
    private val repaymentDao: RepaymentDao
) {
    fun getAllLoanDebts(): Flow<List<LoanDebt>> = loanDebtDao.getAllLoanDebts()
    fun getDebts(): Flow<List<LoanDebt>> = loanDebtDao.getLoanDebtsByType(LoanDebt.TYPE_DEBT)
    fun getLoans(): Flow<List<LoanDebt>> = loanDebtDao.getLoanDebtsByType(LoanDebt.TYPE_LOAN)

    suspend fun getAllLoanDebtsSync(): List<LoanDebt> = loanDebtDao.getAllLoanDebtsSync()

    suspend fun getLoanDebtById(id: Long): LoanDebt? = loanDebtDao.getLoanDebtById(id)

    suspend fun insertLoanDebt(loanDebt: LoanDebt): Long = loanDebtDao.insertLoanDebt(loanDebt)
    suspend fun updateLoanDebt(loanDebt: LoanDebt) = loanDebtDao.updateLoanDebt(loanDebt)
    suspend fun deleteLoanDebt(loanDebt: LoanDebt) = loanDebtDao.deleteLoanDebt(loanDebt)

    fun getRepaymentsForLoan(loanId: Long): Flow<List<Repayment>> = repaymentDao.getRepaymentsForLoan(loanId)
    suspend fun getRepaymentsForLoanSync(loanId: Long): List<Repayment> = repaymentDao.getRepaymentsForLoanSync(loanId)

    suspend fun insertRepayment(repayment: Repayment): Long = repaymentDao.insertRepayment(repayment)
    suspend fun deleteRepayment(repayment: Repayment) = repaymentDao.deleteRepayment(repayment)

    suspend fun eraseAll() {
        repaymentDao.deleteAll()
        loanDebtDao.deleteAll()
    }
}
