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
            db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(),
            db.loanDebtDao(), db.repaymentDao(), db
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

    // ---- deleting one account never affects another ----

    @Test
    fun deletingACashBalanceLeavesEveryOtherAccountUntouched() = runBlocking {
        // Two independent ledgers, each with its own cash, entries and transfer.
        val mainA = mainAccount("A", 1)
        val mainB = mainAccount("B", 2)
        repository.updateAccount(repository.getAccountById(mainA)!!.copy(startingBalance = "1000"))
        repository.updateAccount(repository.getAccountById(mainB)!!.copy(startingBalance = "2000"))
        val cashA = repository.createBalanceAccount(mainA, "Cash A", "CASH", BigDecimal("100"))
        val cashB = repository.createBalanceAccount(mainB, "Cash B", "CASH", BigDecimal("200"))
        repository.transfer(mainA, cashA, BigDecimal("50"), BigDecimal.ONE)
        repository.transfer(mainB, cashB, BigDecimal("70"), BigDecimal.ONE)
        expense(mainA, "10", balanceAccountId = cashA)
        expense(mainB, "20", balanceAccountId = cashB)
        expense(mainB, "5")
        repository.insertTopUp(TopUp(accountId = cashB, amount = "30", date = 1, note = null))
        // A loan settled out of each cash balance (loans are shared, accounts not).
        val debtA = db.loanDebtDao().insertLoanDebt(
            LoanDebt(type = "DEBT", personName = "A", amountOriginal = "40", currencyOriginal = "ILS", date = 1, dueDate = null, note = null))
        val debtB = db.loanDebtDao().insertLoanDebt(
            LoanDebt(type = "DEBT", personName = "B", amountOriginal = "60", currencyOriginal = "ILS", date = 1, dueDate = null, note = null))
        repository.settleLoan(debtA, accountId = cashA)
        repository.settleLoan(debtB, accountId = cashB)

        // Delete ONLY cash A.
        repository.deleteAccountCascade(repository.getAccountById(cashA)!!)

        val balances = repository.balances()
        // Main A only loses the transfer into the deleted cash (money returns).
        assertEquals(0, BigDecimal("1000").compareTo(balances.getValue(mainA)))
        // Ledger B is completely untouched: 2000-70 transfer-5 expense = 1925 main,
        // 200+70+30-20 expense-60 settlement = 220 cash.
        assertEquals(0, BigDecimal("1925").compareTo(balances.getValue(mainB)))
        assertEquals(0, BigDecimal("220").compareTo(balances.getValue(cashB)))
        // B's rows are all still there (A's cash expense went with cash A).
        assertEquals(2, repository.getAllExpensesSync().size)
        assertEquals(1, repository.getAllTopUpsSync().size)
        assertEquals(1, repository.getWithdrawalsSync().size)
        assertEquals(mainB, repository.getWithdrawalsSync().single().fromAccountId)
        // Shared loans survive; the settlement paid from the deleted cash becomes
        // external instead of reopening the loan, B's stays linked to cash B.
        assertEquals(2, db.loanDebtDao().getAllLoanDebtsSync().size)
        assertTrue(db.loanDebtDao().getLoanDebtById(debtA)!!.isSettled)
        val repayments = db.repaymentDao().getAllRepaymentsSync().associateBy { it.loanDebtId }
        assertNull(repayments.getValue(debtA).accountId)
        assertEquals(cashB, repayments.getValue(debtB).accountId)
    }

    @Test
    fun deletingAMainAccountLeavesTheOtherMainCompletelyUntouched() = runBlocking {
        val doomed = mainAccount("Doomed", 1)
        val keeper = mainAccount("Keeper", 2)
        repository.updateAccount(repository.getAccountById(keeper)!!.copy(startingBalance = "500"))
        val keeperCash = repository.createBalanceAccount(keeper, "Keeper cash", "CASH", BigDecimal("80"))
        val doomedCash = repository.createBalanceAccount(doomed, "Doomed cash", "CASH", BigDecimal("90"))
        expense(keeper, "15", balanceAccountId = keeperCash)
        expense(doomed, "25", balanceAccountId = doomedCash)
        expense(doomed, "7")
        repository.insertTopUp(TopUp(accountId = keeper, amount = "100", date = 1, note = null))
        repository.setActiveAccount(keeper)

        repository.deleteAccountCascade(repository.getAccountById(doomed)!!)

        assertEquals(1, repository.getAccountCount())
        assertNotNull(repository.getAccountById(keeper))
        assertNotNull(repository.getAccountById(keeperCash))
        assertNull(repository.getAccountById(doomedCash))
        val balances = repository.balances()
        assertEquals(0, BigDecimal("600").compareTo(balances.getValue(keeper)))
        assertEquals(0, BigDecimal("65").compareTo(balances.getValue(keeperCash)))
        // Only the keeper's rows remain.
        assertEquals(1, repository.getAllExpensesSync().size)
        assertEquals(keeper, repository.getAllExpensesSync().single().accountId)
        assertEquals(1, repository.getAllTopUpsSync().size)
        assertEquals(keeper, repository.resolveActiveAccountId())
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
