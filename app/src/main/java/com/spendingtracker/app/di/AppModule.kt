package com.spendingtracker.app.di

import android.content.Context
import androidx.room.Room
import com.spendingtracker.app.data.local.AppDatabase
import com.spendingtracker.app.data.remote.ExchangeRateApi
import com.spendingtracker.app.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
    fun provideOkHttp(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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
