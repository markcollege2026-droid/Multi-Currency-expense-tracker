package com.spendingtracker.app.data.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonObject
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.ExchangeRate
import com.spendingtracker.app.data.local.entity.RecurringPayment
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.data.remote.ExchangeRateResponse
import com.spendingtracker.app.data.remote.FrankfurterTimeSeriesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.math.BigDecimal

/**
 * Regression tests for recurring-payment generation ("PHASE 4 - Recurring
 * payment duplication"): posting an occurrence and advancing its schedule must
 * be atomic and idempotent, so a crash, a retry or a second concurrent run can
 * never post the same occurrence twice - and a missing rate must never post a
 * guessed amount.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class RecurringRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var rates: ExchangeRateRepository
    private lateinit var repository: RecurringRepository
    private var accountId = 0L

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
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rates = ExchangeRateRepository(db.exchangeRateDao(), OfflineApi())
        repository = RecurringRepository(db.recurringPaymentDao(), db.accountDao(), db.expenseDao(), rates, db)
        accountId = db.accountDao().insertAccount(
            Account(name = "Main", currency = "ILS", startingBalance = "1000")
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun addPayment(
        amount: String = "10",
        currency: String = "ILS",
        nextDueAt: Long = 1_000L,
        endDate: Long? = null,
        frequency: String = RecurringPayment.FREQ_DAILY
    ): Long = db.recurringPaymentDao().insert(
        RecurringPayment(
            accountId = accountId,
            title = "Rent",
            amountOriginal = amount,
            currencyOriginal = currency,
            frequency = frequency,
            startDate = nextDueAt,
            endDate = endDate,
            nextDueAt = nextDueAt
        )
    )

    private suspend fun expenses() = db.expenseDao().getAllExpensesSync()

    @Test
    fun postsDueOccurrenceOnceAndAdvancesTheSchedule() = runBlocking {
        addPayment(nextDueAt = 1_000)
        val created = repository.processDuePayments(now = 2_000)
        assertEquals(1, created)
        assertEquals(1, expenses().size)
        val payment = db.recurringPaymentDao().getAllSync().single()
        assertTrue("schedule must advance past the posted occurrence", payment.nextDueAt > 1_000)
    }

    @Test
    fun repeatedRunsNeverDuplicateAnOccurrence() = runBlocking {
        addPayment(nextDueAt = 1_000)
        repository.processDuePayments(now = 2_000)
        // The exact crash/retry scenario: the same run repeats. Before the fix,
        // an expense posted without the schedule advance was posted again.
        repository.processDuePayments(now = 2_000)
        repository.processDuePayments(now = 3_000)
        assertEquals(1, expenses().size)
    }

    @Test
    fun interruptedBeforeCommitPostsNothingAndRerunPostsOnce() = runBlocking {
        addPayment(nextDueAt = 1_000)
        // An interrupted run rolls back its whole transaction, so the state is
        // exactly "nothing posted, schedule not advanced" - rerun from there.
        assertEquals(0, expenses().size)
        val created = repository.processDuePayments(now = 2_000)
        assertEquals(1, created)
        assertEquals(1, expenses().size)
    }

    @Test
    fun concurrentRunsCannotPostTheSameOccurrenceTwice() = runBlocking {
        addPayment(nextDueAt = 1_000)
        val runs = (1..4).map {
            async(Dispatchers.Default) { repository.processDuePayments(now = 2_000) }
        }.awaitAll()
        assertEquals("exactly one run must post the occurrence", 1, runs.sum())
        assertEquals(1, expenses().size)
    }

    @Test
    fun missingRateLeavesThePaymentDueAndPostsOnceTheRateArrives() = runBlocking {
        addPayment(amount = "10", currency = "EUR", nextDueAt = 1_000)
        assertEquals(0, repository.processDuePayments(now = 2_000))
        assertEquals("never post a guessed amount", 0, expenses().size)
        val payment = db.recurringPaymentDao().getAllSync().single()
        assertEquals("schedule must not advance without a posting", 1_000L, payment.nextDueAt)

        db.exchangeRateDao().insertRate(
            ExchangeRate(baseCurrency = "EUR", targetCurrency = "ILS", rate = "4", timestamp = 1)
        )
        assertEquals(1, repository.processDuePayments(now = 2_000))
        val expense = expenses().single()
        assertEquals("10", expense.amountOriginal)
        assertEquals("40", BigDecimal(expense.amountConverted).stripTrailingZeros().toPlainString())
        assertEquals("4", expense.exchangeRateUsed)
        // And the now-posted occurrence is not repeated.
        assertEquals(0, repository.processDuePayments(now = 3_000))
        assertEquals(1, expenses().size)
    }

    @Test
    fun endDateDeactivatesTheSchedule() = runBlocking {
        addPayment(nextDueAt = 1_000, endDate = 500)
        repository.processDuePayments(now = 2_000)
        val payment = db.recurringPaymentDao().getAllSync().single()
        assertFalse(payment.isActive)
        assertEquals(0, expenses().size)
    }

    @Test
    fun catchUpIsCappedPerRunAndContinuesNextRun() = runBlocking {
        // A daily payment long overdue: at most 50 postings per run.
        addPayment(nextDueAt = 1_000)
        assertEquals(50, repository.processDuePayments(now = 1_000L + 100L * 24 * 60 * 60 * 1000L))
        assertEquals(50, expenses().size)
        assertEquals(50, repository.processDuePayments(now = 1_000L + 200L * 24 * 60 * 60 * 1000L))
        assertEquals(100, expenses().size)
    }

    @Test
    fun malformedAmountNeverPostsAZeroExpense() = runBlocking {
        addPayment(amount = "not-a-number", nextDueAt = 1_000)
        repository.processDuePayments(now = 2_000)
        assertEquals("a 0-value expense is financial garbage", 0, expenses().size)
    }

    @Test
    fun orphanedPaymentIsDeactivated() = runBlocking {
        val id = addPayment(nextDueAt = 1_000)
        db.accountDao().deleteAccount(Account(id = accountId, name = "Main", currency = "ILS", startingBalance = "1000"))
        repository.processDuePayments(now = 2_000)
        val payment = db.recurringPaymentDao().getById(id)!!
        assertFalse("payment without an account must stop firing", payment.isActive)
    }
}
