package com.spendingtracker.app.ui.screens.converter

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.HistoricalRateSeries
import com.spendingtracker.app.domain.CurrencyConverter
import com.spendingtracker.app.util.CurrencyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class ConverterListItem(
    val info: CurrencyProvider.CurrencyInfo,
    val isTopBase: Boolean,
    val unitRateFromBase: BigDecimal,
    val inverseRateToBase: BigDecimal,
    val convertedAmount: BigDecimal,
    val changePercent24h: Double
)

enum class ConverterSubTab {
    CONVERTER,
    CHARTS
}

enum class ChartTimeframe(val label: String, val days: Int) {
    ONE_WEEK("1W", 7),
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365)
}

data class ConverterUiState(
    val subTab: ConverterSubTab = ConverterSubTab.CONVERTER,
    val selectedCodes: List<String> = DEFAULT_CODES,
    val baseAmountInput: String = "1",
    val baseAmount: BigDecimal = BigDecimal.ONE,
    val items: List<ConverterListItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdatedMillis: Long? = null,
    val statusMessage: String? = null,
    // Charts state
    val chartBaseCode: String = "USD",
    val chartTargetCode: String = "EUR",
    val chartTimeframe: ChartTimeframe = ChartTimeframe.ONE_MONTH,
    val chartSeries: HistoricalRateSeries? = null,
    val isChartLoading: Boolean = false
) {
    companion object {
        val DEFAULT_CODES = listOf("USD", "EUR", "GBP", "ILS", "JPY", "XAU", "XAG", "WTI")
    }
}

