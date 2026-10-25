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
    private fun repositoryFor(db: AppDatabase) = FinanceRepository(db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), db.accountDao(), db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(), db.loanDebtDao(), db.repaymentDao(), db)
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
    @Test fun multipleCashAccountsAndForeignCurrencyCashAreAllowed() = runBlocking {
        val eurCash = repository.createBalanceAccount(mainId, "EUR wallet", "CASH", BigDecimal("50"), "eur")
        val ilsCash = repository.createBalanceAccount(mainId, "Pocket", "CASH", BigDecimal("20"))
        assertEquals("EUR", repository.getAccountById(eurCash)!!.currency)
        // Blank currency falls back to the main account currency.
        assertEquals("ILS", repository.getAccountById(ilsCash)!!.currency)
        money("1000", mainId); money("50", eurCash); money("20", ilsCash)
        // Cards stay in the main currency: payments debit main at face value.
        repository.createBalanceAccount(mainId, "Mastercard", "CREDIT_CARD", BigDecimal("10"), "EUR")
        assertEquals("ILS", repository.getAllAccountsSync().first { it.name == "Mastercard" }.currency)
    }
    @Test fun withdrawalMovesFundsWithCurrencyConversion() = runBlocking {
        val eurCash = repository.createBalanceAccount(mainId, "EUR wallet", "CASH", BigDecimal.ZERO, "EUR")
        val w = repository.withdraw(mainId, eurCash, BigDecimal("100"), BigDecimal("0.25"), "ATM")
        money("900", mainId); money("25", eurCash)
        assertMoney("100", w.amountFromAsBigDecimal())
        assertMoney("25", w.amountToAsBigDecimal())
        assertEquals("ILS", w.currencyFrom)
        assertEquals("EUR", w.currencyTo)
        assertEquals(1, repository.getWithdrawalsSync().size)
    }
    @Test fun sameCurrencyWithdrawalStoresNoConversion() = runBlocking {
        // The cash account of this fixture is in the main currency.
        val w = repository.withdraw(mainId, cashId, BigDecimal("40"), BigDecimal("3.5"), "ATM")
        assertMoney("40", w.amountToAsBigDecimal())
        // A mismatched rate is ignored rather than inflating a same-currency transfer.
        assertMoney("1", w.rateAsBigDecimal())
        money("960", mainId); money("140", cashId)
    }
    @Test fun depositFundedFromCashIsAReverseTransfer() = runBlocking {
        // Depositing into main out of a cash balance moves money between the two.
        repository.withdraw(
            fromAccountId = cashId, toAccountId = mainId,
            amountFrom = BigDecimal("40"), rate = BigDecimal.ONE, amountTo = BigDecimal("40")
        )
        money("1040", mainId); money("60", cashId)
        val stored = repository.getWithdrawalsSync().single()
        assertEquals(cashId, stored.fromAccountId)
        assertEquals(mainId, stored.toAccountId)
    }
    @Test fun foreignCashDepositConvertsIntoTheMainCurrency() = runBlocking {
        val eurCash = repository.createBalanceAccount(mainId, "EUR wallet", "CASH", BigDecimal("100"), "EUR")
        // 1 EUR = 4 ILS, depositing ₪100 spends €25 of the wallet.
        repository.withdraw(
            fromAccountId = eurCash, toAccountId = mainId,
            amountFrom = BigDecimal("25"), rate = BigDecimal("4"), amountTo = BigDecimal("100")
        )
        money("1100", mainId); money("75", eurCash)
        assertEquals("EUR", repository.getWithdrawalsSync().single().currencyFrom)
        assertEquals("ILS", repository.getWithdrawalsSync().single().currencyTo)
    }
    @Test fun transfersOutsideMainAndItsCashAreRejected() = runBlocking {
        // Cash cannot fund a card directly, and main cannot receive from itself.
        rejected {
            repository.withdraw(
                fromAccountId = cashId, toAccountId = cardId,
                amountFrom = BigDecimal("10"), rate = BigDecimal.ONE
            )
        }
        rejected {
            repository.withdraw(
                fromAccountId = mainId, toAccountId = mainId,
                amountFrom = BigDecimal("10"), rate = BigDecimal.ONE
            )
        }
        money("1000", mainId); money("100", cashId)
    }
    @Test fun withdrawalRequiresFundsAndValidCashTarget() = runBlocking {
        rejected { repository.withdraw(mainId, cardId, BigDecimal("10"), BigDecimal.ONE) }
        rejected { repository.withdraw(mainId, cashId, BigDecimal("1001"), BigDecimal.ONE) }
        rejected { repository.withdraw(mainId, cashId, BigDecimal("0"), BigDecimal.ONE) }
        money("1000", mainId); money("100", cashId); money("-250", cardId)
        assertTrue(repository.getWithdrawalsSync().isEmpty())
    }
    @Test fun deletingACashBalanceAlsoRemovesItsWithdrawals() = runBlocking {
        val eurCash = repository.createBalanceAccount(mainId, "EUR wallet", "CASH", BigDecimal.ZERO, "EUR")
        repository.withdraw(mainId, eurCash, BigDecimal("100"), BigDecimal("0.25"))
        money("900", mainId); money("25", eurCash)
        repository.deleteAccountCascade(repository.getAccountById(eurCash)!!)
        // The transfer row goes away with the balance it pointed at.
        assertTrue(repository.getWithdrawalsSync().isEmpty())
        money("1000", mainId)
    }
    @Test fun withdrawReturnsTheStoredRowSoItCanBeDeleted() = runBlocking {
        val w = repository.withdraw(mainId, cashId, BigDecimal("40"), BigDecimal.ONE)
        // @Insert does not mutate its argument, so the returned row must carry the id.
        assertTrue("withdraw() returned an unsaved row", w.id > 0)
        assertEquals(w.id, repository.getWithdrawalsSync().single().id)
        repository.deleteWithdrawal(w)
        money("1000", mainId); money("100", cashId)
    }
    @Test fun withdrawalSurvivesBackupRoundTrip() = runBlocking {
        repository.withdraw(mainId, cashId, BigDecimal("40"), BigDecimal.ONE, "Weekend cash")
        money("960", mainId); money("140", cashId)
        val exported = backups().exportAll()
        assertTrue(backups().importAll(exported).isSuccess)
        money("960", mainId); money("140", cashId)
        assertEquals(1, repository.getWithdrawalsSync().size)
        assertEquals("Weekend cash", repository.getWithdrawalsSync().single().note)
    }
    @Test fun cashAccountSetAsideFlagPersists() = runBlocking {
        val cash = repository.getAccountById(cashId)!!
        assertTrue(cash.includedInTotal)
        repository.updateBalanceAccountFields(cash.copy(includedInTotal = false))
        assertEquals(false, repository.getAccountById(cashId)!!.includedInTotal)
        val exported = backups().exportAll()
        assertTrue(backups().importAll(exported).isSuccess)
        assertEquals(false, repository.getAccountById(cashId)!!.includedInTotal)
    }
    @Test fun editingABalanceCannotRetypeMoney() = runBlocking {
        val eurCash = repository.createBalanceAccount(mainId, "EUR wallet", "CASH", BigDecimal("5"), "EUR")
        val stored = repository.getAccountById(eurCash)!!
        // Currency and parent are immutable: editing keeps the stored values.
        repository.updateBalanceAccountFields(stored.copy(
            name = "Renamed",
            currency = "JPY",
            type = "CREDIT_CARD",
            parentAccountId = 999,
            includedInTotal = false
        ))
        val updated = repository.getAccountById(eurCash)!!
        assertEquals("Renamed", updated.name)
        assertEquals("EUR", updated.currency)
        assertEquals("CASH", updated.type)
        assertEquals(mainId, updated.parentAccountId)
        assertEquals(false, updated.includedInTotal)
    }
    private fun assertMoney(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))
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
    // ---- cash -> cash transfers ----
    @Test fun cashToCashTransferMovesMoneyWithoutTouchingMain() = runBlocking {
        val pocket = repository.createBalanceAccount(mainId, "Pocket", "CASH", BigDecimal("20"))
        val w = repository.transfer(cashId, pocket, BigDecimal("40"), BigDecimal.ONE, "Rebalance")
        assertEquals(cashId, w.fromAccountId)
        assertEquals(pocket, w.toAccountId)
        money("60", cashId); money("60", pocket); money("1000", mainId)
        // A transfer is neither spending nor income.
        assertTrue(repository.getAllExpensesSync().isEmpty())
        assertTrue(repository.getAllTopUpsSync().isEmpty())
    }
    @Test fun cashToCashTransferConvertsAcrossCurrencies() = runBlocking {
        val eurCash = repository.createBalanceAccount(mainId, "EUR wallet", "CASH", BigDecimal("100"), "EUR")
        // 1 EUR = 4 ILS: 25 EUR out of the wallet lands as 100 ILS of pocket cash.
        val w = repository.transfer(eurCash, cashId, BigDecimal("25"), BigDecimal("4"))
        assertMoney("100", w.amountToAsBigDecimal())
        money("75", eurCash); money("200", cashId); money("1000", mainId)
        // Typing the target side stores it exactly, like a withdrawal.
        val back = repository.transfer(cashId, eurCash, BigDecimal("40"), BigDecimal("0.25"), amountTo = BigDecimal("10"))
        assertMoney("10", back.amountToAsBigDecimal())
        money("85", eurCash); money("160", cashId)
    }
    @Test fun transferIntoSetAsideCashKeepsBalancesAndLowersTheTotal() = runBlocking {
        val aside = repository.createBalanceAccount(mainId, "Savings cash", "CASH", BigDecimal.ZERO)
        repository.updateBalanceAccountFields(repository.getAccountById(aside)!!.copy(includedInTotal = false))
        val accounts = repository.getAllAccountsSync()
        val before = com.spendingtracker.app.domain.AccountBalances.totalAvailable(
            accounts, repository.balances(), mainId) { BigDecimal.ONE }
        // 1000 main + 100 cash - 250 card debt; the set-aside balance holds 0.
        assertMoney("850", before)
        repository.transfer(cashId, aside, BigDecimal("40"), BigDecimal.ONE)
        // Both balances are exactly right; only the counted total fell by 40.
        money("60", cashId); money("40", aside); money("1000", mainId)
        assertMoney("810", com.spendingtracker.app.domain.AccountBalances.totalAvailable(
            repository.getAllAccountsSync(), repository.balances(), mainId) { BigDecimal.ONE })
        // Moving it back restores the total - the transfer is the adjustment.
        repository.transfer(aside, cashId, BigDecimal("40"), BigDecimal.ONE)
        money("100", cashId); money("0", aside)
        assertMoney("850", com.spendingtracker.app.domain.AccountBalances.totalAvailable(
            repository.getAllAccountsSync(), repository.balances(), mainId) { BigDecimal.ONE })
    }
    @Test fun transfersBetweenDifferentLedgersAreRejected() = runBlocking {
        val otherMain = repository.insertAccount(Account(name = "Work", currency = "ILS", startingBalance = "500"))
        val otherCash = repository.createBalanceAccount(otherMain, "Work cash", "CASH", BigDecimal("50"))
        rejected { repository.transfer(cashId, otherCash, BigDecimal("10"), BigDecimal.ONE) }
        rejected { repository.transfer(mainId, otherCash, BigDecimal("10"), BigDecimal.ONE) }
        rejected { repository.transfer(cashId, cashId, BigDecimal("10"), BigDecimal.ONE) }
        money("100", cashId); money("50", otherCash)
        assertTrue(repository.getWithdrawalsSync().isEmpty())
    }
    @Test fun transferCannotOverdrawTheSourceCashBalance() = runBlocking {
        val pocket = repository.createBalanceAccount(mainId, "Pocket", "CASH", BigDecimal.ZERO)
        rejected { repository.transfer(cashId, pocket, BigDecimal("101"), BigDecimal.ONE) }
        money("100", cashId); money("0", pocket)
    }

    // ---- loan settlements ----
    private suspend fun loan(type: String, amount: String, currency: String = "ILS", person: String = "Dana"): Long =
        db.loanDebtDao().insertLoanDebt(
            LoanDebt(type = type, personName = person, amountOriginal = amount,
                currencyOriginal = currency, date = 1, dueDate = null, note = null)
        )
    @Test fun settleDebtFromCashMovesMoneyAndArchivesTheLoan() = runBlocking {
        val debt = loan(LoanDebt.TYPE_DEBT, "80")
        val repayment = repository.settleLoan(debt, accountId = cashId)!!
        money("20", cashId); money("1000", mainId)
        val stored = db.loanDebtDao().getLoanDebtById(debt)!!
        assertTrue(stored.isSettled)
        assertNotNull(stored.settledAt)
        assertEquals(repayment.id, stored.settlementRepaymentId)
        assertEquals(cashId, repayment.accountId)
        assertMoney("80", repayment.accountAmountAsBigDecimalOrNull()!!)
        // Recorded as a transaction, never as spending.
        assertTrue(repository.getAllExpensesSync().isEmpty())
        assertEquals(1, repository.getRepaymentsSync().size)
    }
    @Test fun settleLoanOwedToMeIntoCashCreditsThatBalance() = runBlocking {
        val owed = loan(LoanDebt.TYPE_LOAN, "30")
        repository.settleLoan(owed, accountId = cashId)
        money("130", cashId); money("1000", mainId)
        assertTrue(db.loanDebtDao().getLoanDebtById(owed)!!.isSettled)
        assertTrue(repository.getAllTopUpsSync().isEmpty())
    }
    @Test fun externalSettlementIsTheDefaultAndTouchesNoBalance() = runBlocking {
        val debt = loan(LoanDebt.TYPE_DEBT, "80")
        val repayment = repository.settleLoan(debt)!!
        assertNull(repayment.accountId)
        assertNull(repayment.accountAmount)
        money("100", cashId); money("1000", mainId); money("-250", cardId)
        assertTrue(db.loanDebtDao().getLoanDebtById(debt)!!.isSettled)
    }
    @Test fun partialSettlementKeepsTheLoanActiveUntilTheRestIsPaid() = runBlocking {
        val debt = loan(LoanDebt.TYPE_DEBT, "80")
        repository.recordRepayment(debt, BigDecimal("30"), accountId = cashId)
        money("70", cashId)
        assertFalse(db.loanDebtDao().getLoanDebtById(debt)!!.isSettled)
        repository.recordRepayment(debt, BigDecimal("50"), accountId = mainId)
        money("950", mainId)
        assertTrue(db.loanDebtDao().getLoanDebtById(debt)!!.isSettled)
    }
    @Test fun settlementRejectsOverpaymentInsufficientFundsAndCards() = runBlocking {
        val debt = loan(LoanDebt.TYPE_DEBT, "150")
        rejected { repository.recordRepayment(debt, BigDecimal("151")) }
        // Cash only holds 100 of the 150 owed.
        rejected { repository.recordRepayment(debt, BigDecimal("150"), accountId = cashId) }
        rejected { repository.recordRepayment(debt, BigDecimal("10"), accountId = cardId) }
        rejected { repository.recordRepayment(debt, BigDecimal.ZERO) }
        money("100", cashId); money("1000", mainId)
        assertTrue(repository.getRepaymentsSync().isEmpty())
        assertFalse(db.loanDebtDao().getLoanDebtById(debt)!!.isSettled)
    }
    @Test fun crossCurrencySettlementConvertsIntoTheAccountCurrency() = runBlocking {
        val eurCash = repository.createBalanceAccount(mainId, "EUR wallet", "CASH", BigDecimal("100"), "EUR")
        val debt = loan(LoanDebt.TYPE_DEBT, "80") // ILS
        // 1 ILS = 0.25 EUR: settling 80 ILS costs 20 EUR of the wallet.
        val repayment = repository.settleLoan(debt, accountId = eurCash, rate = BigDecimal("0.25"))!!
        assertMoney("20", repayment.accountAmountAsBigDecimalOrNull()!!)
        assertEquals("EUR", repayment.accountCurrency)
        money("80", eurCash); money("1000", mainId)
    }
    @Test fun reopeningASettledLoanReversesItsSettlement() = runBlocking {
        val debt = loan(LoanDebt.TYPE_DEBT, "80")
        repository.recordRepayment(debt, BigDecimal("30")) // earlier external part-payment stays
        repository.settleLoan(debt, accountId = cashId)
        money("50", cashId)
        repository.reopenLoan(debt)
        val stored = db.loanDebtDao().getLoanDebtById(debt)!!
        assertFalse(stored.isSettled)
        assertNull(stored.settledAt)
        assertNull(stored.settlementRepaymentId)
        // The 50 settlement is returned; the earlier 30 repayment is untouched.
        money("100", cashId)
        assertEquals(1, repository.getRepaymentsSync().size)
        assertMoney("30", repository.getRepaymentsSync().single().amountAsBigDecimal())
    }
    @Test fun deletingTheSettlementTransactionReopensTheLoan() = runBlocking {
        val owed = loan(LoanDebt.TYPE_LOAN, "45")
        val settlement = repository.settleLoan(owed, accountId = cashId)!!
        money("145", cashId)
        repository.deleteRepayment(settlement)
        money("100", cashId)
        assertFalse(db.loanDebtDao().getLoanDebtById(owed)!!.isSettled)
        assertTrue(repository.getRepaymentsSync().isEmpty())
    }
    @Test fun fullyRepaidLoanCanBeArchivedWithoutMovingNewMoney() = runBlocking {
        val debt = loan(LoanDebt.TYPE_DEBT, "60")
        // Legacy-style repayments wrote the loan off without settling it.
        db.repaymentDao().insertRepayment(Repayment(loanDebtId = debt, amount = "60", date = 2, note = null))
        assertNull(repository.settleLoan(debt, accountId = cashId))
        assertTrue(db.loanDebtDao().getLoanDebtById(debt)!!.isSettled)
        money("100", cashId)
        assertEquals(1, repository.getRepaymentsSync().size)
    }
    @Test fun backupRoundTripKeepsCashToCashTransfersAndLoanSettlements() = runBlocking {
        val pocket = repository.createBalanceAccount(mainId, "Pocket", "CASH", BigDecimal("10"))
        repository.transfer(cashId, pocket, BigDecimal("25"), BigDecimal.ONE)
        val debt = loan(LoanDebt.TYPE_DEBT, "30")
        repository.settleLoan(debt, accountId = pocket)
        money("75", cashId); money("5", pocket)
        val exported = backups().exportAll()
        assertTrue(exported.contains("\"schemaVersion\": 6"))
        assertTrue(backups().importAll(exported).isSuccess)
        money("75", cashId); money("5", pocket); money("1000", mainId)
        val storedLoan = db.loanDebtDao().getLoanDebtById(debt)!!
        assertTrue(storedLoan.isSettled)
        val settlement = repository.getRepaymentsSync().single()
        assertEquals(pocket, settlement.accountId)
        assertEquals(settlement.id, storedLoan.settlementRepaymentId)
        // Reopening still reverses the right transaction after a restore.
        repository.reopenLoan(debt)
        money("35", pocket)
    }
    @Test fun migrationFiveToSixKeepsOldRepaymentsExternal() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "v5-loan-migration.db"
        context.deleteDatabase(name)
        // Start from the current schema, then rebuild the two loan tables exactly
        // as v5 had them (same downgrade-in-place pattern as the 3->4 test).
        val existing = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        existing.loanDebtDao().insertLoanDebt(
            LoanDebt(id = 1, type = "DEBT", personName = "Dana", amountOriginal = "100",
                currencyOriginal = "ILS", date = 1, dueDate = null, note = "legacy", isSettled = true, createdAt = 1)
        )
        existing.repaymentDao().insertRepayment(Repayment(id = 1, loanDebtId = 1, amount = "40", date = 2, note = null, createdAt = 2))
        val sql = existing.openHelper.writableDatabase
        sql.execSQL("PRAGMA foreign_keys = OFF")
        sql.execSQL("CREATE TABLE old_repayments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, loanDebtId INTEGER NOT NULL, amount TEXT NOT NULL, date INTEGER NOT NULL, note TEXT, createdAt INTEGER NOT NULL, FOREIGN KEY(loanDebtId) REFERENCES loan_debts(id) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sql.execSQL("INSERT INTO old_repayments SELECT id,loanDebtId,amount,date,note,createdAt FROM repayments")
        sql.execSQL("DROP TABLE repayments")
        sql.execSQL("CREATE TABLE old_loan_debts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, personName TEXT NOT NULL, amountOriginal TEXT NOT NULL, currencyOriginal TEXT NOT NULL, date INTEGER NOT NULL, dueDate INTEGER, note TEXT, isSettled INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        sql.execSQL("INSERT INTO old_loan_debts SELECT id,type,personName,amountOriginal,currencyOriginal,date,dueDate,note,isSettled,createdAt FROM loan_debts")
        sql.execSQL("DROP TABLE loan_debts")
        sql.execSQL("ALTER TABLE old_loan_debts RENAME TO loan_debts")
        sql.execSQL("ALTER TABLE old_repayments RENAME TO repayments")
        sql.version = 5
        existing.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(AppModule.MIGRATION_5_6).build()
        try {
            val storedLoan = upgraded.loanDebtDao().getLoanDebtById(1)!!
            assertTrue(storedLoan.isSettled)
            assertNull(storedLoan.settledAt)
            assertNull(storedLoan.settlementRepaymentId)
            val repayment = upgraded.repaymentDao().getAllRepaymentsSync().single()
            assertNull("legacy repayments stay external", repayment.accountId)
            assertMoney("40", repayment.amountAsBigDecimal())
            // And the migrated ledger can settle through an account immediately.
            val migrated = repositoryFor(upgraded)
            migrated.reopenLoan(1)
            assertFalse(upgraded.loanDebtDao().getLoanDebtById(1)!!.isSettled)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }

    private fun backups(): com.spendingtracker.app.util.ExportImportManager {
        val api = java.lang.reflect.Proxy.newProxyInstance(
            com.spendingtracker.app.data.remote.ExchangeRateApi::class.java.classLoader,
            arrayOf(com.spendingtracker.app.data.remote.ExchangeRateApi::class.java)
        ) { _, _, _ -> error("Backup tests must never call the network") } as com.spendingtracker.app.data.remote.ExchangeRateApi
        val rates = ExchangeRateRepository(db.exchangeRateDao(), api)
        return com.spendingtracker.app.util.ExportImportManager(repository, LoanRepository(db.loanDebtDao(), db.repaymentDao()),
            rates, RecurringRepository(db.recurringPaymentDao(), db.accountDao(), db.expenseDao(), rates))
    }
    @Test fun backupRoundTripKeepsCashRoutingAutopayAndPayments() = runBlocking {
        repository.insertExpense(expense(cashId, "30"))
        repository.configureAutopay(repository.getAccountById(cardId)!!.copy(autopayEnabled = true, autopayDay = 20))
        repository.payCard(cardId, BigDecimal("50"))
        val exported = backups().exportAll()
        assertTrue(backups().importAll(exported).isSuccess)
        money("70", cashId); money("950", mainId); money("-200", cardId)
        assertEquals(cashId, repository.getAllExpensesSync().single().balanceAccountId)
        assertTrue(repository.getAccountById(cardId)!!.autopayEnabled)
        assertEquals(1, repository.getCardPayments().size)
    }
    @Test fun invalidImportRollsBackEvenAfterErase() = runBlocking {
        val exported = backups().exportAll()
        // Duplicate account IDs fail after the replace-all transaction has started.
        val invalid = exported.replace("\"id\": $cashId", "\"id\": $mainId")
        assertTrue(backups().importAll(invalid).isFailure)
        money("1000", mainId); money("100", cashId); money("-250", cardId)
    }
    @Test fun csvUsesActualCurrencyAndEscapesNotes() = runBlocking {
        repository.insertExpense(expense(cashId, "5").copy(note = "Food, \"market\""))
        repository.insertTopUp(TopUp(accountId = cashId, amount = "10", date = 1, note = null))
        val csv = backups().exportTransactionsCsv()
        assertTrue(csv.contains("\"Food, \"\"market\"\"\""))
        assertTrue(csv.contains("\"INCOME\""))
        assertTrue(csv.contains("\"10\",\"ILS\""))
    }
    private fun backupFile() = java.io.File.createTempFile("tracker-backup", ".stbk", RuntimeEnvironment.getApplication().cacheDir)
    private fun fileManager() = com.spendingtracker.app.util.BackupFileManager(RuntimeEnvironment.getApplication(), backups())
    @Test fun plainFileBackupRestoresBalances() = runBlocking {
        val file = backupFile()
        try {
            val uri = android.net.Uri.fromFile(file)
            fileManager().save(uri, com.spendingtracker.app.util.BackupFileManager.Format.JSON)
            assertTrue(file.readText().contains("creditCardPayments"))
            repository.insertExpense(expense(cashId, "20"))
            fileManager().restore(fileManager().read(uri))
            money("100", cashId)
        } finally { file.delete() }
    }
    @Test fun encryptedFileBackupRestoresAndWrongPasswordLeavesDataUntouched() = runBlocking {
        val file = backupFile()
        try {
            val uri = android.net.Uri.fromFile(file)
            fileManager().save(uri, com.spendingtracker.app.util.BackupFileManager.Format.ENCRYPTED, "test backup passphrase".toCharArray())
            val bytes = fileManager().read(uri)
            assertTrue(com.spendingtracker.app.util.BackupEncryption.isEncrypted(bytes))
            repository.insertExpense(expense(cashId, "20"))
            rejected { fileManager().restore(bytes, "wrong password".toCharArray()) }
            money("80", cashId)
            fileManager().restore(bytes, "test backup passphrase".toCharArray())
            money("100", cashId); money("1000", mainId); money("-250", cardId)
        } finally { file.delete() }
    }
    @Test fun invalidPlainFileDoesNotEraseExistingData() = runBlocking {
        try { fileManager().restore("not json".toByteArray()); fail("Expected invalid backup") }
        catch (_: Exception) { }
        money("1000", mainId); money("100", cashId)
    }
    @Test fun restoreReplacesCategoriesAndRateCache() = runBlocking {
        val saved = backups().exportAll()
        repository.insertCategory(Category(name = "Not in backup", iconName = "category", color = 0))
        db.exchangeRateDao().insertRate(ExchangeRate(baseCurrency = "USD", targetCurrency = "ILS", rate = "3.5"))
        assertTrue(backups().importAll(saved).isSuccess)
        assertTrue(repository.getAllCategoriesSync().isEmpty())
        assertTrue(db.exchangeRateDao().getAllRatesSync().isEmpty())
    }
    @Test fun emptyBackupFileIsRejected() = runBlocking {
        val file = backupFile()
        try { rejected { fileManager().read(android.net.Uri.fromFile(file)) } }
        finally { file.delete() }
    }
    @Test fun migrationOneThroughFourPreservesLegacyData() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val name = "legacy-migration.db"
        context.deleteDatabase(name)
        val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        val source = requireNotNull(javaClass.classLoader!!.getResourceAsStream("schema-1.json")).bufferedReader().use { it.readText() }
        val entities = org.json.JSONObject(source).getJSONObject("database").getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
        }
        legacy.execSQL("INSERT INTO app_settings VALUES(1,'ILS','500','50','SYSTEM',NULL,NULL,1)")
        legacy.execSQL("INSERT INTO categories VALUES(1,'Food','restaurant',0,1,1)")
        legacy.execSQL("INSERT INTO expenses VALUES(1,'50','ILS','50','ILS','1',1,1,'legacy note','Cash',1,1)")
        legacy.execSQL("INSERT INTO top_ups VALUES(1,'20',1,'income',1)")
        legacy.version = 1
        legacy.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(AppModule.MIGRATION_1_2, AppModule.MIGRATION_2_3, AppModule.MIGRATION_3_4, AppModule.MIGRATION_4_5, AppModule.MIGRATION_5_6).build()
        try {
            val migrated = repositoryFor(upgraded)
            assertEquals("legacy note", migrated.getExpenseById(1)!!.note)
            assertEquals(0, BigDecimal("470").compareTo(migrated.balances().getValue(1)))
        } finally { upgraded.close(); context.deleteDatabase(name) }
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
        // Loan tables go back to their v5 shape too, or the 5->6 ALTERs collide.
        sql.execSQL("DROP TABLE repayments")
        sql.execSQL("DROP TABLE loan_debts")
        sql.execSQL("CREATE TABLE loan_debts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, personName TEXT NOT NULL, amountOriginal TEXT NOT NULL, currencyOriginal TEXT NOT NULL, date INTEGER NOT NULL, dueDate INTEGER, note TEXT, isSettled INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
        sql.execSQL("CREATE TABLE repayments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, loanDebtId INTEGER NOT NULL, amount TEXT NOT NULL, date INTEGER NOT NULL, note TEXT, createdAt INTEGER NOT NULL, FOREIGN KEY(loanDebtId) REFERENCES loan_debts(id) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sql.version = 3
        existing.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(AppModule.MIGRATION_3_4, AppModule.MIGRATION_4_5, AppModule.MIGRATION_5_6).build()
        try {
            val migrated = repositoryFor(upgraded)
            assertEquals("MAIN", migrated.getAccountById(1)!!.type)
            assertNull(migrated.getExpenseById(1)!!.balanceAccountId)
            assertEquals("keep me", migrated.getExpenseById(1)!!.note)
            assertEquals(0, BigDecimal("103").compareTo(migrated.balances().getValue(1)))
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
