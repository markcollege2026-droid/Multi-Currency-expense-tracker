package com.spendingtracker.app.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AppButton
import com.spendingtracker.app.ui.components.AppCard
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppOutlinedButton
import com.spendingtracker.app.ui.components.AppSectionHeader
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.ui.components.AppValueRow
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

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
    val dims = LocalAppDimens.current

    Scaffold(
        topBar = { AppTopBar(title = "Settings", onBack = onBack) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = dims.screenPadding,
                    end = dims.screenPadding,
                    top = 4.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
            ) {
                if (uiState.message != null || uiState.error != null) {
                    item(key = "messages") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.error != null) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(Modifier.fillMaxWidth().padding(7.dp)) {
                                Text(
                                    text = uiState.error ?: uiState.message.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Dismiss",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { viewModel.clearMessage() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "accounts") {
                    SettingsSection("Accounts") {
                        Text(
                            text = "Balances, expenses and deposits are tracked per account; loans are shared. " +
                                "Deleting an account removes only that account and its own entries - every other account keeps its data.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.accounts.forEach { account ->
                            val isActive = account.id == uiState.activeAccountId
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val summary = uiState.accountSummaries[account.id]
                                    val entries = summary?.entries ?: 0
                                    Text(
                                        text = "Now " + CurrencyFormatter.formatSimple(
                                            summary?.balance ?: account.startingBalanceAsBigDecimal(),
                                            account.currency
                                        ) + " · $entries " + if (entries == 1) "entry" else "entries",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isActive) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    SmallAction("Use") { viewModel.switchAccount(account) }
                                }
                                SmallAction("Rename") { renameTarget = account }
                                // The last main account holds the whole ledger: offer
                                // no delete button for it at all.
                                if (uiState.accounts.size > 1) {
                                    SmallAction("Delete", isError = true) { deleteTarget = account }
                                }
                            }
                            // This main account's own cash/card balances. Each can be
                            // deleted individually, without touching any other account.
                            uiState.balanceAccounts
                                .filter { it.parentAccountId == account.id }
                                .forEach { child ->
                                    val childSummary = uiState.accountSummaries[child.id]
                                    val childEntries = childSummary?.entries ?: 0
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 1.dp, bottom = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = child.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = (if (child.type == "CASH") "Cash" else "Credit card") +
                                                    " · " + CurrencyFormatter.formatSimple(
                                                        childSummary?.balance ?: child.startingBalanceAsBigDecimal(),
                                                        child.currency
                                                    ) + " · $childEntries " + if (childEntries == 1) "entry" else "entries" +
                                                    if (child.type == "CASH" && !child.includedInTotal) " · set aside" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        SmallAction("Delete", isError = true) { deleteTarget = child }
                                    }
                                }
                            AppHairline()
                        }
                        AppOutlinedButton(onClick = { showAddAccount = true }) {
                            Text("+ Add account", maxLines = 1)
                        }
                    }
                }

                item(key = "balance") {
                    SettingsSection("Balance") {
                        AppValueRow("Currency", uiState.settings?.balanceCurrency ?: "USD")
                        AppValueRow("Starting", uiState.settings?.startingBalance ?: "0")
                        AppOutlinedButton(onClick = { showCurrencyPicker = true }) {
                            Text("Change balance currency", maxLines = 1)
                        }
                        Text(
                            text = "Changing currency re-expresses history using stored rates.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                item(key = "threshold") {
                    SettingsSection("Low balance warning") {
                        AppTextField(
                            value = uiState.lowBalanceThreshold,
                            onValueChange = viewModel::onThresholdChanged,
                            label = "Threshold in ${uiState.settings?.balanceCurrency ?: "USD"}",
                            keyboardType = KeyboardType.Decimal
                        )
                        AppButton(onClick = viewModel::saveThreshold) {
                            Text("Save threshold", maxLines = 1)
                        }
                    }
                }

                item(key = "theme") {
                    SettingsSection("Theme") {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppFilterChip(
                                selected = uiState.theme == "LIGHT",
                                onClick = { viewModel.onThemeChanged("LIGHT") },
                                label = "Light"
                            )
                            AppFilterChip(
                                selected = uiState.theme == "DARK",
                                onClick = { viewModel.onThemeChanged("DARK") },
                                label = "Dark"
                            )
                            AppFilterChip(
                                selected = uiState.theme == "SYSTEM",
                                onClick = { viewModel.onThemeChanged("SYSTEM") },
                                label = "System"
                            )
                        }
                    }
                }

                item(key = "currency_order") {
                    SettingsSection("Currency order") {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AppFilterChip(
                                selected = uiState.currencySortMode == "ALPHABETICAL",
                                onClick = { viewModel.onCurrencySortModeChanged("ALPHABETICAL") },
                                label = "A-Z"
                            )
                            AppFilterChip(
                                selected = uiState.currencySortMode == "FAVORITES",
                                onClick = { viewModel.onCurrencySortModeChanged("FAVORITES") },
                                label = "Favorites"
                            )
                            AppFilterChip(
                                selected = uiState.currencySortMode == "RECENT",
                                onClick = { viewModel.onCurrencySortModeChanged("RECENT") },
                                label = "Recent"
                            )
                            AppFilterChip(
                                selected = uiState.currencySortMode == "CUSTOM",
                                onClick = { viewModel.onCurrencySortModeChanged("CUSTOM") },
                                label = "Custom"
                            )
                        }
                        if (uiState.favoriteCurrencies.isNotEmpty()) {
                            Text(
                                text = "Favorites: ${uiState.favoriteCurrencies.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (uiState.recentCurrencies.isNotEmpty()) {
                            Text(
                                text = "Recent: ${uiState.recentCurrencies.take(5).joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AppButton(
                                onClick = viewModel::showCurrencyOrderDialog,
                                modifier = Modifier.weight(1.3f)
                            ) { Text("Customize", maxLines = 1) }
                            AppOutlinedButton(
                                onClick = viewModel::resetCurrencyOrder,
                                modifier = Modifier.weight(1f)
                            ) { Text("Reset", maxLines = 1) }
                        }
                        Text(
                            text = "Tap the star in the currency picker to add or remove favorites.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                item(key = "rates") {
                    SettingsSection("Exchange rates") {
                        Text(
                            text = "Last updated: " + (
                                uiState.settings?.lastRatesUpdate?.let {
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                                } ?: "Never"
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppButton(onClick = viewModel::refreshRates) {
                            Text("Refresh rates", maxLines = 1)
                        }
                    }
                }

                item(key = "data") {
                    SettingsSection("Data") {
                        AppOutlinedButton(onClick = viewModel::exportJson) {
                            Text("Export all as JSON", maxLines = 1)
                        }
                        AppOutlinedButton(onClick = viewModel::exportCsv) {
                            Text("Export transactions as CSV", maxLines = 1)
                        }
                        BackupControls(uiState, viewModel)

                        uiState.exportJson?.let { json ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, json)
                                type = "application/json"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share JSON")
                            AppButton(onClick = { context.startActivity(shareIntent) }) {
                                Text("Share exported JSON", maxLines = 1)
                            }
                        }
                        uiState.exportCsv?.let { csv ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csv)
                                type = "text/csv"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share CSV")
                            AppButton(onClick = { context.startActivity(shareIntent) }) {
                                Text("Share exported CSV", maxLines = 1)
                            }
                        }

                        AppButton(
                            onClick = viewModel::requestErase,
                            enabled = !uiState.backupBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Erase all data", maxLines = 1)
                        }
                    }
                }
            }
        }

        if (showCurrencyPicker) {
            CurrencyPickerDialog(
                orderedCurrencies = uiState.orderedCurrencies,
                balanceCurrency = uiState.settings?.balanceCurrency ?: "USD",
                onDismiss = { showCurrencyPicker = false },
                onCurrencySelected = { code ->
                    showCurrencyPicker = false
                    viewModel.requestChangeCurrency(code)
                }
            )
        }

        if (showAddAccount) {
            AppDialog(
                onDismissRequest = { showAddAccount = false },
                title = "New account",
                confirmLabel = "Create",
                confirmEnabled = addAccountName.isNotBlank(),
                onConfirm = {
                    viewModel.addAccount(addAccountName, addAccountCurrency, addAccountBalance)
                    addAccountName = ""
                    addAccountBalance = ""
                    showAddAccount = false
                }
            ) {
                AppTextField(
                    value = addAccountName,
                    onValueChange = { addAccountName = it },
                    label = "Account name"
                )
                AppTextField(
                    value = addAccountBalance,
                    onValueChange = { addAccountBalance = it },
                    label = "Starting balance (optional)",
                    keyboardType = KeyboardType.Decimal
                )
                AppOutlinedButton(onClick = { showAddAccountCurrencyPicker = true }) {
                    Text("Currency: $addAccountCurrency", maxLines = 1)
                }
            }
        }

        renameTarget?.let { target ->
            var name by remember(target) { mutableStateOf(target.name) }
            AppDialog(
                onDismissRequest = { renameTarget = null },
                title = "Rename account",
                confirmLabel = "Save",
                confirmEnabled = name.isNotBlank(),
                onConfirm = {
                    viewModel.renameAccount(target, name)
                    renameTarget = null
                }
            ) {
                AppTextField(value = name, onValueChange = { name = it }, label = "Name")
            }
        }

        deleteTarget?.let { target ->
            // Two accounts can share a name (both "Main"), and this delete removes
            // every entry of the one that is tapped with no undo. Show what is at
            // stake, and demand the name when it is not unique.
            val summary = uiState.accountSummaries[target.id]
            val duplicateName = (uiState.accounts + uiState.balanceAccounts)
                .distinctBy { it.id }
                .count { it.name.equals(target.name, ignoreCase = true) } > 1
            val isMain = target.type == "MAIN"
            var typed by remember(target.id) { mutableStateOf("") }
            AppDialog(
                onDismissRequest = { deleteTarget = null },
                title = "Delete ${target.name}?",
                confirmLabel = "Delete",
                confirmEnabled = !duplicateName || typed.trim().equals(target.name, ignoreCase = true),
                confirmColors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                onConfirm = {
                    viewModel.deleteAccount(target)
                    deleteTarget = null
                }
            ) {
                Text(
                    text = if (isMain) {
                        "This deletes only \u201C" + target.name + "\u201D: its " + (summary?.entries ?: 0) +
                            " entries (expenses, deposits, transfers, card payments) and its cash/card balances." +
                            " Every other account keeps all of its data." +
                            " A safety copy of the whole ledger is saved to app storage first." +
                            " Loans are shared and kept. There is no undo inside the app."
                    } else {
                        "This deletes only this " + (if (target.type == "CASH") "cash balance" else "credit card") +
                            " and its " + (summary?.entries ?: 0) + " entries. Transferred money returns to the " +
                            "balances it came from, and every other account - including its main account - " +
                            "keeps all of its data. A safety copy of the whole ledger is saved first." +
                            " Loans are shared and kept; settlements paid from this balance become external."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                AppValueRow(
                    "Current balance",
                    CurrencyFormatter.format(
                        summary?.balance ?: target.startingBalanceAsBigDecimal(),
                        target.currency
                    )
                )
                if (isMain && uiState.accounts.count { it.type == "MAIN" } <= 1) {
                    Text(
                        text = "This is the only main account and cannot be deleted.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (duplicateName) {
                    Text(
                        text = "Another account is also called \"${target.name}\". Type the name to confirm.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    AppTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = "Account name",
                        placeholder = target.name
                    )
                }
            }
        }

        if (showAddAccountCurrencyPicker) {
            CurrencyPickerDialog(
                orderedCurrencies = uiState.orderedCurrencies,
                balanceCurrency = uiState.settings?.balanceCurrency ?: "USD",
                onDismiss = { showAddAccountCurrencyPicker = false },
                onCurrencySelected = { code ->
                    addAccountCurrency = code
                    showAddAccountCurrencyPicker = false
                }
            )
        }

        if (uiState.showChangeCurrencyDialog) {
            AppDialog(
                onDismissRequest = viewModel::dismissChangeCurrency,
                title = "Change balance currency?",
                confirmLabel = "Continue",
                onConfirm = viewModel::confirmChangeCurrency
            ) {
                Text(
                    text = "Changing the balance currency re-expresses all history using stored rates. Continue?",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (uiState.showEraseDialog) {
            AppDialog(
                onDismissRequest = viewModel::dismissErase,
                title = "Erase all data?",
                confirmLabel = "Erase",
                // The erase is one transaction; afterwards there is no ledger, so the
                // app restarts into setup instead of showing empty screens.
                onConfirm = {
                    viewModel.confirmErase { (context as? android.app.Activity)?.recreate() }
                }
            ) {
                Text(
                    text = "This deletes every account, entry, transfer and loan, and cannot be undone. " +
                        "Save a backup from this screen first if you might want the data back.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AppSectionHeader(title)
            content()
        }
    }
}

/** Small text action that stays tappable/focusable but does not cost 48dp. */
@Composable
private fun SmallAction(
    label: String,
    isError: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 3.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp)
    )
}

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
    val compact = LocalCompactUi.current
    val maxList = (LocalConfiguration.current.screenHeightDp * if (compact) 0.5f else 0.55f).dp
    val filtered = remember(searchQuery, localOrder, orderedCurrencies) {
        if (searchQuery.isBlank()) {
            localOrder.mapNotNull { code ->
                orderedCurrencies.find { it.code == code }
                    ?: com.spendingtracker.app.util.CurrencyProvider.allCurrencies.find { it.code == code }
            }
        } else {
            com.spendingtracker.app.util.CurrencyProvider.allCurrencies.filter {
                it.code.contains(searchQuery.uppercase()) ||
                    it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Customize currency order",
        confirmLabel = "Save",
        onConfirm = {
            onSaveCustomOrder(localOrder)
            onDismiss()
        },
        contentScrollable = false
    ) {
        Text(
            text = "Sort mode: $sortMode",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = "Search currencies",
            placeholder = "EUR, dollar…"
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxList),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filtered.size) { idx ->
                val currency = filtered[idx]
                val isFavorite = favoriteCurrencies.contains(currency.code)
                val actualIndex = localOrder.indexOf(currency.code)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${currency.code} · ${currency.name}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currency.symbol,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    SmallAction(if (isFavorite) "★" else "☆") { onToggleFavorite(currency.code) }
                    if (sortMode == "CUSTOM" && actualIndex != -1) {
                        SmallAction("↑") {
                            if (actualIndex > 0) {
                                val newOrder = localOrder.toMutableList()
                                val item = newOrder.removeAt(actualIndex)
                                newOrder.add(actualIndex - 1, item)
                                localOrder = newOrder
                                onMove(actualIndex, actualIndex - 1)
                            }
                        }
                        SmallAction("↓") {
                            if (actualIndex < localOrder.size - 1) {
                                val newOrder = localOrder.toMutableList()
                                val item = newOrder.removeAt(actualIndex)
                                newOrder.add(actualIndex + 1, item)
                                localOrder = newOrder
                                onMove(actualIndex, actualIndex + 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
