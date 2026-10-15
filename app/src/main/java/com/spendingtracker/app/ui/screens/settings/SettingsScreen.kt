package com.spendingtracker.app.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Account?>(null) }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var addAccountName by remember { mutableStateOf("") }
    var addAccountCurrency by remember { mutableStateOf("USD") }
    var addAccountBalance by remember { mutableStateOf("") }
    var showAddAccountCurrencyPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Accounts", style = MaterialTheme.typography.titleMedium)
                        Text("Balances, expenses and top-ups are tracked per account. Debts/loans are shared across all accounts.", style = MaterialTheme.typography.bodySmall)
                        uiState.accounts.forEach { account ->
                            val isActive = account.id == uiState.activeAccountId
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        account.name + if (isActive) "  (active)" else "",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "Start: ${CurrencyFormatter.formatSimple(account.startingBalanceAsBigDecimal(), account.currency)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!isActive) {
                                    TextButton(onClick = { viewModel.switchAccount(account) }) { Text("Switch") }
                                }
                                TextButton(onClick = { renameTarget = account }) { Text("Rename") }
                                TextButton(
                                    onClick = { deleteTarget = account },
                                    enabled = uiState.accounts.size > 1
                                ) { Text("Delete") }
                            }
                        }
                        Button(onClick = { showAddAccount = true }, modifier = Modifier.fillMaxWidth()) { Text("Add account") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Balance", style = MaterialTheme.typography.titleMedium)
                        Text("Currency: ${uiState.settings?.balanceCurrency ?: "USD"}")
                        Text("Starting: ${uiState.settings?.startingBalance ?: "0"}")
                        Button(onClick = { showCurrencyPicker = true }) { Text("Change balance currency") }
                        Text("Changing currency will re-express history using stored rates", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Low balance warning", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = uiState.lowBalanceThreshold, onValueChange = viewModel::onThresholdChanged, label = { Text("Threshold in ${uiState.settings?.balanceCurrency}") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = viewModel::saveThreshold) { Text("Save threshold") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = uiState.theme == "LIGHT", onClick = { viewModel.onThemeChanged("LIGHT") }, label = { Text("Light") })
                            FilterChip(selected = uiState.theme == "DARK", onClick = { viewModel.onThemeChanged("DARK") }, label = { Text("Dark") })
                            FilterChip(selected = uiState.theme == "SYSTEM", onClick = { viewModel.onThemeChanged("SYSTEM") }, label = { Text("System") })
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Currency order", style = MaterialTheme.typography.titleMedium)
                        Text("Customize how currencies appear in dropdowns", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(selected = uiState.currencySortMode == "ALPHABETICAL", onClick = { viewModel.onCurrencySortModeChanged("ALPHABETICAL") }, label = { Text("A-Z") })
                            FilterChip(selected = uiState.currencySortMode == "FAVORITES", onClick = { viewModel.onCurrencySortModeChanged("FAVORITES") }, label = { Text("Favorites") })
                            FilterChip(selected = uiState.currencySortMode == "RECENT", onClick = { viewModel.onCurrencySortModeChanged("RECENT") }, label = { Text("Recent") })
                            FilterChip(selected = uiState.currencySortMode == "CUSTOM", onClick = { viewModel.onCurrencySortModeChanged("CUSTOM") }, label = { Text("Custom") })
                        }
                        if (uiState.favoriteCurrencies.isNotEmpty()) {
                            Text("Favorites: ${uiState.favoriteCurrencies.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (uiState.recentCurrencies.isNotEmpty()) {
                            Text("Recent: ${uiState.recentCurrencies.take(5).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::showCurrencyOrderDialog) { Text("Customize order") }
                            TextButton(onClick = viewModel::resetCurrencyOrder) { Text("Reset") }
                        }
                        Text("Tap star in currency picker to add/remove favorites", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Exchange rates", style = MaterialTheme.typography.titleMedium)
                        Text("Last updated: ${uiState.settings?.lastRatesUpdate?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) } ?: "Never"}")
                        Button(onClick = viewModel::refreshRates) { Text("Refresh rates") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Data", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = viewModel::exportJson, modifier = Modifier.fillMaxWidth()) { Text("Export all as JSON") }
                        Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) { Text("Export transactions as CSV") }
                        BackupControls(uiState, viewModel)

                        uiState.exportJson?.let { json ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, json)
                                type = "application/json"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share JSON")
                            Button(onClick = { context.startActivity(shareIntent) }, modifier = Modifier.fillMaxWidth()) { Text("Share exported JSON") }
                        }
                        uiState.exportCsv?.let { csv ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csv)
                                type = "text/csv"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share CSV")
                            Button(onClick = { context.startActivity(shareIntent) }, modifier = Modifier.fillMaxWidth()) { Text("Share exported CSV") }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(enabled = !uiState.backupBusy, onClick = viewModel::requestErase, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                            Text("Erase all data")
                        }
                    }
                }

                uiState.message?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(it, modifier = Modifier.padding(12.dp))
                    }
                }
                uiState.error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(it, modifier = Modifier.padding(12.dp))
                    }
                }
                if (uiState.message != null || uiState.error != null) {
                    TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                }
            }
        }

        if (showCurrencyPicker) {
            CurrencyPickerDialog(
                orderedCurrencies = uiState.orderedCurrencies,
                balanceCurrency = uiState.settings?.balanceCurrency ?: "USD",
                onDismiss = { showCurrencyPicker = false },
                onCurrencySelected = { code -> showCurrencyPicker = false; viewModel.requestChangeCurrency(code) }
            )
        }

        if (showAddAccount) {
            AlertDialog(
                onDismissRequest = { showAddAccount = false },
                title = { Text("New account") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addAccountName,
                            onValueChange = { addAccountName = it },
                            label = { Text("Account name") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = addAccountBalance,
                            onValueChange = { addAccountBalance = it },
                            label = { Text("Starting balance (optional)") },
                            singleLine = true
                        )
                        OutlinedButton(onClick = { showAddAccountCurrencyPicker = true }) {
                            Text("Currency: $addAccountCurrency")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addAccount(addAccountName, addAccountCurrency, addAccountBalance)
                        addAccountName = ""
                        addAccountBalance = ""
                        showAddAccount = false
                    }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { showAddAccount = false }) { Text("Cancel") } }
            )
        }

        renameTarget?.let { target ->
            var name by remember(target) { mutableStateOf(target.name) }
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("Rename account") },
                text = {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.renameAccount(target, name); renameTarget = null }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete account?") },
                text = {
                    Text("Deletes \"${target.name}\" together with its expenses and top-ups. This cannot be undone. Debts/loans are kept.")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteAccount(target); deleteTarget = null }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
            )
        }

        if (showAddAccountCurrencyPicker) {
            CurrencyPickerDialog(
                orderedCurrencies = uiState.orderedCurrencies,
                balanceCurrency = uiState.settings?.balanceCurrency ?: "USD",
                onDismiss = { showAddAccountCurrencyPicker = false },
                onCurrencySelected = { code -> addAccountCurrency = code; showAddAccountCurrencyPicker = false }
            )
        }

        if (uiState.showChangeCurrencyDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissChangeCurrency,
                title = { Text("Change balance currency?") },
                text = { Text("Changing the balance currency will re-express all history using stored rates. Continue?") },
                confirmButton = { Button(onClick = viewModel::confirmChangeCurrency) { Text("Continue") } },
                dismissButton = { TextButton(onClick = viewModel::dismissChangeCurrency) { Text("Cancel") } }
            )
        }

        if (uiState.showEraseDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissErase,
                title = { Text("Erase all data?") },
                text = { Text("Are you sure you want to erase all data? This cannot be undone.") },
                confirmButton = { Button(onClick = viewModel::confirmErase, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Erase") } },
                dismissButton = { TextButton(onClick = viewModel::dismissErase) { Text("Cancel") } }
            )
        }

        if (uiState.showCurrencyOrderDialog) {
            CurrencyOrderDialog(
                orderedCurrencies = uiState.orderedCurrencies,
                favoriteCurrencies = uiState.favoriteCurrencies,
                customOrder = uiState.customOrder,
                sortMode = uiState.currencySortMode,
                onDismiss = viewModel::dismissCurrencyOrderDialog,
                onToggleFavorite = viewModel::toggleFavoriteCurrency,
                onMove = viewModel::moveCurrencyInCustomOrder,
                onSaveCustomOrder = viewModel::setCustomOrder,
                onSortModeChanged = viewModel::onCurrencySortModeChanged
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyOrderDialog(
    orderedCurrencies: List<com.spendingtracker.app.util.CurrencyProvider.CurrencyInfo>,
    favoriteCurrencies: List<String>,
    customOrder: List<String>,
    sortMode: String,
    onDismiss: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSaveCustomOrder: (List<String>) -> Unit,
    onSortModeChanged: (String) -> Unit
) {
    var localOrder by remember(customOrder, orderedCurrencies) {
        mutableStateOf(
            if (customOrder.isNotEmpty()) customOrder
            else orderedCurrencies.map { it.code }
        )
    }
    var searchQuery by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize currency order") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text("Sort mode: $sortMode", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search currencies") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                val filtered = if (searchQuery.isBlank()) {
                    localOrder.mapNotNull { code -> orderedCurrencies.find { it.code == code } ?: com.spendingtracker.app.util.CurrencyProvider.allCurrencies.find { it.code == code } }
                } else {
                    com.spendingtracker.app.util.CurrencyProvider.allCurrencies.filter {
                        it.code.contains(searchQuery.uppercase()) || it.name.contains(searchQuery, ignoreCase = true)
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered.size) { idx ->
                        val currency = filtered[idx]
                        val isFavorite = favoriteCurrencies.contains(currency.code)
                        val actualIndex = localOrder.indexOf(currency.code)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${currency.code} - ${currency.name}", style = MaterialTheme.typography.bodyMedium)
                                    Text(currency.symbol, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { onToggleFavorite(currency.code) }) {
                                        Text(if (isFavorite) "★" else "☆")
                                    }
                                    if (sortMode == "CUSTOM" && actualIndex != -1) {
                                        IconButton(onClick = {
                                            if (actualIndex > 0) {
                                                val newOrder = localOrder.toMutableList()
                                                val item = newOrder.removeAt(actualIndex)
                                                newOrder.add(actualIndex - 1, item)
                                                localOrder = newOrder
                                                onMove(actualIndex, actualIndex - 1)
                                            }
                                        }) {
                                            Text("↑")
                                        }
                                        IconButton(onClick = {
                                            if (actualIndex < localOrder.size - 1) {
                                                val newOrder = localOrder.toMutableList()
                                                val item = newOrder.removeAt(actualIndex)
                                                newOrder.add(actualIndex + 1, item)
                                                localOrder = newOrder
                                                onMove(actualIndex, actualIndex + 1)
                                            }
                                        }) {
                                            Text("↓")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveCustomOrder(localOrder)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
