package com.spendingtracker.app.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Early schema exports used top_ups and a required category FK. Rebuild without losing rows.
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            fun hasTable(name: String): Boolean = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'").use { it.moveToFirst() }
            if (hasTable("top_ups") && !hasTable("topups")) db.execSQL("ALTER TABLE top_ups RENAME TO topups")
            db.execSQL("CREATE TABLE expenses_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, amountOriginal TEXT NOT NULL, currencyOriginal TEXT NOT NULL, amountConverted TEXT NOT NULL, currencyConverted TEXT NOT NULL, exchangeRateUsed TEXT NOT NULL, categoryId INTEGER, date INTEGER NOT NULL, note TEXT, paymentMethod TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            db.execSQL("INSERT INTO expenses_new SELECT id, amountOriginal, currencyOriginal, amountConverted, currencyConverted, exchangeRateUsed, categoryId, date, note, paymentMethod, createdAt, updatedAt FROM expenses")
            db.execSQL("DROP TABLE expenses")
            db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")
            val columns = db.query("PRAGMA table_info(app_settings)").use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
            }
            for (column in listOf("currencySortMode", "customCurrencyOrder", "favoriteCurrencies", "recentCurrencies")) {
                if (column !in columns) db.execSQL("ALTER TABLE app_settings ADD COLUMN $column TEXT" + if (column == "currencySortMode") " NOT NULL DEFAULT 'ALPHABETICAL'" else "")
            }
            db.execSQL("DELETE FROM exchange_rates WHERE id NOT IN (SELECT MAX(id) FROM exchange_rates GROUP BY baseCurrency, targetCurrency)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exchange_rates_baseCurrency_targetCurrency ON exchange_rates(baseCurrency, targetCurrency)")
        }
    }

    /**
     * v2 -> v3: introduces multiple accounts and recurring payments.
     * Creates the accounts/recurring_payments tables, links expenses and top-ups
     * to an account (existing rows become part of the auto-created "Main" account),
     * and records the active account in app_settings.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `accounts` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`currency` TEXT NOT NULL, " +
                    "`startingBalance` TEXT NOT NULL, " +
                    "`lowBalanceThreshold` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO `accounts` (`id`, `name`, `currency`, `startingBalance`, `lowBalanceThreshold`, `createdAt`) " +
                    "VALUES (1, 'Main', " +
                    "COALESCE((SELECT `balanceCurrency` FROM `app_settings` WHERE `id` = 1), 'USD'), " +
                    "COALESCE((SELECT `startingBalance` FROM `app_settings` WHERE `id` = 1), '0'), " +
                    "(SELECT `lowBalanceThreshold` FROM `app_settings` WHERE `id` = 1), " +
                    System.currentTimeMillis() + ")"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_payments` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`amountOriginal` TEXT NOT NULL, " +
                    "`currencyOriginal` TEXT NOT NULL, " +
                    "`categoryId` INTEGER, " +
                    "`note` TEXT, " +
                    "`paymentMethod` TEXT, " +
                    "`frequency` TEXT NOT NULL, " +
                    "`startDate` INTEGER NOT NULL, " +
                    "`endDate` INTEGER, " +
                    "`nextDueAt` INTEGER NOT NULL, " +
                    "`lastRunAt` INTEGER, " +
                    "`isActive` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_recurring_payments_accountId` ON `recurring_payments` (`accountId`)"
            )
            db.execSQL("ALTER TABLE `expenses` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_expenses_accountId` ON `expenses` (`accountId`)"
            )
            db.execSQL("ALTER TABLE `topups` ADD COLUMN `accountId` INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_topups_accountId` ON `topups` (`accountId`)"
            )
            db.execSQL(
                "ALTER TABLE `app_settings` ADD COLUMN `activeAccountId` INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'MAIN'")
            db.execSQL("ALTER TABLE accounts ADD COLUMN parentAccountId INTEGER")
            db.execSQL("ALTER TABLE accounts ADD COLUMN creditLimit TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN autopayEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE accounts ADD COLUMN autopayDay INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE accounts ADD COLUMN autopayMode TEXT NOT NULL DEFAULT 'FULL_BALANCE'")
            db.execSQL("ALTER TABLE accounts ADD COLUMN autopayFixedAmount TEXT")
            db.execSQL("ALTER TABLE accounts ADD COLUMN autopayStartDate TEXT")
            db.execSQL("ALTER TABLE expenses ADD COLUMN balanceAccountId INTEGER")
            db.execSQL("CREATE TABLE credit_card_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, cardId INTEGER NOT NULL, sourceAccountId INTEGER NOT NULL, amount TEXT NOT NULL, date INTEGER NOT NULL, status TEXT NOT NULL, autopayPeriod TEXT, dismissed INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX index_credit_card_payments_cardId_autopayPeriod ON credit_card_payments(cardId, autopayPeriod)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Cash accounts can be "set aside" so their money is excluded from totals.
            db.execSQL("ALTER TABLE accounts ADD COLUMN includedInTotal INTEGER NOT NULL DEFAULT 1")
            // Main -> cash withdrawals, stored in both currencies for conversion.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `withdrawals` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`fromAccountId` INTEGER NOT NULL, " +
                    "`toAccountId` INTEGER NOT NULL, " +
                    "`amountFrom` TEXT NOT NULL, " +
                    "`amountTo` TEXT NOT NULL, " +
                    "`currencyFrom` TEXT NOT NULL, " +
                    "`currencyTo` TEXT NOT NULL, " +
                    "`exchangeRate` TEXT NOT NULL, " +
                    "`date` INTEGER NOT NULL, " +
                    "`note` TEXT, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
        }
    }

    @Provides
    fun provideCreditCardPaymentDao(db: AppDatabase) = db.creditCardPaymentDao()

    @Provides
    fun provideWithdrawalDao(db: AppDatabase) = db.withdrawalDao()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
    }

    @Provides
    fun provideCategoryDao(db: AppDatabase) = db.categoryDao()

    @Provides
    fun provideExpenseDao(db: AppDatabase) = db.expenseDao()

    @Provides
    fun provideTopUpDao(db: AppDatabase) = db.topUpDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase) = db.appSettingsDao()

    @Provides
    fun provideExchangeRateDao(db: AppDatabase) = db.exchangeRateDao()

    @Provides
    fun provideLoanDebtDao(db: AppDatabase) = db.loanDebtDao()

    @Provides
    fun provideRepaymentDao(db: AppDatabase) = db.repaymentDao()

    @Provides
    fun provideAccountDao(db: AppDatabase) = db.accountDao()

    @Provides
    fun provideRecurringPaymentDao(db: AppDatabase) = db.recurringPaymentDao()

    @Provides
    @Singleton
    fun provideOkHttp(@ApplicationContext context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            // Tight timeouts so a slow/hanging rate API can't stall conversions
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // Small HTTP cache so repeated rate fetches can be served from disk
            .cache(Cache(File(context.cacheDir, "http_cache"), 10L * 1024L * 1024L))
            .apply {
                // Log network traffic only in debug builds
                if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideExchangeRateApi(client: OkHttpClient): ExchangeRateApi {
        return Retrofit.Builder()
            .baseUrl(Constants.EXCHANGE_RATE_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExchangeRateApi::class.java)
    }
}
