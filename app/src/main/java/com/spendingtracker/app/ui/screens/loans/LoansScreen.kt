package com.spendingtracker.app.ui.screens.loans

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
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onBack: () -> Unit,
    onAddLoan: () -> Unit,
    onEditLoan: (Long) -> Unit,
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loans & Debts") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddLoan) { Text("+") }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Summary in ${uiState.balanceCurrency}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Total owed by me: ${CurrencyFormatter.format(uiState.totalOwedByMe, uiState.balanceCurrency)}")
                            Text("Total owed to me: ${CurrencyFormatter.format(uiState.totalOwedToMe, uiState.balanceCurrency)}")
                        }
                    }
                }

                item { Text("I owe (Debts)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (uiState.debts.isEmpty()) {
                    item { Text("No debts. Tap + to add.", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(uiState.debts) { item ->
                        LoanItem(item = item, onEdit = { onEditLoan(item.loan.id) }, onDelete = { viewModel.deleteLoan(item.loan) }, onToggleSettled = { viewModel.toggleSettled(item.loan) })
                    }
                }

                item { Text("Owed to me (Loans)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                if (uiState.loans.isEmpty()) {
                    item { Text("No loans. Tap + to add.", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(uiState.loans) { item ->
                        LoanItem(item = item, onEdit = { onEditLoan(item.loan.id) }, onDelete = { viewModel.deleteLoan(item.loan) }, onToggleSettled = { viewModel.toggleSettled(item.loan) })
                    }
                }
            }
        }
    }
}

@Composable
fun LoanItem(item: LoanWithBalance, onEdit: () -> Unit, onDelete: () -> Unit, onToggleSettled: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onEdit, colors = CardDefaults.cardColors(containerColor = if (item.isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.loan.personName, fontWeight = FontWeight.Bold)
                if (item.isOverdue) Text("Overdue", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                if (item.loan.isSettled) Text("Settled", color = MaterialTheme.colorScheme.primary)
            }
            Text("${CurrencyFormatter.formatSimple(item.loan.amountAsBigDecimal(), item.loan.currencyOriginal)} - Remaining: ${CurrencyFormatter.formatSimple(item.remaining, item.loan.currencyOriginal)}")
            LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            Text("Due: ${item.loan.dueDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "No due date"}", style = MaterialTheme.typography.bodySmall)
            Text(item.loan.note ?: "", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleSettled) { Text(if (item.loan.isSettled) "Unsettle" else "Settle") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
        }
    }
}
