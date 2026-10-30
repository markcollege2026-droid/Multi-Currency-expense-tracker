package com.spendingtracker.app.ui.screens.settings

import android.app.Application
import androidx.room.Room
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.local.dao.AccountDao
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.AppSettings
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import com.spendingtracker.app.data.repository.RecurringRepository
import com.spendingtracker.app.util.BackupFileManager
import com.spendingtracker.app.util.ExportImportManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Reproduction of the reported Manage accounts fault: with one account the
 * Settings row says **"0 accounts · Active: -"**, adding a second account makes
 * both appear, deleting it again leaves a stale list, and after a restart it is
 * back to zero - while Home looks normal the whole time.
 *
 * Cause under test: [SettingsViewModel]'s settings collector builds its next state
 * with `_uiState.value = _uiState.value.copy(...)`, and one of the values it copies
 * (`lowBalanceThreshold = financeRepository.getAccountById(...)`) is a **suspend**
 * call evaluated inside the argument list. The receiver (`_uiState.value`) is read
 * *before* that suspension, so when the collector resumes it applies `copy(...)` to
 * a stale snapshot and writes it back - discarding `accounts` (and summaries) that
 * the other three collectors stored in the meantime.
 *
 * [GatedAccountDao] holds the settings collector inside `getAccountById`, which
 * makes that interleaving deterministic instead of a race. The final assertion
 * describes the **buggy** behaviour on purpose (a characterisation test): it is the
 * proof, and it should be flipped when the collector is fixed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class ManageAccountsStateTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FinanceRepository

    @Before
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = repositoryWith(db.accountDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Only `getAccountById` is delayed; everything else is the real DAO. */
    private class GatedAccountDao(
        private val delegate: AccountDao,
        private val gate: CompletableDeferred<Unit>
    ) : AccountDao {
        override fun getAllAccounts(): Flow<List<Account>> = delegate.getAllAccounts()
        override suspend fun getAllAccountsSync(): List<Account> = delegate.getAllAccountsSync()
        override suspend fun getAccountById(id: Long): Account? {
            gate.await()
            return delegate.getAccountById(id)
        }
        override suspend fun getCount(): Int = delegate.getCount()
        override suspend fun insertAccount(account: Account): Long = delegate.insertAccount(account)
        override suspend fun updateAccount(account: Account) = delegate.updateAccount(account)
        override suspend fun deleteAccount(account: Account) = delegate.deleteAccount(account)
    }

    private fun repositoryWith(accounts: AccountDao): FinanceRepository = FinanceRepository(
        db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), accounts,
        db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(),
        db.loanDebtDao(), db.repaymentDao(), db
    )

    /** The API is never called here; a proxy keeps that honest if something tries. */
    private fun noNetworkApi(): ExchangeRateApi = java.lang.reflect.Proxy.newProxyInstance(
        ExchangeRateApi::class.java.classLoader,
        arrayOf(ExchangeRateApi::class.java)
    ) { _, _, _ -> error("Settings state must never touch the network") } as ExchangeRateApi

    private fun settingsViewModel(repo: FinanceRepository): SettingsViewModel {
        val rates = ExchangeRateRepository(db.exchangeRateDao(), noNetworkApi())
        val loans = LoanRepository(db.loanDebtDao(), db.repaymentDao())
        val recurring = RecurringRepository(db.recurringPaymentDao(), db.accountDao(), db.expenseDao(), rates)
        val exports = ExportImportManager(repo, loans, rates, recurring)
        val files = BackupFileManager(RuntimeEnvironment.getApplication(), exports)
        return SettingsViewModel(repo, rates, loans, exports, files, RuntimeEnvironment.getApplication())
    }

    private fun addMain(name: String, createdAt: Long): Long = runBlocking {
        repository.insertAccount(
            Account(name = name, currency = "ILS", startingBalance = "0", createdAt = createdAt)
        )
    }

    /** Waits for a concrete state instead of a frame, so the tests cannot race Room. */
    private suspend fun SettingsViewModel.awaitState(predicate: (SettingsUiState) -> Boolean): SettingsUiState =
        withTimeout(15_000) { uiState.first(predicate) }

    private suspend fun SettingsViewModel.awaitLoaded(): SettingsUiState =
        awaitState { !it.isLoading && it.accounts.isNotEmpty() }

    private fun activeLabel(state: SettingsUiState): String =
        state.accounts.firstOrNull { it.id == state.activeAccountId }?.name ?: "-"

    // ---- the reported scenario, deterministically ----

    @Test
    fun aSettingsLoadThatSuspendsDiscardsTheAccountListItRacesWith() = runBlocking {
        val id = addMain("Main", 1)
        repository.saveSettings(AppSettings(activeAccountId = id, balanceCurrency = "ILS"))

        val gate = CompletableDeferred<Unit>()
        val gated = FinanceRepository(
            db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(),
            GatedAccountDao(db.accountDao(), gate),
            db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(),
            db.loanDebtDao(), db.repaymentDao(), db
        )
        val viewModel = settingsViewModel(gated)

        // The accounts collector lands while the settings collector is parked inside
        // getAccountById - i.e. the account IS in state at this point.
        val withAccount = withTimeout(15_000) { viewModel.uiState.first { it.accounts.isNotEmpty() } }
        assertEquals(listOf("Main"), withAccount.accounts.map { it.name })
        assertTrue("settings must still be loading, which is what makes this order possible", viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        // Nothing else will be emitted, so "loaded with no accounts" is a final state.
        val settled = viewModel.awaitState { !it.isLoading }

        // The stale write wins: the account list is gone even though nothing was
        // deleted, and no further emission will put it back.
        assertEquals("the row renders '0 accounts' from here", 0, settled.accounts.size)
        assertEquals("and 'Active: -'", "-", activeLabel(settled))
        // The database still has the account - this is purely a lost state update.
        assertEquals(1, repository.getAllAccountsSync().count { it.type == "MAIN" })
        assertEquals("Home resolves the ledger from the database, not from this state", id, repository.resolveActiveAccountId())
    }

    @Test
    fun theSameLoadWithoutTheSuspensionKeepsTheList() = runBlocking {
        val id = addMain("Main", 1)
        repository.saveSettings(AppSettings(activeAccountId = id, balanceCurrency = "ILS"))

        val viewModel = settingsViewModel(repository) // no gate: no interleaving
        val settled = viewModel.awaitLoaded()

        assertEquals(1, settled.accounts.size)
        assertEquals("Main", activeLabel(settled))
    }

    // ---- the rest of the reported sequence, at state level ----

    @Test
    fun deletingTheSecondAccountLeavesOneWithNoStaleRow() = runBlocking {
        val first = addMain("Main", 1)
        repository.saveSettings(AppSettings(activeAccountId = first, balanceCurrency = "ILS"))
        val second = addMain("Travel", 2)

        val viewModel = settingsViewModel(repository)
        assertEquals(
            "both appear once a second account exists",
            2,
            viewModel.awaitState { it.accounts.size == 2 }.accounts.size
        )

        val travel = repository.getAllAccounts().first().first { it.id == second }
        viewModel.deleteAccount(travel)

        val after = withTimeout(15_000) { viewModel.uiState.first { it.accounts.size == 1 && it.busy == null } }
        assertEquals(listOf("Main"), after.accounts.map { it.name })
    }

    @Test
    fun oneAccountWithoutASettingsRowStillListsButCannotNameTheActiveOne() = runBlocking {
        val created = addMain("Main", 1) // deliberately no settings row
        val viewModel = settingsViewModel(repository)
        val settled = viewModel.awaitLoaded()

        assertEquals(1, settled.accounts.size)
        assertEquals(1L, settled.activeAccountId)
        // Only a dash when the real id is not 1 - the pointer falls back to 1.
        assertEquals(if (created == 1L) "Main" else "-", activeLabel(settled))
        assertEquals("the repository repairs the pointer for other screens", created, repository.resolveActiveAccountId())
    }

    @Test
    fun aDanglingActivePointerDoesNotHideTheList() = runBlocking {
        addMain("Main", 1)
        addMain("Travel", 2)
        repository.saveSettings(AppSettings(activeAccountId = 99, balanceCurrency = "ILS"))

        val viewModel = settingsViewModel(repository)
        val settled = viewModel.awaitState { it.accounts.size == 2 && !it.isLoading }

        assertEquals(2, settled.accounts.size)
        assertEquals("-", activeLabel(settled))
        assertNotNull(repository.resolveActiveAccountId())
    }
}
