package com.spendingtracker.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

data class ExchangeRateResponse(
    val result: String,
    val base_code: String,
    val time_last_update_utc: String,
    val rates: Map<String, Double>
)

data class FrankfurterResponse(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Map<String, Double>
)

interface ExchangeRateApi {
    // Using open.er-api.com free no-key API
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(@Path("base") base: String): ExchangeRateResponse
}

interface FrankfurterApi {
    @GET("latest?from={base}")
    suspend fun getLatestRatesFrankfurter(@Path("base") base: String): FrankfurterResponse
}
