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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val json = input?.bufferedReader()?.readText()
                if (json != null) viewModel.importJson(json)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
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
                        Button(onClick = { importLauncher.launch("application/json") }, modifier = Modifier.fillMaxWidth()) { Text("Import JSON") }

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
                        Button(onClick = viewModel::requestErase, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
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
            CurrencyPickerDialog(onDismiss = { showCurrencyPicker = false }, onCurrencySelected = { code -> showCurrencyPicker = false; viewModel.requestChangeCurrency(code) })
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
    }
}
