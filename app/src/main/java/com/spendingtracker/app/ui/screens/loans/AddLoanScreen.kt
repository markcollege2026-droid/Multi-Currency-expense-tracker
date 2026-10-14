package com.spendingtracker.app.ui.screens.loans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.ui.components.DueDatePickerDialog
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanScreen(
    loanId: Long?,
    onBack: () -> Unit,
    viewModel: AddLoanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(loanId) { viewModel.loadLoan(loanId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "Edit Loan/Debt" else "Add Loan/Debt") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = uiState.type == LoanDebt.TYPE_DEBT, onClick = { viewModel.onTypeChanged(LoanDebt.TYPE_DEBT) }, label = { Text("I owe") })
                    FilterChip(selected = uiState.type == LoanDebt.TYPE_LOAN, onClick = { viewModel.onTypeChanged(LoanDebt.TYPE_LOAN) }, label = { Text("Owed to me") })
                }
            }
            item {
                OutlinedTextField(value = uiState.personName, onValueChange = viewModel::onPersonChanged, label = { Text("Person / Name") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(value = uiState.amount, onValueChange = viewModel::onAmountChanged, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = uiState.currency, onValueChange = {}, label = { Text("Currency") }, readOnly = true, modifier = Modifier.weight(1f))
                    Button(onClick = { showCurrencyPicker = true }) { Text("Pick") }
                }
            }
            item {
                OutlinedTextField(value = uiState.note, onValueChange = viewModel::onNoteChanged, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Text("Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(uiState.date))}")
                Text("Due: ${uiState.dueDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it)) } ?: "Not set"}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDueDatePicker = true }) { Text("Pick due date") }
                    OutlinedButton(onClick = { viewModel.onDueDateChanged(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) }) { Text("In 7 days") }
                }
                if (uiState.dueDate != null) {
                    TextButton(onClick = { viewModel.onDueDateChanged(null) }) { Text("Clear due date") }
                }
            }
            item {
                if (uiState.error != null) Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.saveLoan(loanId, onBack) }, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isLoading) {
                    if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Save")
                }
            }

            if (uiState.isEditMode && loanId != null) {
                item {
                    Divider()
                    Text("Repayments", style = MaterialTheme.typography.titleLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = uiState.newRepaymentAmount, onValueChange = viewModel::onRepaymentAmountChanged, label = { Text("Repayment amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        Button(onClick = { viewModel.addRepayment(loanId) }) { Text("Add") }
                    }
                    OutlinedTextField(value = uiState.newRepaymentNote, onValueChange = viewModel::onRepaymentNoteChanged, label = { Text("Repayment note") }, modifier = Modifier.fillMaxWidth())
                }
                items(uiState.repayments) { rep ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${CurrencyFormatter.formatSimple(try { java.math.BigDecimal(rep.amount) } catch (e: Exception) { java.math.BigDecimal.ZERO }, uiState.currency)}")
                                Text(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(rep.date)), style = MaterialTheme.typography.bodySmall)
                                Text(rep.note ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.deleteRepayment(rep, loanId) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }

        if (showCurrencyPicker) {
            CurrencyPickerDialog(onDismiss = { showCurrencyPicker = false }, onCurrencySelected = { code -> viewModel.onCurrencyChanged(code); showCurrencyPicker = false })
        }

        if (showDueDatePicker) {
            DueDatePickerDialog(
                initialDateMillis = uiState.dueDate,
                onDismiss = { showDueDatePicker = false },
                onConfirm = { millis -> viewModel.onDueDateChanged(millis) }
            )
        }
    }
}
