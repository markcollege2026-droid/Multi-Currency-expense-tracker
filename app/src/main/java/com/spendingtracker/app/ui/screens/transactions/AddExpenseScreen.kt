package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Scrollable + keyboard-aware so the Save button stays reachable on small screens
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = viewModel::onAmountChanged,
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
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

            Text("Payment account")
            uiState.paymentAccounts.forEach { account ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = (uiState.balanceAccountId ?: uiState.accountId) == account.id,
                        onClick = { viewModel.selectPaymentAccount(account.id) })
                    Text("${account.name} (${account.type.replace('_', ' ')})")
                }
            }
            if (uiState.paymentAccounts.none { it.type == "CASH" }) Text("Create cash or credit cards on Home to use them here.")
            if (uiState.paymentAccounts.any { it.id == uiState.balanceAccountId && it.type == "CASH" }) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = uiState.allowOverdraft, onCheckedChange = viewModel::allowOverdraft)
                    Text("Explicitly allow cash overdraft")
                }
            }

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
                orderedCurrencies = uiState.orderedCurrencies,
                balanceCurrency = uiState.balanceCurrency,
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
                },
                onDeleteCategory = { viewModel.deleteCategory(it) },
                onCreateCategory = { name, color -> viewModel.createCategory(name, color) }
            )
        }
    }
}

@Composable
fun CurrencyPickerDialog(
    orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    balanceCurrency: String = "USD",
    onDismiss: () -> Unit,
    onCurrencySelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, orderedCurrencies) {
        val q = query.lowercase().trim()
        if (q.isBlank()) {
            orderedCurrencies.take(100)
        } else {
            orderedCurrencies.filter {
                it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
            }.take(100)
        }
    }
    // Scale the list to the screen so dialogs fit on small devices
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.4f).dp.coerceAtMost(340.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select currency") },
        text = {
            Column {
                Text("Order: ${balanceCurrency} first, then favorites/recent/custom per Settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight)) {
                    items(filtered.size) { idx ->
                        val c = filtered[idx]
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (c.code == balanceCurrency) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onClick = { onCurrencySelected(c.code) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("${c.code} - ${c.name}", style = MaterialTheme.typography.bodyMedium)
                                    Text(c.symbol, style = MaterialTheme.typography.bodySmall)
                                }
                                if (c.code == balanceCurrency) {
                                    Text("BALANCE", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CategoryPickerDialog(
    categories: List<com.spendingtracker.app.data.local.entity.Category>,
    onDismiss: () -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onDeleteCategory: ((com.spendingtracker.app.data.local.entity.Category) -> Unit)? = null,
    onCreateCategory: ((String, Int) -> Unit)? = null
) {
    val listMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.35f).dp.coerceAtMost(240.dp)
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val colorOptions = listOf(0xFF6200EE, 0xFF03DAC5, 0xFFE91E63, 0xFF4CAF50, 0xFF2196F3, 0xFFFF9800)
    var newColor by remember { mutableStateOf(colorOptions.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select category") },
        text = {
            Column {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = listMaxHeight)) {
                    item {
                        OutlinedButton(onClick = { onCategorySelected(null) }, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                            Text("None (no category)")
                        }
                    }
                    items(categories.size) { idx ->
                        val cat = categories[idx]
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { onCategorySelected(cat.id) }, modifier = Modifier.weight(1f).padding(4.dp)) {
                                Text(cat.name)
                            }
                            if (onDeleteCategory != null) {
                                TextButton(onClick = { onDeleteCategory(cat) }) { Text("\u2715") }
                            }
                        }
                    }
                }
                if (onCreateCategory != null) {
                    if (!adding) {
                        TextButton(onClick = { adding = true }) { Text("+ New category") }
                    } else {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Category name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            colorOptions.forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(
                                            width = if (c == newColor) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                        .clickable { newColor = c }
                                )
                            }
                        }
                        Row {
                            TextButton(onClick = { adding = false; newName = "" }) { Text("Cancel") }
                            TextButton(onClick = {
                                val n = newName.trim()
                                if (n.isNotEmpty()) {
                                    onCreateCategory(n, newColor.toInt())
                                    newName = ""
                                    adding = false
                                }
                            }) { Text("Add") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
