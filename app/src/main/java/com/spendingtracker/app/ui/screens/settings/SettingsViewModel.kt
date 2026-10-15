package com.spendingtracker.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendingtracker.app.data.local.entity.AppSettings
import com.spendingtracker.app.data.repository.ExchangeRateRepository
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.data.repository.LoanRepository
import com.spendingtracker.app.util.CurrencyProvider
import com.spendingtracker.app.util.ExportImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings? = null,
    val currencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    val orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    val lowBalanceThreshold: String = "",
    val theme: String = "SYSTEM",
    val currencySortMode: String = "ALPHABETICAL",
    val customOrder: List<String> = emptyList(),
    val favoriteCurrencies: List<String> = emptyList(),
    val recentCurrencies: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val showChangeCurrencyDialog: Boolean = false,
    val showEraseDialog: Boolean = false,
    val showCurrencyOrderDialog: Boolean = false,
    val newCurrency: String = "",
    val exportJson: String? = null,
    val exportCsv: String? = null,
    val message: String? = null,
    val error: String? = null,
    val accounts: List<com.spendingtracker.app.data.local.entity.Account> = emptyList(),
    val activeAccountId: Long = 1
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository,
    private val loanRepository: LoanRepository,
    private val exportImportManager: ExportImportManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            financeRepository.getSettings().collect { settings ->
                val sortMode = settings?.currencySortMode ?: "ALPHABETICAL"
                val customOrder = settings?.customCurrencyOrder?.let { json ->
                    try {
                        json.trim().removePrefix("[").removeSuffix("]")
                            .split(",").map { it.trim().trim('"', '\'', ' ') }.filter { it.isNotBlank() }
                    } catch (e: Exception) { emptyList() }
                } ?: emptyList()
                val favorites = settings?.favoriteCurrencies?.let { json ->
                    try {
                        json.trim().removePrefix("[").removeSuffix("]")
                            .split(",").map { it.trim().trim('"', '\'', ' ') }.filter { it.isNotBlank() }
                    } catch (e: Exception) { emptyList() }
                } ?: emptyList()
                val recents = settings?.recentCurrencies?.let { json ->
                    try {
                        json.trim().removePrefix("[").removeSuffix("]")
                            .split(",").map { it.trim().trim('"', '\'', ' ') }.filter { it.isNotBlank() }
                    } catch (e: Exception) { emptyList() }
                } ?: emptyList()

                val ordered = CurrencyProvider.getOrderedCurrencies(
                    sortMode = sortMode,
                    customOrderJson = settings?.customCurrencyOrder,
                    favoriteJson = settings?.favoriteCurrencies,
                    recentJson = settings?.recentCurrencies,
                    balanceCurrency = settings?.balanceCurrency
                )

                _uiState.value = _uiState.value.copy(
                    settings = settings,
                    lowBalanceThreshold = financeRepository.getAccountById(settings?.activeAccountId ?: 1)?.lowBalanceThreshold ?: "",
                    theme = settings?.theme ?: "SYSTEM",
                    currencySortMode = sortMode,
                    customOrder = customOrder,
                    favoriteCurrencies = favorites,
                    recentCurrencies = recents,
                    currencies = CurrencyProvider.allCurrencies,
                    orderedCurrencies = ordered,
                    activeAccountId = settings?.activeAccountId ?: 1L,
                    isLoading = false
                )
            }
        }
        viewModelScope.launch {
            financeRepository.getAllAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(accounts = accounts)
            }
        }
    }

    fun switchAccount(account: com.spendingtracker.app.data.local.entity.Account) {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync()
            financeRepository.saveSettings(
                current?.copy(activeAccountId = account.id) ?: AppSettings(activeAccountId = account.id)
            )
            _uiState.value = _uiState.value.copy(activeAccountId = account.id)
        }
    }

    fun addAccount(name: String, currency: String, startingBalance: String) {
        viewModelScope.launch {
            val sb = try {
                if (startingBalance.isBlank()) BigDecimal.ZERO else BigDecimal(startingBalance.trim())
            } catch (e: NumberFormatException) {
                BigDecimal.ZERO
            }
            val id = financeRepository.insertAccount(
                com.spendingtracker.app.data.local.entity.Account(
                    name = name.ifBlank { "Account" },
                    currency = currency.ifBlank { "USD" },
                    startingBalance = sb.toPlainString()
                )
            )
            val current = financeRepository.getSettingsSync()
            financeRepository.saveSettings(
                current?.copy(activeAccountId = id) ?: AppSettings(activeAccountId = id)
            )
            _uiState.value = _uiState.value.copy(activeAccountId = id, message = "Account created")
        }
    }

    fun renameAccount(account: com.spendingtracker.app.data.local.entity.Account, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            financeRepository.updateAccount(account.copy(name = trimmed))
        }
    }

    fun deleteAccount(account: com.spendingtracker.app.data.local.entity.Account) {
        viewModelScope.launch {
            financeRepository.deleteAccountCascade(account)
            _uiState.value = _uiState.value.copy(message = "Account deleted")
        }
    }

    fun onThresholdChanged(v: String) { _uiState.value = _uiState.value.copy(lowBalanceThreshold = v) }
    fun onThemeChanged(t: String) {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync()
            if (current != null) {
                financeRepository.saveSettings(current.copy(theme = t))
            }
            _uiState.value = _uiState.value.copy(theme = t)
        }
    }

    fun saveThreshold() {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync() ?: return@launch
            val threshold = _uiState.value.lowBalanceThreshold
            if (threshold.isBlank()) {
                financeRepository.getAccountById(financeRepository.getActiveAccountId())?.let { financeRepository.updateAccount(it.copy(lowBalanceThreshold = null)) }
                _uiState.value = _uiState.value.copy(message = "Threshold cleared")
            } else {
                try {
                    val bd = BigDecimal(threshold)
                    if (bd < BigDecimal.ZERO) {
                        _uiState.value = _uiState.value.copy(error = "Threshold cannot be negative")
                        return@launch
                    }
                    financeRepository.getAccountById(financeRepository.getActiveAccountId())?.let { financeRepository.updateAccount(it.copy(lowBalanceThreshold = bd.toPlainString())) }
                    _uiState.value = _uiState.value.copy(message = "Threshold saved")
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(error = "Invalid threshold")
                }
            }
        }
    }

    fun requestChangeCurrency(newCurrency: String) {
        _uiState.value = _uiState.value.copy(newCurrency = newCurrency, showChangeCurrencyDialog = true)
    }

    fun confirmChangeCurrency() {
        _uiState.value = _uiState.value.copy(showChangeCurrencyDialog = false,
            error = "Existing ledger currencies are fixed to preserve history. Create a new account in the desired currency instead.")
    }

    fun dismissChangeCurrency() { _uiState.value = _uiState.value.copy(showChangeCurrencyDialog = false) }

    fun requestErase() { _uiState.value = _uiState.value.copy(showEraseDialog = true) }
    fun dismissErase() { _uiState.value = _uiState.value.copy(showEraseDialog = false) }

    fun confirmErase() {
        viewModelScope.launch {
            financeRepository.eraseAll()
            loanRepository.eraseAll()
            // Reset settings to not setup
            financeRepository.saveSettings(AppSettings(balanceCurrency = "USD", startingBalance = "0", isSetupComplete = false))
            _uiState.value = _uiState.value.copy(showEraseDialog = false, message = "All data erased")
        }
    }

    fun exportJson() {
        viewModelScope.launch {
            try {
                val json = exportImportManager.exportAll()
                _uiState.value = _uiState.value.copy(exportJson = json, message = "JSON exported")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            try {
                val csv = exportImportManager.exportTransactionsCsv()
                _uiState.value = _uiState.value.copy(exportCsv = csv, message = "CSV exported")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun importJson(jsonString: String) {
        viewModelScope.launch {
            val result = exportImportManager.importAll(jsonString)
            if (result.isSuccess) _uiState.value = _uiState.value.copy(message = "Import successful")
            else _uiState.value = _uiState.value.copy(error = result.exceptionOrNull()?.message)
        }
    }

    fun showCurrencyOrderDialog() { _uiState.value = _uiState.value.copy(showCurrencyOrderDialog = true) }
    fun dismissCurrencyOrderDialog() { _uiState.value = _uiState.value.copy(showCurrencyOrderDialog = false) }

    fun onCurrencySortModeChanged(mode: String) {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync() ?: return@launch
            financeRepository.saveSettings(current.copy(currencySortMode = mode))
            _uiState.value = _uiState.value.copy(currencySortMode = mode, message = "Currency sort: $mode")
        }
    }

    fun toggleFavoriteCurrency(code: String) {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync() ?: return@launch
            val currentFavs = current.favoriteCurrencies?.let { json ->
                try {
                    json.trim().removePrefix("[").removeSuffix("]")
                        .split(",").map { it.trim().trim('"', '\'', ' ') }.filter { it.isNotBlank() }.toMutableList()
                } catch (e: Exception) { mutableListOf() }
            } ?: mutableListOf()

            if (currentFavs.contains(code)) {
                currentFavs.remove(code)
            } else {
                currentFavs.add(code)
            }
            val newJson = currentFavs.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
            financeRepository.saveSettings(current.copy(favoriteCurrencies = newJson))
            _uiState.value = _uiState.value.copy(message = if (currentFavs.contains(code)) "$code added to favorites" else "$code removed from favorites")
        }
    }

    fun moveCurrencyInCustomOrder(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync() ?: return@launch
            val order = current.customCurrencyOrder?.let { json ->
                try {
                    json.trim().removePrefix("[").removeSuffix("]")
                        .split(",").map { it.trim().trim('"', '\'', ' ') }.filter { it.isNotBlank() }.toMutableList()
                } catch (e: Exception) { mutableListOf() }
            } ?: CurrencyProvider.allCurrencies.map { it.code }.toMutableList()

            if (fromIndex in order.indices && toIndex in order.indices) {
                val item = order.removeAt(fromIndex)
                order.add(toIndex, item)
                val newJson = order.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
                financeRepository.saveSettings(current.copy(customCurrencyOrder = newJson, currencySortMode = "CUSTOM"))
                _uiState.value = _uiState.value.copy(message = "Order updated")
            }
        }
    }

    fun setCustomOrder(codes: List<String>) {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync() ?: return@launch
            val newJson = codes.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
            financeRepository.saveSettings(current.copy(customCurrencyOrder = newJson, currencySortMode = "CUSTOM"))
            _uiState.value = _uiState.value.copy(customOrder = codes, currencySortMode = "CUSTOM", message = "Custom order saved")
        }
    }

    fun resetCurrencyOrder() {
        viewModelScope.launch {
            val current = financeRepository.getSettingsSync() ?: return@launch
            financeRepository.saveSettings(current.copy(customCurrencyOrder = null, currencySortMode = "ALPHABETICAL", favoriteCurrencies = null, recentCurrencies = null))
            _uiState.value = _uiState.value.copy(message = "Currency order reset to alphabetical")
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null, error = null, exportJson = null, exportCsv = null) }

    fun refreshRates() {
        viewModelScope.launch {
            val settings = financeRepository.getSettingsSync() ?: return@launch
            val result = rateRepository.refreshRates(settings.balanceCurrency)
            result.onSuccess { ts ->
                financeRepository.saveSettings(settings.copy(lastRatesUpdate = ts))
                _uiState.value = _uiState.value.copy(message = "Rates refreshed")
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = "Failed to refresh: ${it.message}")
            }
        }
    }
}
