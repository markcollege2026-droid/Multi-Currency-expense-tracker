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
import kotlinx.coroutines.delay
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
 * Reproduction of the reported Manage accounts fault: with one account the Settings
 * row says **"0 accounts · Active: -"**, adding a second account makes both appear,
 * deleting it again can leave the deleted row behind, and after a restart it is back
 * to zero - while Home looks normal the whole time.
 *
 * Cause: [SettingsViewModel]'s settings collector writes
 * `_uiState.value = _uiState.value.copy(...)`, and one of the values it copies -
 * `lowBalanceThreshold = financeRepository.getAccountById(...)` - is a **suspend**
 * call evaluated inside the argument list. The receiver (`_uiState.value`, the whole
 * current state object) is read *before* that suspension, so when the collector
 * resumes it applies `copy(...)` to a stale snapshot and writes it back, discarding
 * `accounts`, `accountSummaries`, `busy`, `toast` and anything else the other
 * collectors or button handlers stored while it was parked.
 *
 * Home is unaffected because it has no such writer: its state is produced by a
 * single `combine` over the database flows, so there is no second copy to clobber.
 *
 * [GatedAccountDao] holds the settings collector inside `getAccountById`, which makes
 * the interleaving deterministic instead of a race. The first test reproduces that
 * interleaving and asserts the **fixed** outcome: the collector must not lose the
 * account list (or a toast) that was stored while it was suspended. Before the fix
 * the same test failed with `expected:<1> but was:<0>`.
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

    /** Only `getAccountById` is delayed; every other call is the real DAO. */
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

    private suspend fun SettingsViewModel.awaitState(
        predicate: (SettingsUiState) -> Boolean
    ): SettingsUiState = withTimeout(15_000) { uiState.first(predicate) }

    /** Polls the database, so a test can assert data truth without depending on state. */
    private suspend fun awaitMainAccounts(count: Int) {
        withTimeout(15_000) {
            while (repository.getAllAccountsSync().count { it.type == "MAIN" } != count) delay(5)
        }
    }

    private fun activeLabel(state: SettingsUiState): String =
        state.accounts.firstOrNull { it.id == state.activeAccountId }?.name ?: "-"

    // ---- the reported fault, deterministically ----

    @Test
    fun aSettingsLoadThatSuspendsKeepsTheAccountListItRacesWith() = runBlocking {
        val id = addMain("Main", 1)
        repository.saveSettings(AppSettings(activeAccountId = id, balanceCurrency = "ILS"))

        val gate = CompletableDeferred<Unit>()
        val viewModel = settingsViewModel(repositoryWith(GatedAccountDao(db.accountDao(), gate)))

        // The accounts collector lands while the settings collector is parked inside
        // getAccountById - the account IS in the state at this point.
        val withAccount = withTimeout(15_000) { viewModel.uiState.first { it.accounts.isNotEmpty() } }
        assertEquals(listOf("Main"), withAccount.accounts.map { it.name })
        assertTrue(
            "settings must still be loading, which is what makes this order possible",
            viewModel.uiState.value.isLoading
        )

        gate.complete(Unit)
        val settled = viewModel.awaitState { !it.isLoading }

        // The fix: the late settings write must not resurrect the pre-suspension
        // snapshot. This is the exact state that rendered "0 accounts - Active: -".
        assertEquals("the account list must survive the settings write", 1, settled.accounts.size)
        assertEquals("and the row can name the active account", "Main", activeLabel(settled))
        assertEquals(id, settled.activeAccountId)
    }

    /**
     * The same window also swallowed `busy` and `toast`, which is why deleting an
     * account could look like it did nothing: the completion toast was written and
     * then reverted by this collector before a frame was drawn.
     */
    @Test
    fun aToastWrittenWhileTheSettingsLoadSuspendsIsNotSwallowed() = runBlocking {
        val id = addMain("Main", 1)
        repository.saveSettings(AppSettings(activeAccountId = id, balanceCurrency = "ILS"))

        val gate = CompletableDeferred<Unit>()
        val viewModel = settingsViewModel(repositoryWith(GatedAccountDao(db.accountDao(), gate)))

        // Switch account while the collector is parked: it writes a toast.
        withTimeout(15_000) { viewModel.uiState.first { it.accounts.isNotEmpty() } }
        viewModel.switchAccount(viewModel.uiState.value.accounts.first())
        assertEquals("Using Main", viewModel.uiState.value.toast)

        gate.complete(Unit)
        val settled = viewModel.awaitState { !it.isLoading }
        assertEquals("the toast must survive to be shown", "Using Main", settled.toast)
    }

    /**
     * The collector is not dead - only the write that loses to it is. A later database
     * change re-emits and the list appears, which is exactly what the user sees after
     * creating a second account.
     */
    @Test
    fun aLaterEmissionRestoresTheListSoTheFaultLooksIntermittent() = runBlocking {
        val id = addMain("Main", 1)
        repository.saveSettings(AppSettings(activeAccountId = id, balanceCurrency = "ILS"))
        val viewModel = settingsViewModel(repository)
        viewModel.awaitState { !it.isLoading }

        // Any account write triggers a fresh emission.
        addMain("Travel", 2)

        val refreshed = viewModel.awaitState { it.accounts.size == 2 }
        assertEquals(listOf("Main", "Travel"), refreshed.accounts.map { it.name })
    }

    // ---- the rest of the reported sequence, at data level ----

    @Test
    fun deletingTheSecondAccountRemovesItFromTheDatabase() = runBlocking {
        val first = addMain("Main", 1)
        repository.saveSettings(AppSettings(activeAccountId = first, balanceCurrency = "ILS"))
        val second = addMain("Travel", 2)

        val viewModel = settingsViewModel(repository)
        val travel = repository.getAllAccounts().first().first { it.id == second }
        viewModel.deleteAccount(travel)

        awaitMainAccounts(1)
        assertEquals(
            "the active pointer is moved off the deleted account",
            first,
            repository.getSettingsSync()?.activeAccountId
        )
        // Ends with an assertion, not a bare expression: JUnit requires a void method.
        assertTrue(viewModel.awaitState { it.busy == null && !it.isLoading }.busy == null)
    }

    @Test
    fun aDanglingActivePointerIsRepairedInTheData() = runBlocking {
        addMain("Main", 1)
        addMain("Travel", 2)
        repository.saveSettings(AppSettings(activeAccountId = 99, balanceCurrency = "ILS"))

        val repaired = repository.resolveActiveAccountId()
        assertNotNull("the pointer must be repaired for every other screen", repaired)
        assertEquals(repaired, repository.getSettingsSync()?.activeAccountId)
    }
}
