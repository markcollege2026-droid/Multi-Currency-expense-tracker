package com.spendingtracker.app.data.repository

import android.app.Application
import androidx.room.Room
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.local.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * Account lifecycle: setup, switching, deleting and erasing.
 *
 * These guard the "all history was wiped on reboot" report. The data was never
 * deleted: an erase left `isSetupComplete` false, the next launch opened the setup
 * wizard, and the wizard created a second empty "Main" which became active. Every
 * screen filters by the active account, so the ledger looked gone while loans (which
 * are shared) stayed on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class AccountLifecycleTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FinanceRepository(
            db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), db.accountDao(),
            db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(), db
        )
    }

    @After
    fun close() = db.close()

    private suspend fun expense(accountId: Long, amount: String = "10", balanceAccountId: Long? = null) =
        repository.insertExpense(
            Expense(
                accountId = accountId,
                amountOriginal = amount,
                currencyOriginal = "ILS",
                amountConverted = amount,
                currencyConverted = "ILS",
                exchangeRateUsed = "1",
                categoryId = null,
                date = 1,
                note = "Test",
                paymentMethod = "CASH",
                balanceAccountId = balanceAccountId
            )
        )

    private suspend fun mainAccount(name: String, createdAt: Long) = repository.insertAccount(
        Account(name = name, currency = "ILS", startingBalance = "0", createdAt = createdAt)
    )

    // ---- setup ----

    @Test
    fun ledgerExistsMeansNoSetupEvenWhenTheFlagSaysOtherwise() = runBlocking {
        val mainId = mainAccount("Main", 1)
        // Exactly the state left behind by "erase all data" followed by creating an
        // account from Settings: an account exists, the flag says "not set up".
        repository.saveSettings(AppSettings(isSetupComplete = false, activeAccountId = 1))

        assertFalse("A ledger exists, so setup must not run", repository.needsSetup())
        assertEquals(mainId, repository.resolveActiveAccountId())
        // ...and the contradictory flag is cleared instead of booting into the wizard.
        assertTrue(repository.getSettingsSync()?.isSetupComplete == true)
    }

    @Test
    fun emptyLedgerIsTheOnlyThingThatNeedsSetup() = runBlocking {
        assertTrue(repository.needsSetup())
        assertNull(repository.resolveActiveAccountId())
        // No settings row is invented for an empty ledger.
        assertNull(repository.getSettingsSync())
    }

    @Test
    fun completingSetupTwiceKeepsOneAccount() = runBlocking {
        val first = repository.completeSetup("Main", "ILS", "100")
        val second = repository.completeSetup("Main", "ILS", "100")

        assertEquals("setup must be idempotent", first, second)
        assertEquals(1, repository.getAccountCount())
        assertEquals(first, repository.resolveActiveAccountId())
        assertEquals(first, repository.getSettingsSync()?.activeAccountId)
        assertTrue(repository.getSettingsSync()?.isSetupComplete == true)
    }

    @Test
    fun completingSetupReusesAnExistingLedgerInsteadOfAddingASecondMain() = runBlocking {
        val existing = mainAccount("Household", 5)
        val id = repository.completeSetup("Main", "USD", "50")

        assertEquals(existing, id)
        assertEquals(1, repository.getAccountCount())
        // The existing ledger's currency/name are not overwritten by the wizard.
        assertEquals("ILS", repository.getAccountById(existing)?.currency)
    }

    // ---- active pointer ----

    @Test
    fun danglingActiveAccountIsRepairedDeterministically() = runBlocking {
        val first = mainAccount("First", 1)
        mainAccount("Second", 2)
        repository.saveSettings(AppSettings(isSetupComplete = true, activeAccountId = 999))

        assertEquals(first, repository.resolveActiveAccountId())
        assertEquals("the repair is persisted", first, repository.getSettingsSync()?.activeAccountId)
    }

    @Test
    fun accountsCreatedInTheSameMillisecondStillResolveInAStableOrder() = runBlocking {
        val first = mainAccount("A", 1)
        val second = mainAccount("B", 1) // identical createdAt: id breaks the tie
        repository.saveSettings(AppSettings(isSetupComplete = true, activeAccountId = 999))

        assertEquals(minOf(first, second), repository.resolveActiveAccountId())
        assertEquals(minOf(first, second), repository.resolveActiveAccountId())
    }

    @Test
    fun switchingAccountCreatesTheMissingSettingsRow() = runBlocking {
        val mainId = mainAccount("Main", 1)
        // An interrupted erase can leave data with no settings row at all; switching
        // used to return silently and look like a dead button.
        repository.setActiveAccount(mainId)

        val settings = repository.getSettingsSync()
        assertNotNull(settings)
        assertEquals(mainId, settings?.activeAccountId)
        assertTrue(settings?.isSetupComplete == true)
    }

    @Test
    fun fieldLevelWritesCannotRevertTheActiveAccount() = runBlocking {
        val first = mainAccount("First", 1)
        val second = mainAccount("Second", 2)
        repository.setActiveAccount(second)

        // Rate refresh / theme / currency-order writes must not touch the pointer.
        repository.saveRatesTimestamp(1234L)
        repository.saveTheme("DARK")
        repository.saveCurrencySortMode("RECENT")

        val settings = repository.getSettingsSync()
        assertEquals(second, settings?.activeAccountId)
        assertEquals("DARK", settings?.theme)
        assertEquals(1234L, settings?.lastRatesUpdate)
        assertNotEquals(first, settings?.activeAccountId)
    }

    // ---- deletion ----

    @Test
    fun theLastMainAccountCannotBeDeleted() = runBlocking {
        val mainId = mainAccount("Only", 1)
        repository.saveSettings(AppSettings(isSetupComplete = true, activeAccountId = mainId))

        try {
            repository.deleteAccountCascade(repository.getAccountById(mainId)!!)
            fail("Deleting the only main account must be refused")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("only main account"))
        }
        assertEquals(1, repository.getAccountCount())
    }

    @Test
    fun deletingTheActiveAccountMovesThePointerToASurvivor() = runBlocking {
        val keeper = mainAccount("Keeper", 1)
        val doomed = mainAccount("Doomed", 2)
        repository.setActiveAccount(doomed)
        expense(doomed, "25")
        db.loanDebtDao().insertLoanDebt(
            LoanDebt(type = "DEBT", personName = "Dana", amountOriginal = "50", currencyOriginal = "ILS", date = 1, dueDate = null, note = null)
        )

        val survivor = repository.deleteAccountCascade(repository.getAccountById(doomed)!!)

        assertEquals(keeper, survivor?.id)
        assertEquals("the app must not open a deleted account", keeper, repository.getSettingsSync()?.activeAccountId)
        assertEquals(1, repository.getAccountCount())
        // Its rows go with it; shared loans stay.
        assertTrue(repository.getAllExpensesSync().isEmpty())
        assertEquals(1, db.loanDebtDao().getAllLoanDebtsSync().size)
    }

    @Test
    fun deletingACashBalanceRemovesTheExpensesPaidFromIt() = runBlocking {
        val mainId = mainAccount("Main", 1)
        val cashId = repository.createBalanceAccount(mainId, "Pocket", "CASH", BigDecimal("100"))
        expense(mainId, "30", balanceAccountId = cashId)
        expense(mainId, "5")
        assertEquals(2, repository.getAllExpensesSync().size)

        repository.deleteAccountCascade(repository.getAccountById(cashId)!!)

        // The expense paid out of the deleted cash must not survive as an orphan:
        // it would stay in History while no longer reducing any balance.
        assertEquals(1, repository.getAllExpensesSync().size)
        assertEquals(5, repository.getAllExpensesSync().single().convertedAsBigDecimal().toInt())
    }

    // ---- erase ----

    @Test
    fun anInterruptedEraseRollsBackCompletely() = runBlocking {
        val mainId = mainAccount("Main", 1)
        repository.setActiveAccount(mainId)
        expense(mainId, "40")

        // Simulates the app being killed between erasing the ledger and erasing loans.
        try {
            repository.atomic {
                repository.eraseAll()
                error("process died here")
            }
        } catch (e: IllegalStateException) {
            // expected
        }

        // All-or-nothing: no "history gone but loans (or settings) left behind" state.
        assertEquals(1, repository.getAllExpensesSync().size)
        assertEquals(1, repository.getAccountCount())
        assertNotNull(repository.getSettingsSync())
    }

    @Test
    fun eraseAndResetLeavesAUsableSetupState() = runBlocking {
        val mainId = mainAccount("Main", 1)
        repository.setActiveAccount(mainId)
        expense(mainId, "40")

        repository.atomic {
            repository.eraseAll()
            repository.resetSettingsAfterErase()
        }

        assertTrue(repository.getAllExpensesSync().isEmpty())
        assertTrue(repository.needsSetup())
        assertNull(repository.resolveActiveAccountId())
    }

    // ---- summaries ----

    @Test
    fun summariesShowWhereTheEntriesActuallyAre() = runBlocking {
        val busy = mainAccount("Old ledger", 1)
        val empty = mainAccount("New ledger", 2)
        expense(busy, "12")
        expense(busy, "8")
        repository.insertTopUp(TopUp(accountId = busy, amount = "100", date = 1, note = null))
        repository.setActiveAccount(empty)

        val summaries: Map<Long, AccountSummary> = repository.observeAccountSummaries().first()

        assertEquals(3, summaries[busy]?.entries)
        assertEquals(0, summaries[empty]?.entries)
        // The busy account still holds its money, even though it is not active.
        assertEquals(0, BigDecimal("80").compareTo(summaries[busy]!!.balance))
    }
}
