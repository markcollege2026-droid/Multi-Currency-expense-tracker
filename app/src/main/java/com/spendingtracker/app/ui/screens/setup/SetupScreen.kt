package com.spendingtracker.app.ui.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.util.CurrencyProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val filteredCurrencies = remember(uiState.searchQuery) {
        CurrencyProvider.search(uiState.searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Welcome to Spending Tracker") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Choose your balance currency", style = MaterialTheme.typography.titleLarge)
            Text("This will be your main currency. Expenses in other currencies will be converted to it.", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchChanged,
                label = { Text("Search currency") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Selected: ${uiState.selectedCurrency} - ${CurrencyProvider.getSymbol(uiState.selectedCurrency)}", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredCurrencies.take(50)) { currency ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.onCurrencySelected(currency.code) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (currency.code == uiState.selectedCurrency) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${currency.code} - ${currency.name}")
                            Text(currency.symbol)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.startingBalance,
                onValueChange = viewModel::onStartingBalanceChanged,
                label = { Text("Starting balance (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.completeSetup(onSetupComplete) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Continue")
            }
        }
    }
}
