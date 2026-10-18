package com.spendingtracker.app.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AppButton
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.util.CurrencyProvider

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current

    val filteredCurrencies = remember(uiState.searchQuery) {
        CurrencyProvider.search(uiState.searchQuery)
    }

    Scaffold(
        topBar = { AppTopBar(title = "Welcome to Spending Tracker") },
        bottomBar = {
            // Pinned above the keyboard/navigation bar so Continue is always reachable.
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = if (compact) 6.dp else 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    uiState.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AppButton(
                        onClick = { viewModel.completeSetup(onSetupComplete) },
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Continue", maxLines = 1)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
            contentPadding = PaddingValues(
                start = dims.screenPadding,
                end = dims.screenPadding,
                top = 4.dp,
                bottom = 10.dp
            )
        ) {
            item(key = "intro") {
                Text(
                    text = "Choose your balance currency. Expenses in other currencies are converted to it.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item(key = "account_name") {
                AppTextField(
                    value = uiState.accountName,
                    onValueChange = viewModel::onAccountNameChanged,
                    label = "Account name (optional, e.g. Main)",
                    imeAction = ImeAction.Next
                )
            }
            item(key = "starting") {
                AppTextField(
                    value = uiState.startingBalance,
                    onValueChange = viewModel::onStartingBalanceChanged,
                    label = "Starting balance (optional)",
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                )
            }
            item(key = "selected") {
                Text(
                    text = "Balance currency: ${uiState.selectedCurrency} · " +
                        CurrencyProvider.getSymbol(uiState.selectedCurrency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            item(key = "search") {
                AppTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchChanged,
                    label = "Search currency",
                    placeholder = "USD, euro…",
                    imeAction = ImeAction.Search
                )
            }
            items(filteredCurrencies.take(60), key = { it.code }) { currency ->
                val selected = currency.code == uiState.selectedCurrency
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .clickable { viewModel.onCurrencySelected(currency.code) }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currency.code,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 42.dp)
                    )
                    Text(
                        text = currency.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = currency.symbol,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
