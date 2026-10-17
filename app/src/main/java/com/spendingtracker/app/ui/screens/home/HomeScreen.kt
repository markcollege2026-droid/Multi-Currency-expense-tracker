package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTopUp: () -> Unit,
    onNavigateToRecurring: () -> Unit = {},
    onNavigateToTransactions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showQuickAdd by remember { mutableStateOf(false) }
    var showAdjustBalance by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Tracker") },
                actions = {
                    // Account switcher
                    TextButton(onClick = { accountMenuExpanded = true }) {
                        Text(uiState.activeAccount?.name ?: "Account", maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        uiState.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (${account.currency})") },
                                trailingIcon = {
                                    if (account.id == uiState.activeAccount?.id) Text("✓")
                                },
                                onClick = {
                                    viewModel.switchAccount(account.id)
                                    accountMenuExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Manage accounts…") },
                            onClick = {
                                accountMenuExpanded = false
                                onNavigateToSettings()
                            }
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Quick add")
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Balance Card with adjust option
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Main/debit balance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                IconButton(onClick = { showAdjustBalance = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Adjust balance", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                CurrencyFormatter.format(uiState.balance, uiState.balanceCurrency),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isNegativeBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showAdjustBalance = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Text("Adjust balance")
                            }
                            if (uiState.isLowBalance) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("⚠️ Low balance warning!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (uiState.isNegativeBalance) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Negative balance - you spent more than you have", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            if (uiState.isRatesStale) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("⚠️ Exchange rates are stale", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                item { BalanceAccountsPanel(uiState, viewModel) }

                // Quick stats
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(modifier = Modifier.weight(1f), title = "This week", amount = CurrencyFormatter.format(uiState.totalSpentWeek, uiState.balanceCurrency))
                        StatCard(modifier = Modifier.weight(1f), title = "This month", amount = CurrencyFormatter.format(uiState.totalSpentMonth, uiState.balanceCurrency))
                    }
                }

                // Top categories
                if (uiState.topCategories.isNotEmpty()) {
                    item {
                        Text("Top categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    items(uiState.topCategories) { (category, amount) ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category?.name ?: "Unknown")
                                Text(CurrencyFormatter.format(amount, uiState.balanceCurrency), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Spending trend simple
                if (uiState.spendingTrend.isNotEmpty()) {
                    item {
                        Text("Spending trend (last 7 days)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            uiState.spendingTrend.forEach { (date, amount) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val day = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(date))
                                    Text(day, fontSize = 10.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // Simple bar
                                    val max = uiState.spendingTrend.maxOfOrNull { it.second.toDouble() } ?: 1.0
                                    val height = if (max == 0.0) 4.dp else ((amount.toDouble() / max) * 40).dp.coerceAtLeast(4.dp)
                                    Box(modifier = Modifier.width(20.dp).height(height).padding(2.dp), contentAlignment = Alignment.BottomCenter) {
                                        Card(modifier = Modifier.fillMaxWidth().height(height), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {}
                                    }
                                }
                            }
                        }
                    }
                }

                // Recurring payments entry
                item {
                    Card(modifier = Modifier.fillMaxWidth(), onClick = onNavigateToRecurring) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏰ Recurring payments", fontWeight = FontWeight.SemiBold)
                            Text("Manage →", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Recent transactions
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent transactions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onNavigateToTransactions) { Text("See all") }
                    }
                }
                items(uiState.recentExpenses) { expense ->
                    ExpenseItem(expense = expense, balanceCurrency = uiState.balanceCurrency, onEdit = { onEditExpense(expense.id) })
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onNavigateToAddExpense, modifier = Modifier.weight(1f)) { Text("Add expense") }
                        OutlinedButton(onClick = onNavigateToAddTopUp, modifier = Modifier.weight(1f)) { Text("Add money") }
                    }
                }
            }
        }

        if (showQuickAdd) {
            QuickAddBottomSheet(
                onDismiss = { showQuickAdd = false },
                onAddExpense = { showQuickAdd = false; onNavigateToAddExpense() },
                onAddMoney = { showQuickAdd = false; onNavigateToAddTopUp() },
                onAddRecurring = { showQuickAdd = false; onNavigateToRecurring() }
            )
        }

        if (showAdjustBalance) {
            AdjustBalanceDialog(
                currentBalance = uiState.balance,
                balanceCurrency = uiState.balanceCurrency,
                onDismiss = { showAdjustBalance = false },
                onConfirm = { desired ->
                    viewModel.adjustBalance(desired)
                    showAdjustBalance = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustBalanceDialog(
    currentBalance: BigDecimal,
    balanceCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal) -> Unit
) {
    var input by remember { mutableStateOf(currentBalance.toPlainString()) }
    var error by remember { mutableStateOf<String?>(null) }

    val currentFormatted = remember(currentBalance, balanceCurrency) {
        CurrencyFormatter.format(currentBalance, balanceCurrency)
    }
    val desired = remember(input) {
        try { BigDecimal(input) } catch (e: Exception) { null }
    }
    val diff = remember(desired, currentBalance) {
        desired?.subtract(currentBalance)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current balance: $currentFormatted", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    label = { Text("New balance ($balanceCurrency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                    supportingText = {
                        when {
                            error != null -> Text(error!!)
                            diff != null && diff.compareTo(BigDecimal.ZERO) != 0 -> {
                                val sign = if (diff > BigDecimal.ZERO) "+" else ""
                                Text(
                                    "Difference: $sign${CurrencyFormatter.format(diff, balanceCurrency)} will be logged as ${if (diff > BigDecimal.ZERO) "Top-up" else "Expense"} with note \"Balance adjustment\"",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            else -> Text("Enter desired total balance", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (diff != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (diff > BigDecimal.ZERO) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                if (diff > BigDecimal.ZERO) "Will add:" else if (diff < BigDecimal.ZERO) "Will deduct:" else "No change",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                CurrencyFormatter.format(diff.abs(), balanceCurrency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Logged as ${if (diff > BigDecimal.ZERO) "Top-up" else "Expense"} • Note: Balance adjustment", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = try { BigDecimal(input) } catch (e: Exception) { null }
                    if (parsed == null) {
                        error = "Invalid number"
                    } else {
                        onConfirm(parsed)
                    }
                },
                enabled = desired != null
            ) { Text("Adjust") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, amount: String) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ExpenseItem(expense: Expense, balanceCurrency: String, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("${CurrencyFormatter.formatSimple(expense.originalAsBigDecimal(), expense.currencyOriginal)} → -${CurrencyFormatter.formatSimple(expense.convertedAsBigDecimal(), balanceCurrency)}", fontWeight = FontWeight.SemiBold)
                Text(expense.note ?: "", style = MaterialTheme.typography.bodySmall)
                Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(expense.date)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onAddExpense: () -> Unit,
    onAddMoney: () -> Unit,
    onAddRecurring: () -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Quick add", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) { Text("Add Expense") }
            OutlinedButton(onClick = onAddMoney, modifier = Modifier.fillMaxWidth()) { Text("Add Money") }
            OutlinedButton(onClick = onAddRecurring, modifier = Modifier.fillMaxWidth()) { Text("Recurring payment") }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
