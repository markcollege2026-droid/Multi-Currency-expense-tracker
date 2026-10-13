package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.util.CurrencyProvider
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    expenseId: Long?,
    onBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    LaunchedEffect(expenseId) {
        viewModel.loadExpense(expenseId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Expense" else "Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::onAmountChanged,
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.currency,
                    onValueChange = {},
                    label = { Text("Currency") },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showCurrencyPicker = true }) { Text("Pick") }
            }

            if (uiState.currency != uiState.balanceCurrency) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = uiState.useManualRate, onCheckedChange = viewModel::onUseManualRateChanged)
                    Text("Use manual rate")
                }
                if (uiState.useManualRate) {
                    OutlinedTextField(
                        value = uiState.manualRate,
                        onValueChange = viewModel::onManualRateChanged,
                        label = { Text("Manual rate (1 ${uiState.currency} = X ${uiState.balanceCurrency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                uiState.convertedAmount?.let {
                    Text("Converted: -${it.toPlainString()} ${uiState.balanceCurrency} (rate ${uiState.rateUsed?.toPlainString()})", style = MaterialTheme.typography.bodyMedium)
                } ?: Text("No rate available - enter manual rate", color = MaterialTheme.colorScheme.error)
            } else {
                Text("Same as balance currency: ${uiState.balanceCurrency}", style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.categories.find { it.id == uiState.categoryId }?.name ?: "Select category",
                    onValueChange = {},
                    label = { Text("Category") },
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { showCategoryPicker = true }) { Text("Pick") }
            }

            OutlinedTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChanged,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.paymentMethod,
                onValueChange = viewModel::onPaymentMethodChanged,
                label = { Text("Payment method (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(uiState.date))}")

            if (uiState.error != null) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.saveExpense(expenseId, onBack) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && uiState.convertedAmount != null
            ) {
                if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("Save")
            }
        }

        if (showCurrencyPicker) {
            CurrencyPickerDialog(
                onDismiss = { showCurrencyPicker = false },
                onCurrencySelected = { code ->
                    viewModel.onCurrencyChanged(code)
                    showCurrencyPicker = false
                }
            )
        }

        if (showCategoryPicker) {
            CategoryPickerDialog(
                categories = uiState.categories,
                onDismiss = { showCategoryPicker = false },
                onCategorySelected = { id ->
                    viewModel.onCategoryChanged(id)
                    showCategoryPicker = false
                }
            )
        }
    }
}

@Composable
fun CurrencyPickerDialog(onDismiss: () -> Unit, onCurrencySelected: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { CurrencyProvider.search(query).take(100) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select currency") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered.size) { idx ->
                        val c = filtered[idx]
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text("${c.code} - ${c.name}") },
                            supportingContent = { Text(c.symbol) },
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        )
                        Button(onClick = { onCurrencySelected(c.code) }, modifier = Modifier.fillMaxWidth()) { Text("Select ${c.code}") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CategoryPickerDialog(categories: List<com.spendingtracker.app.data.local.entity.Category>, onDismiss: () -> Unit, onCategorySelected: (Long?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select category") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(300.dp)) {
                items(categories.size) { idx ->
                    val cat = categories[idx]
                    Button(onClick = { onCategorySelected(cat.id) }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        Text(cat.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
