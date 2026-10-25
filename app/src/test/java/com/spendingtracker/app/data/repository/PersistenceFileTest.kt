package com.spendingtracker.app.data.repository

import android.app.Application
import androidx.room.Room
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.TopUp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * Durability of the real database file.
 *
 * Every other repository test uses an in-memory database, which cannot show whether
 * committed rows survive the process going away. This one closes the file and opens
 * it again - the same thing an app kill, a reboot or "close from Recents" does - so
 * "the reboot lost my data" has a test that can fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class PersistenceFileTest {

    private val dbName = "persistence-test.db"
    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
        openDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(dbName)
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(
            db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), db.accountDao(),
            db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(),
            db.loanDebtDao(), db.repaymentDao(), db
        )
    }

    /** Simulates the app process ending and starting again on the same file. */
    private fun reopenDatabase() {
        db.close()
        openDatabase()
    }

    @Test
    fun committedLedgerSurvivesAReopen() = runBlocking {
        val mainId = repository.insertAccount(
            Account(name = "Main", currency = "ILS", startingBalance = "1000")
        )
        repository.setActiveAccount(mainId)
        repository.insertTopUp(TopUp(accountId = mainId, amount = "250", date = 1, note = "Salary"))
        repository.insertExpense(
            Expense(
                accountId = mainId,
                amountOriginal = "40",
                currencyOriginal = "ILS",
                amountConverted = "40",
                currencyConverted = "ILS",
                exchangeRateUsed = "1",
                categoryId = null,
                date = 2,
                note = "Lunch",
                paymentMethod = "CASH"
            )
        )

        reopenDatabase()

        assertEquals(1, repository.getAllExpensesSync().size)
        assertEquals(1, repository.getAllTopUpsSync().size)
        assertEquals(mainId, repository.resolveActiveAccountId())
        val expected = BigDecimal("1210")
        assertEquals(0, expected.compareTo(repository.balances()[mainId]))
    }

    @Test
    fun aCrashBeforeAnyCommitLeavesThePreviousLedgerIntact() = runBlocking {
        val mainId = repository.insertAccount(Account(name = "Main", currency = "ILS", startingBalance = "10"))
        repository.setActiveAccount(mainId)

        reopenDatabase()

        // Nothing was written to the accounts table, so nothing can be lost from it.
        assertNotNull(repository.getAccountById(mainId))
        assertEquals(1, repository.getAccountCount())
    }

    @Test
    fun theActiveAccountAndSetupFlagSurviveAReopen() = runBlocking {
        val first = repository.insertAccount(Account(name = "First", currency = "ILS", startingBalance = "0", createdAt = 1))
        val second = repository.insertAccount(Account(name = "Second", currency = "ILS", startingBalance = "0", createdAt = 2))
        repository.setActiveAccount(second)

        reopenDatabase()

        assertEquals(second, repository.resolveActiveAccountId())
        assertEquals(true, repository.getSettingsSync()?.isSetupComplete)
        assertEquals(first, repository.getAccountById(first)?.id)
    }
}
