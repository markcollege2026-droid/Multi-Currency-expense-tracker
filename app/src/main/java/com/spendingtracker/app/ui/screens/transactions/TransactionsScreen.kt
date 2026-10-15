package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onBack: (() -> Unit)? = null,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    onAddExpense: () -> Unit,
    onAddTopUp: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    modifier = Modifier.width(120.dp).height(56.dp),
                    text = { Text("Expense") },
                    icon = { Text("-") }
                )
                ExtendedFloatingActionButton(
                    onClick = onAddTopUp,
                    modifier = Modifier.width(120.dp).height(56.dp),
                    text = { Text("Top-up") },
                    icon = { Text("+") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchChanged,
                    label = { Text("Search") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = uiState.selectedType == null, onClick = { viewModel.onTypeSelected(null) }, label = { Text("All") })
                    FilterChip(selected = uiState.selectedType == "EXPENSE", onClick = { viewModel.onTypeSelected("EXPENSE") }, label = { Text("Expenses") })
                    FilterChip(selected = uiState.selectedType == "TOPUP", onClick = { viewModel.onTypeSelected("TOPUP") }, label = { Text("Top-ups") })
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Group by day (memoized - recomputation with date formatting is expensive on big lists)
                val grouped = remember(uiState.filteredExpenses, uiState.filteredTopUps, uiState.cardPayments, uiState.selectedType, uiState.searchQuery) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    (uiState.filteredExpenses.map { it.date to it } + uiState.filteredTopUps.map { it.date to it } +
                        uiState.cardPayments.filter { uiState.selectedType == null && (uiState.searchQuery.isBlank() || "credit card payment ${it.status}".contains(uiState.searchQuery, ignoreCase = true)) }.map { it.date to it })
                        .sortedByDescending { it.first }
                        .groupBy { dateFormat.format(Date(it.first)) }
                }

                if (grouped.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No transactions yet. Add your first expense or top-up!", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grouped.forEach { (day, items) ->
                            item {
                                Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                            }
                            items(items) { (_, item) ->
                                when (item) {
                                    is com.spendingtracker.app.data.local.entity.CreditCardPayment -> {
                                        Card(Modifier.fillMaxWidth()) {
                                            Column(Modifier.padding(12.dp)) {
                                                Text("Credit card payment · ${CurrencyFormatter.formatSimple(java.math.BigDecimal(item.amount), uiState.balanceCurrency)}", fontWeight = FontWeight.Bold)
                                                Text(item.status.replace('_', ' '))
                                                Text("Main/debit → card #${item.cardId}")
                                            }
                                        }
                                    }
                                    is Expense -> {
                                        Card(modifier = Modifier.fillMaxWidth(), onClick = { onEditExpense(item.id) }) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${CurrencyFormatter.formatSimple(item.originalAsBigDecimal(), item.currencyOriginal)} → -${CurrencyFormatter.formatSimple(item.convertedAsBigDecimal(), uiState.balanceCurrency)}", fontWeight = FontWeight.SemiBold)
                                                    Text(item.note ?: "", style = MaterialTheme.typography.bodySmall)
                                                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.date)), style = MaterialTheme.typography.bodySmall)
                                                }
                                                IconButton(onClick = { viewModel.deleteExpense(item) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                                }
                                            }
                                        }
                                    }
                                    is TopUp -> {
                                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), onClick = { onEditTopUp(item.id) }) {
                                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("+${CurrencyFormatter.formatSimple(item.amountAsBigDecimal(), uiState.balanceCurrency)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                    Text(item.note ?: "Top-up", style = MaterialTheme.typography.bodySmall)
                                                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.date)), style = MaterialTheme.typography.bodySmall)
                                                }
                                                IconButton(onClick = { viewModel.deleteTopUp(item) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