@HiltViewModel
class ConverterViewModel @Inject constructor(
    private val rateRepository: ExchangeRateRepository,
    private val financeRepository: FinanceRepository,
    private val currencyConverter: CurrencyConverter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("converter_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    init {
        val savedCodesStr = prefs.getString("selected_codes", null)
        val initialCodes = savedCodesStr
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.takeIf { it.size >= 2 }
            ?: ConverterUiState.DEFAULT_CODES

        val savedAmountStr = prefs.getString("base_amount", "1") ?: "1"
        val parsedAmount = savedAmountStr.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO } ?: BigDecimal.ONE

        val initialBase = initialCodes.firstOrNull() ?: "USD"
        val initialTarget = initialCodes.getOrNull(1) ?: "EUR"

        _uiState.value = _uiState.value.copy(
            selectedCodes = initialCodes,
            baseAmountInput = savedAmountStr,
            baseAmount = parsedAmount,
            chartBaseCode = initialBase,
            chartTargetCode = initialTarget
        )

        // Observe Room exchange rates so UI automatically updates whenever rates change
        viewModelScope.launch {
            rateRepository.getAllRates().collect { allRates ->
                val latestTs = allRates.maxOfOrNull { it.timestamp }
                recomputeConversionList(latestTs)
            }
        }

        // Initial compute + live API refresh + chart load
        viewModelScope.launch {
            recomputeConversionList(null)
            refreshRatesFromApi(showFeedback = false)
            loadChartSeries()
        }
    }

    private suspend fun recomputeConversionList(latestRateTimestamp: Long?) {
        val state = _uiState.value
        val codes = state.selectedCodes
        if (codes.isEmpty()) return

        val topBaseCode = codes.first()
        val baseAmt = state.baseAmount

        val computed = codes.mapIndexed { index, code ->
            val info = CurrencyProvider.getInfo(code)
            val isTop = index == 0
            val unitRate = if (isTop) {
                BigDecimal.ONE
            } else {
                rateRepository.getConversionRateOrFallback(topBaseCode, code)
            }
            val inverseRate = if (isTop || unitRate.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ONE
            } else {
                try {
                    BigDecimal.ONE.divide(unitRate, 8, RoundingMode.HALF_EVEN).stripTrailingZeros()
                } catch (_: Exception) {
                    BigDecimal.ONE
                }
            }
            val converted = if (isTop) {
                baseAmt
            } else {
                currencyConverter.convert(baseAmt, topBaseCode, code, unitRate)
            }
            val change24h = rateRepository.get24hChangePercent(topBaseCode, code)

            ConverterListItem(
                info = info,
                isTopBase = isTop,
                unitRateFromBase = unitRate,
                inverseRateToBase = inverseRate,
                convertedAmount = converted,
                changePercent24h = change24h
            )
        }

        _uiState.value = _uiState.value.copy(
            items = computed,
            lastUpdatedMillis = latestRateTimestamp ?: _uiState.value.lastUpdatedMillis
        )
    }

    fun selectSubTab(subTab: ConverterSubTab) {
        _uiState.value = _uiState.value.copy(subTab = subTab)
        if (subTab == ConverterSubTab.CHARTS && _uiState.value.chartSeries == null) {
            loadChartSeries()
        }
    }

    fun openChartForTarget(targetCode: String) {
        val topBase = _uiState.value.selectedCodes.firstOrNull() ?: "USD"
        val effectiveTarget = if (targetCode == topBase) {
            _uiState.value.selectedCodes.getOrNull(1) ?: "EUR"
        } else {
            targetCode
        }
        _uiState.value = _uiState.value.copy(
            subTab = ConverterSubTab.CHARTS,
            chartBaseCode = topBase,
            chartTargetCode = effectiveTarget
        )
        loadChartSeries()
    }

    fun updateBaseAmount(newAmountStr: String) {
        val cleaned = newAmountStr.trim().replace(",", "")
        val bd = cleaned.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO } ?: return
        prefs.edit().putString("base_amount", cleaned).apply()
        _uiState.value = _uiState.value.copy(
            baseAmountInput = cleaned,
            baseAmount = bd
        )
        viewModelScope.launch {
            recomputeConversionList(_uiState.value.lastUpdatedMillis)
        }
    }

    /**
     * Moves [code] to the top of the list (index 0), making it the basis for
     * conversion for all remaining items in the list.
     */
    fun promoteToTop(code: String) {
        val current = _uiState.value.selectedCodes.toMutableList()
        if (current.firstOrNull() == code) return
        current.remove(code)
        current.add(0, code)
        saveSelectedCodes(current)

        val nextSecond = current.getOrNull(1) ?: "EUR"
        _uiState.value = _uiState.value.copy(
            selectedCodes = current,
            chartBaseCode = code,
            chartTargetCode = if (_uiState.value.chartTargetCode == code) nextSecond else _uiState.value.chartTargetCode
        )
        viewModelScope.launch {
            recomputeConversionList(_uiState.value.lastUpdatedMillis)
            refreshRatesFromApi(showFeedback = false)
            loadChartSeries()
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.selectedCodes.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        saveSelectedCodes(current)

        val topCode = current.first()
        val secondCode = current.getOrNull(1) ?: "EUR"
        _uiState.value = _uiState.value.copy(
            selectedCodes = current,
            chartBaseCode = topCode,
            chartTargetCode = if (_uiState.value.chartTargetCode == topCode) secondCode else _uiState.value.chartTargetCode
        )
        viewModelScope.launch {
            recomputeConversionList(_uiState.value.lastUpdatedMillis)
            if (fromIndex == 0 || toIndex == 0) {
                refreshRatesFromApi(showFeedback = false)
                loadChartSeries()
            }
        }
    }

    fun toggleCurrencyInList(code: String) {
        val current = _uiState.value.selectedCodes.toMutableList()
        if (code in current) {
            if (current.size <= 2) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Keep at least 2 items in the converter list"
                )
                return
            }
            current.remove(code)
        } else {
            current.add(code)
        }
        saveSelectedCodes(current)
        _uiState.value = _uiState.value.copy(
            selectedCodes = current,
            chartBaseCode = current.first()
        )
        viewModelScope.launch {
            recomputeConversionList(_uiState.value.lastUpdatedMillis)
        }
    }

    fun addAndMakeTop(code: String) {
        val current = _uiState.value.selectedCodes.toMutableList()
        current.remove(code)
        current.add(0, code)
        saveSelectedCodes(current)
        _uiState.value = _uiState.value.copy(
            selectedCodes = current,
            chartBaseCode = code
        )
        viewModelScope.launch {
            recomputeConversionList(_uiState.value.lastUpdatedMillis)
            refreshRatesFromApi(showFeedback = false)
            loadChartSeries()
        }
    }

    fun removeFromList(code: String) {
        val current = _uiState.value.selectedCodes.toMutableList()
        if (current.size <= 2) {
            _uiState.value = _uiState.value.copy(
                statusMessage = "Keep at least 2 items in the converter list"
            )
            return
        }
        if (current.remove(code)) {
            saveSelectedCodes(current)
            _uiState.value = _uiState.value.copy(
                selectedCodes = current,
                chartBaseCode = current.first()
            )
            viewModelScope.launch {
                recomputeConversionList(_uiState.value.lastUpdatedMillis)
            }
        }
    }

    fun refreshRatesFromApi(showFeedback: Boolean = true) {
        val topBase = _uiState.value.selectedCodes.firstOrNull() ?: "USD"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val result = rateRepository.refreshRates(topBase)
            result.fold(
                onSuccess = { ts ->
                    val settings = financeRepository.getSettingsSync()
                    if (settings != null) {
                        financeRepository.saveSettings(settings.copy(lastRatesUpdate = ts))
                    }
                    recomputeConversionList(ts)
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        lastUpdatedMillis = ts,
                        statusMessage = if (showFeedback) "Exchange rates updated" else null
                    )
                },
                onFailure = {
                    recomputeConversionList(_uiState.value.lastUpdatedMillis)
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        statusMessage = if (showFeedback) "Using cached / offline rates" else null
                    )
                }
            )
        }
    }

    fun setChartPair(base: String, target: String) {
        _uiState.value = _uiState.value.copy(
            chartBaseCode = base,
            chartTargetCode = target
        )
        loadChartSeries()
    }

    fun swapChartPair() {
        val oldBase = _uiState.value.chartBaseCode
        val oldTarget = _uiState.value.chartTargetCode
        _uiState.value = _uiState.value.copy(
            chartBaseCode = oldTarget,
            chartTargetCode = oldBase
        )
        loadChartSeries()
    }

    fun setChartTimeframe(timeframe: ChartTimeframe) {
        if (_uiState.value.chartTimeframe == timeframe && _uiState.value.chartSeries != null) return
        _uiState.value = _uiState.value.copy(chartTimeframe = timeframe)
        loadChartSeries()
    }

    fun loadChartSeries() {
        val base = _uiState.value.chartBaseCode
        val target = _uiState.value.chartTargetCode
        val days = _uiState.value.chartTimeframe.days
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChartLoading = true)
            val series = rateRepository.getHistoricalSeries(base, target, days)
            _uiState.value = _uiState.value.copy(
                chartSeries = series,
                isChartLoading = false
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    private fun saveSelectedCodes(codes: List<String>) {
        prefs.edit().putString("selected_codes", codes.joinToString(",")).apply()
    }
}
