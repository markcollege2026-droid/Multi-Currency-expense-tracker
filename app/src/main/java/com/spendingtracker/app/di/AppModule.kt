package com.spendingtracker.app.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
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
