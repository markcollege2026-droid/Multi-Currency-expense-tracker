package com.spendingtracker.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.util.BackupFileManager
import com.spendingtracker.app.util.BackupEncryption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
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
import java.io.File
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
    val accounts: List<Account> = emptyList(),
    /** Every account incl. cash/card children, so each one can be managed (and deleted) on its own. */
    val balanceAccounts: List<Account> = emptyList(),
    /** Entries and derived balance per account, so deleting one can say what is at stake. */
    val accountSummaries: Map<Long, com.spendingtracker.app.data.repository.AccountSummary> = emptyMap(),
    val activeAccountId: Long = 1,
    val backupBusy: Boolean = false,
    val restoreEncrypted: Boolean? = null,
    val backupDocumentName: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val financeRepository: FinanceRepository,
    private val rateRepository: ExchangeRateRepository,
    private val loanRepository: LoanRepository,
    private val exportImportManager: ExportImportManager,
    private val backupFiles: BackupFileManager,
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
        viewModelScope.launch {
            financeRepository.observeBalanceAccounts().collect { accounts ->
                _uiState.value = _uiState.value.copy(balanceAccounts = accounts)
            }
        }
        viewModelScope.launch {
            financeRepository.observeAccountSummaries().collect { summaries ->
                _uiState.value = _uiState.value.copy(accountSummaries = summaries)
            }
        }
    }

    fun switchAccount(account: Account) {
        viewModelScope.launch {
            try {
                // Creates the settings row when it is missing; the old code used to
                // return silently and the switcher became a no-op.
                financeRepository.setActiveAccount(account.id)
                _uiState.value = _uiState.value.copy(activeAccountId = account.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Could not switch account")
            }
        }
    }

    fun addAccount(name: String, currency: String, startingBalance: String) {
        viewModelScope.launch {
            val sb = try {
                if (startingBalance.isBlank()) BigDecimal.ZERO
                else BigDecimal(startingBalance.trim().replace(',', '.'))
            } catch (e: NumberFormatException) {
                _uiState.value = _uiState.value.copy(error = "Starting balance must be a number")
                return@launch
            }
            if (sb < BigDecimal.ZERO) {
                _uiState.value = _uiState.value.copy(error = "Starting balance cannot be negative")
                return@launch
            }
            try {
                val id = withContext(NonCancellable) {
                    val newId = financeRepository.insertAccount(
                        Account(
                            name = name.trim().ifBlank { "Account" },
                            currency = currency.ifBlank { "USD" },
                            startingBalance = sb.toPlainString()
                        )
                    )
                    // Also marks setup complete: without this the next launch used to
                    // open the setup wizard, which then created a second empty "Main"
                    // and made it active - the app looked wiped.
                    financeRepository.setActiveAccount(newId)
                    newId
                }
                _uiState.value = _uiState.value.copy(activeAccountId = id, message = "Account created")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Account not created")
            }
        }
    }

    fun renameAccount(account: Account, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            financeRepository.updateAccount(account.copy(name = trimmed))
        }
    }

    /**
     * Deletes an account and everything scoped to it.
     *
     * A main account is snapshotted to app-private storage first, the cascade and the
     * active-account switch commit together, and the operation is NonCancellable so
     * closing the app mid-delete cannot leave a half-deleted ledger. The last main
     * account cannot be deleted at all.
     */
    fun deleteAccount(account: Account) {
        if (_uiState.value.backupBusy) return
        viewModelScope.launch {
            try {
                // Snapshot before deleting ANY single account (main, cash or card):
                // the copy is whole-ledger JSON, cheap insurance for a misclick.
                val snapshot = snapshotBeforeDelete(account)
                val survivor = withContext(NonCancellable) { financeRepository.deleteAccountCascade(account) }
                val summary = _uiState.value.accountSummaries[account.id]
                _uiState.value = _uiState.value.copy(
                    activeAccountId = survivor?.id ?: _uiState.value.activeAccountId,
                    message = buildString {
                        append(account.name)
                        append(" deleted")
                        summary?.let { append(" with ${it.entries} entries") }
                        survivor?.let { append(" · ${it.name} is now active") }
                        snapshot?.let { append(" · safety copy: $it") }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message ?: "Account not deleted")
            }
        }
    }

    /**
     * Writes a full JSON export to app-private storage before a destructive delete.
     * Best effort: a failure here must not block the delete, so it is reported as
     * null and simply omitted from the confirmation message.
     */
    private suspend fun snapshotBeforeDelete(account: Account): String? = try {
        withContext(Dispatchers.IO) {
            val json = exportImportManager.exportAll()
            val dir = File(context.filesDir, "pre-delete-backups").apply { mkdirs() }
            val file = File(dir, "account-${account.id}-${System.currentTimeMillis()}.json")
            file.writeText(json)
            file.name
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    fun onThresholdChanged(v: String) { _uiState.value = _uiState.value.copy(lowBalanceThreshold = v) }
    fun onThemeChanged(t: String) {
        viewModelScope.launch {
            // Column-level update: never rewrites activeAccountId / isSetupComplete.
            financeRepository.saveTheme(t)
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

    /**
     * Erases the whole ledger. [onErased] runs after the commit, so the screen can
     * route back into setup - without it the app would keep showing empty screens
     * with no active account.
     */
    fun confirmErase(onErased: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                // ONE transaction. Split across three calls, an interruption (or the
                // app being closed from Recents) could leave history deleted while
                // loans survived and no settings row existed - the state that boots
                // into setup with a loans tab full of data.
                withContext(NonCancellable) {
                    financeRepository.atomic {
                        financeRepository.eraseAll()
                        loanRepository.eraseAll()
                        financeRepository.resetSettingsAfterErase()
                    }
                }
                _uiState.value = _uiState.value.copy(
                    showEraseDialog = false,
                    activeAccountId = 1L,
                    message = "All data erased"
                )
                if (financeRepository.needsSetup()) onErased()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    showEraseDialog = false,
                    error = "Nothing was erased: ${e.message}"
                )
            }
        }
    }

    private var pendingRestore: ByteArray? = null

    fun reportBackupError(message: String) { _uiState.update { it.copy(error = message) } }

    fun saveBackup(uri: Uri, format: BackupFileManager.Format, password: CharArray? = null) {
        if (_uiState.value.backupBusy) { password?.fill('\u0000'); return }
        _uiState.update { it.copy(backupBusy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) { backupFiles.save(uri, format, password) }
                _uiState.update { it.copy(message = "Saved $name to the location you selected in the Android file picker.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(error = "Backup not saved: ${e.message}. Remove any incomplete file at the selected location.") } }
            finally { password?.fill('\u0000'); _uiState.update { it.copy(backupBusy = false) } }
        }
    }

    fun openBackup(uri: Uri) {
        if (_uiState.value.backupBusy) return
        cancelRestore()
        _uiState.update { it.copy(backupBusy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val (bytes, name) = withContext(Dispatchers.IO) { backupFiles.read(uri) to backupFiles.displayName(uri) }
                pendingRestore = bytes
                _uiState.update { it.copy(restoreEncrypted = BackupEncryption.isEncrypted(bytes), backupDocumentName = name) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(error = "Cannot read backup: ${e.message}. Existing data was not changed.") } }
            finally { _uiState.update { it.copy(backupBusy = false) } }
        }
    }

    fun restoreBackup(password: CharArray? = null) {
        val bytes = pendingRestore ?: run { password?.fill('\u0000'); return }
        if (_uiState.value.backupBusy) { password?.fill('\u0000'); return }
        _uiState.update { it.copy(backupBusy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { backupFiles.restore(bytes, password) }
                pendingRestore?.fill(0); pendingRestore = null
                _uiState.update { it.copy(restoreEncrypted = null, backupDocumentName = null, message = "Backup restored successfully. Existing data was replaced.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(error = "Restore failed: ${e.message}. Existing data was not changed.") } }
            finally { password?.fill('\u0000'); _uiState.update { it.copy(backupBusy = false) } }
        }
    }

    fun cancelRestore() {
        if (_uiState.value.backupBusy) return
        pendingRestore?.fill(0); pendingRestore = null
        _uiState.update { it.copy(restoreEncrypted = null, backupDocumentName = null, error = null) }
    }

    override fun onCleared() {
        pendingRestore?.fill(0); pendingRestore = null
        super.onCleared()
    }

    fun exportJson() {
        viewModelScope.launch {
            try {
                val json = exportImportManager.exportAll()
                _uiState.value = _uiState.value.copy(exportJson = json, message = "JSON prepared for sharing; not saved to a file")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            try {
                val csv = exportImportManager.exportTransactionsCsv()
                _uiState.value = _uiState.value.copy(exportCsv = csv, message = "CSV prepared for sharing; not saved to a file")
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
            // Column-level write: keeps activeAccountId out of reach.
            financeRepository.saveCurrencySortMode(mode)
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
            financeRepository.saveFavoriteCurrencies(newJson)
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
                financeRepository.saveCustomCurrencyOrder(newJson, "CUSTOM")
                _uiState.value = _uiState.value.copy(message = "Order updated")
            }
        }
    }

    fun setCustomOrder(codes: List<String>) {
        viewModelScope.launch {
            val newJson = codes.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
            financeRepository.saveCustomCurrencyOrder(newJson, "CUSTOM")
            _uiState.value = _uiState.value.copy(customOrder = codes, currencySortMode = "CUSTOM", message = "Custom order saved")
        }
    }

    fun resetCurrencyOrder() {
        viewModelScope.launch {
            financeRepository.saveCustomCurrencyOrder(null, "ALPHABETICAL")
            financeRepository.saveFavoriteCurrencies(null)
            financeRepository.saveRecentCurrencies(null)
            _uiState.value = _uiState.value.copy(message = "Currency order reset to alphabetical")
        }
    }

    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null, error = null, exportJson = null, exportCsv = null) }

    fun refreshRates() {
        viewModelScope.launch {
            val settings = financeRepository.getSettingsSync() ?: return@launch
            val result = rateRepository.refreshRates(settings.balanceCurrency)
            result.onSuccess { ts ->
                financeRepository.saveRatesTimestamp(ts)
                _uiState.value = _uiState.value.copy(message = "Rates refreshed")
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = "Failed to refresh: ${it.message}")
            }
        }
    }
}
