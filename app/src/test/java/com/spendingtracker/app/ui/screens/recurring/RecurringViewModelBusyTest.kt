package com.spendingtracker.app.ui.screens.recurring

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonObject
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.local.dao.RecurringPaymentDao
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.RecurringPayment
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.data.remote.ExchangeRateResponse
import com.spendingtracker.app.data.remote.FrankfurterTimeSeriesResponse
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.RecurringRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import java.io.IOException

/**
 * Regression tests for the recurring-payment UI operations:
 *
 * - repeated taps on Save cannot create duplicate payments (operation-level
 *   busy state refuses the second tap instead of queueing a second insert);
 * - operation failures surface as messages instead of crashing silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class RecurringViewModelBusyTest {

    private lateinit var db: AppDatabase
    private lateinit var finance: FinanceRepository
    private lateinit var rates: ExchangeRateRepository
    private var accountId = 0L

    /** Holds `insert` until the test releases it, so tap ordering is deterministic. */
    private class GatedRecurringDao(
        private val delegate: RecurringPaymentDao,
        private val gate: CompletableDeferred<Unit>
    ) : RecurringPaymentDao {
        override fun getAll(): Flow<List<RecurringPayment>> = delegate.getAll()
        override fun getForAccount(accountId: Long): Flow<List<RecurringPayment>> =
            delegate.getForAccount(accountId)

        override suspend fun getAllSync(): List<RecurringPayment> = delegate.getAllSync()
        override suspend fun getById(id: Long): RecurringPayment? = delegate.getById(id)
        override suspend fun insert(payment: RecurringPayment): Long {
            gate.await()
            return delegate.insert(payment)
        }

        override suspend fun update(payment: RecurringPayment) = delegate.update(payment)
        override suspend fun delete(payment: RecurringPayment) = delegate.delete(payment)
        override suspend fun deleteForAccount(accountId: Long) = delegate.deleteForAccount(accountId)
        override suspend fun deleteAll() = delegate.deleteAll()
    }

    private class OfflineApi : ExchangeRateApi {
        override suspend fun getLatestRates(base: String): ExchangeRateResponse =
            throw IOException("no network in tests")

        override suspend fun getHistoricalRates(url: String): FrankfurterTimeSeriesResponse =
            throw IOException("no network in tests")

        override suspend fun getJsonUrl(url: String): JsonObject =
            throw IOException("no network in tests")
    }

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        finance = FinanceRepository(
            db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), db.accountDao(),
            db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(),
            db.loanDebtDao(), db.repaymentDao(), db
        )
        rates = ExchangeRateRepository(db.exchangeRateDao(), OfflineApi())
        accountId = finance.insertAccount(
            Account(name = "Main", currency = "ILS", startingBalance = "1000")
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun RecurringViewModel.awaitState(
        predicate: (RecurringUiState) -> Boolean
    ): RecurringUiState = withTimeout(15_000) {
        var state = uiState.value
        while (!predicate(state)) {
            delay(5)
            state = uiState.value
        }
        state
    }

    private fun viewModelWith(gatedDao: RecurringPaymentDao): RecurringViewModel {
        val recurring = RecurringRepository(gatedDao, db.accountDao(), db.expenseDao(), rates, db)
        return RecurringViewModel(recurring, finance)
    }

    @Test
    fun doubleTapOnSaveCreatesExactlyOnePayment() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModelWith(GatedRecurringDao(db.recurringPaymentDao(), gate))
        vm.awaitState { it.account != null }

        vm.openAdd()
        vm.onDraftChanged(
            requireNotNull(vm.uiState.value.editing).copy(title = "Rent", amount = "10")
        )
        // Two rapid taps. The first starts the insert (which is held open by the
        // gate); the second must be refused with a busy message, not queued.
        vm.save()
        vm.save()
        val busy = vm.uiState.value
        assertNotNull("second tap must be told the first is still running", busy.message)
        assertTrue(busy.message!!.contains("Still busy"))

        // Let the first insert finish: exactly one payment exists.
        gate.complete(Unit)
        vm.awaitState { it.payments.size == 1 }
        assertEquals(1, db.recurringPaymentDao().getAllSync().size)
    }

    @Test
    fun saveFailureSurfacesAsAnErrorAndClearsBusy() = runBlocking {
        // A DAO whose insert always fails: the operation must report the error
        // (dialog stays open with the typed values) and clear the busy state.
        val failing = object : RecurringPaymentDao by db.recurringPaymentDao() {
            override suspend fun insert(payment: RecurringPayment): Long =
                throw RuntimeException("disk full")
        }
        val vm = viewModelWith(failing)
        vm.awaitState { it.account != null }
        vm.openAdd()
        vm.onDraftChanged(
            requireNotNull(vm.uiState.value.editing).copy(title = "Rent", amount = "10")
        )
        vm.save()
        val state = vm.awaitState { it.error != null }
        assertEquals("disk full", state.error)
        assertEquals(null, state.busy)
        // The dialog is still open with the user's values - nothing was lost.
        assertNotNull(state.editing)
        assertEquals(0, db.recurringPaymentDao().getAllSync().size)
    }
}
