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
    private fun repositoryFor(db: AppDatabase) = FinanceRepository(db.expenseDao(), db.topUpDao(), db.appSettingsDao(), db.categoryDao(), db.accountDao(), db.recurringPaymentDao(), db.creditCardPaymentDao(), db.withdrawalDao(), db)
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
            .addMigrations(AppModule.MIGRATION_1_2, AppModule.MIGRATION_2_3, AppModule.MIGRATION_3_4, AppModule.MIGRATION_4_5).build()
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
        sql.version = 3
        existing.close()
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(AppModule.MIGRATION_3_4, AppModule.MIGRATION_4_5).build()
        try {
            val migrated = repositoryFor(upgraded)
            assertEquals("MAIN", migrated.getAccountById(1)!!.type)
            assertNull(migrated.getExpenseById(1)!!.balanceAccountId)
            assertEquals("keep me", migrated.getExpenseById(1)!!.note)
            assertEquals(0, BigDecimal("103").compareTo(migrated.balances().getValue(1)))
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
