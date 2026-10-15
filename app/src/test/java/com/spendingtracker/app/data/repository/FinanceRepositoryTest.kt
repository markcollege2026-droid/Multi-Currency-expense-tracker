package com.spendingtracker.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.local.entity.*
import com.spendingtracker.app.di.AppModule
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class FinanceRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository
    private var mainId = 0L
    private var cashId = 0L
    private var cardId = 0L
    private fun repositoryFor(db: AppDatabase) = FinanceRepository(db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), db.accountDao(), db.recurringPaymentDao(), db.creditCardPaymentDao(), db)
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).allowMainThreadQueries().build()
        repository = repositoryFor(db)
        mainId = repository.insertAccount(Account(name = "Main", currency = "ILS", startingBalance = "1000"))
        cashId = repository.createBalanceAccount(mainId, "Cash", "CASH", BigDecimal("100"))
        cardId = repository.createBalanceAccount(mainId, "Visa", "CREDIT_CARD", BigDecimal("250"))
    }
    @After fun close() { db.close() }
    private fun expense(target: Long, amount: String) = Expense(accountId = mainId, balanceAccountId = target, amountOriginal = amount,
        currencyOriginal = "ILS", amountConverted = amount, currencyConverted = "ILS", exchangeRateUsed = "1", categoryId = null, date = 1, note = "Test", paymentMethod = "CASH")
    private suspend fun money(expected: String, id: Long) = assertEquals(0, BigDecimal(expected).compareTo(repository.balances().getValue(id)))
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
    }
    @Test fun rejectsCashOverspendUnlessExplicitlyAllowed() = runBlocking {
        rejected { repository.insertExpense(expense(cashId, "101")) }
        money("100", cashId)
        repository.insertExpense(expense(cashId, "101"), allowOverdraft = true)
        money("-1", cashId); money("1000", mainId)
    }
    @Test fun manualPaymentIsAtomicAndPreventsOverpayment() = runBlocking {
        rejected { repository.payCard(cardId, BigDecimal("251")) }
        repository.payCard(cardId, BigDecimal("100"))
        money("900", mainId); money("-150", cardId)
        repository.payCard(cardId, BigDecimal("160"), allowOverpayment = true)
        money("740", mainId); money("10", cardId)
        assertTrue(repository.getAllExpensesSync().isEmpty())
    }
    @Test fun insufficientFundsNeverMutateEitherBalance() = runBlocking {
        val largeCard = repository.createBalanceAccount(mainId, "Other", "CREDIT_CARD", BigDecimal("2000"))
        assertEquals("FAILED_INSUFFICIENT_FUNDS", repository.payCard(largeCard, BigDecimal("2000")).status)
        money("1000", mainId); money("-2000", largeCard)
    }
    @Test fun autopayRunsOnlyOncePerMonthAndCanBeRetriedManually() = runBlocking {
        val card = repository.getAccountById(cardId)!!
        repository.configureAutopay(card.copy(autopayEnabled = true, autopayDay = 15, autopayStartDate = "2026-10-01"))
        assertEquals(1, repository.processAutopay(LocalDate.of(2026, 10, 15)).size)
        assertTrue(repository.processAutopay(LocalDate.of(2026, 10, 15)).isEmpty())
        money("750", mainId); money("0", cardId)
    }
    @Test fun fixedAutopayAndFailureDeduplication() = runBlocking {
        repository.updateAccount(repository.getAccountById(mainId)!!.copy(startingBalance = "10"))
        repository.configureAutopay(repository.getAccountById(cardId)!!.copy(autopayEnabled = true, autopayDay = 15,
            autopayMode = "FIXED_AMOUNT", autopayFixedAmount = "50", autopayStartDate = "2026-10-01"))
        assertEquals("FAILED_INSUFFICIENT_FUNDS", repository.processAutopay(LocalDate.of(2026, 10, 15)).single().status)
        assertTrue(repository.processAutopay(LocalDate.of(2026, 10, 16)).isEmpty())
        money("10", mainId); money("-250", cardId)
        repository.insertTopUp(TopUp(accountId = mainId, amount = "50", date = 1, note = null))
        repository.payCard(cardId, BigDecimal("20"))
        money("40", mainId); money("-230", cardId)
        assertTrue(repository.getCardPayments().first { it.status == "FAILED_INSUFFICIENT_FUNDS" }.dismissed)
    }
    @Test fun cardFromDifferentWalletIsRejected() = runBlocking {
        val otherMain = repository.insertAccount(Account(name = "Work", currency = "ILS", startingBalance = "0"))
        rejected { repository.insertExpense(expense(cardId, "5").copy(accountId = otherMain)) }
    }
    @Test fun failedReplaceAllTransactionRollsBack() = runBlocking {
        try {
            repository.atomic { repository.eraseAll(); error("bad backup") }
        } catch (_: IllegalStateException) { }
        money("1000", mainId); money("-250", cardId)
    }
    @Test fun migrationThreeToFourPreservesLedgerAndPassesRoomValidation() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val existing = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        existing.accountDao().insertAccount(Account(id = 1, name = "Main", currency = "ILS", startingBalance = "123"))
        val sql = existing.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE old_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, currency TEXT NOT NULL, startingBalance TEXT NOT NULL, lowBalanceThreshold TEXT, createdAt INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO old_accounts SELECT id,name,currency,startingBalance,lowBalanceThreshold,createdAt FROM accounts")
        sql.execSQL("DROP TABLE accounts")
        sql.execSQL("ALTER TABLE old_accounts RENAME TO accounts")
        sql.execSQL("CREATE TABLE old_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId INTEGER NOT NULL DEFAULT 1, amountOriginal TEXT NOT NULL, currencyOriginal TEXT NOT NULL, amountConverted TEXT NOT NULL, currencyConverted TEXT NOT NULL, exchangeRateUsed TEXT NOT NULL, categoryId INTEGER, date INTEGER NOT NULL, note TEXT, paymentMethod TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO old_expenses VALUES(1,1,'20','ILS','20','ILS','1',NULL,1,'keep me','Cash',1,1)")
        sql.execSQL("DROP TABLE expenses")
        sql.execSQL("ALTER TABLE old_expenses RENAME TO expenses")
        sql.execSQL("CREATE INDEX index_expenses_accountId ON expenses(accountId)")
        sql.execSQL("DROP TABLE credit_card_payments")
        sql.version = 3
        existing.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(AppModule.MIGRATION_3_4).build()
        try {
            val migrated = repositoryFor(upgraded)
            assertEquals("MAIN", migrated.getAccountById(1)!!.type)
            assertNull(migrated.getExpenseById(1)!!.balanceAccountId)
            assertEquals("keep me", migrated.getExpenseById(1)!!.note)
            assertEquals(0, BigDecimal("103").compareTo(migrated.balances().getValue(1)))
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
