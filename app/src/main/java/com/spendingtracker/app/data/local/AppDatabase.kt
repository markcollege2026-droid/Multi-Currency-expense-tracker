package com.spendingtracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.spendingtracker.app.data.local.converters.Converters
import com.spendingtracker.app.data.local.dao.*
import com.spendingtracker.app.data.local.entity.*

@Database(
    entities = [
        Category::class,
        Expense::class,
        TopUp::class,
        AppSettings::class,
        ExchangeRate::class,
        LoanDebt::class,
        Repayment::class,
        Account::class,
        RecurringPayment::class,
        CreditCardPayment::class,
        Withdrawal::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun creditCardPaymentDao(): CreditCardPaymentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun topUpDao(): TopUpDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun loanDebtDao(): LoanDebtDao
    abstract fun repaymentDao(): RepaymentDao
    abstract fun accountDao(): AccountDao
    abstract fun recurringPaymentDao(): RecurringPaymentDao
    abstract fun withdrawalDao(): WithdrawalDao
}
