package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTopUp: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showQuickAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Tracker") },
                actions = {
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
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Text("🏠") }, label = { Text("Home") })
                NavigationBarItem(selected = false, onClick = onNavigateToTransactions, icon = { Text("📋") }, label = { Text("Transactions") })
                NavigationBarItem(selected = false, onClick = onNavigateToLoans, icon = { Text("💳") }, label = { Text("Loans") })
                NavigationBarItem(selected = false, onClick = onNavigateToSettings, icon = { Text("⚙️") }, label = { Text("Settings") })
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Balance Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text("Balance remaining", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                CurrencyFormatter.format(uiState.balance, uiState.balanceCurrency),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isNegativeBalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                            )
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
                onAddMoney = { showQuickAdd = false; onNavigateToAddTopUp() }
            )
        }
    }
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
fun QuickAddBottomSheet(onDismiss: () -> Unit, onAddExpense: () -> Unit, onAddMoney: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Quick add", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) { Text("Add Expense") }
            OutlinedButton(onClick = onAddMoney, modifier = Modifier.fillMaxWidth()) { Text("Add Money") }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
