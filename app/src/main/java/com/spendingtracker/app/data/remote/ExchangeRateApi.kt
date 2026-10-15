package com.spendingtracker.app.data.remote

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

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

data class FrankfurterTimeSeriesResponse(
    val amount: Double = 1.0,
    val base: String = "",
    val start_date: String = "",
    val end_date: String = "",
    val rates: Map<String, Map<String, Double>> = emptyMap()
)

interface ExchangeRateApi {
    // Using open.er-api.com free no-key API
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(@Path("base") base: String): ExchangeRateResponse

    // Historical time-series endpoint (e.g. Frankfurter API)
    @GET
    suspend fun getHistoricalRates(@Url url: String): FrankfurterTimeSeriesResponse

    // Supplemental JSON endpoint (e.g. commodities/metals rate feed)
    @GET
    suspend fun getJsonUrl(@Url url: String): JsonObject
}

interface FrankfurterApi {
    @GET("latest?from={base}")
    suspend fun getLatestRatesFrankfurter(@Path("base") base: String): FrankfurterResponse
}
