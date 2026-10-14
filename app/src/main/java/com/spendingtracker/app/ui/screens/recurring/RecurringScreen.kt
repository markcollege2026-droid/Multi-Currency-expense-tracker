package com.spendingtracker.app.ui.screens.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.RecurringPayment
import com.spendingtracker.app.ui.components.DueDatePickerDialog
import com.spendingtracker.app.ui.screens.transactions.CategoryPickerDialog
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: (() -> Unit)? = null,
    viewModel: RecurringViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring payments") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openAdd) {
                Text("+")
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.payments.isEmpty()) {
                    item {
                        Text(
                            "No recurring payments yet. Tap + to add rent, subscriptions, or any repeating payment. They are posted automatically to ${uiState.account?.name ?: "your account"} on their due dates.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(uiState.payments, key = { it.id }) { payment ->
                    RecurringItem(
                        payment = payment,
                        dateFormat = dateFormat,
                        onEdit = { viewModel.openEdit(payment) },
                        onDelete = { viewModel.delete(payment) },
                        onToggleActive = { viewModel.toggleActive(payment) }
                    )
                }
            }
        }

        uiState.editing?.let { draft ->
            RecurringEditDialog(
                draft = draft,
                categories = uiState.categories,
                error = uiState.error,
                onDraftChanged = viewModel::onDraftChanged,
                onDismiss = viewModel::dismissDialog,
                onSave = viewModel::save
            )
        }

        uiState.message?.let {
            LaunchedEffect(it) {
                kotlinx.coroutines.delay(2500)
                viewModel.clearMessage()
            }
        }
    }
}

@Composable
private fun RecurringItem(
    payment: RecurringPayment,
    dateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (payment.isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(payment.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${CurrencyFormatter.formatSimple(payment.amountAsBigDecimal(), payment.currencyOriginal)} · ${frequencyLabel(payment.frequency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "Started: ${dateFormat.format(Date(payment.startDate))} · Next: ${if (payment.isActive) dateFormat.format(Date(payment.nextDueAt)) else "paused"}" +
                    (payment.endDate?.let { " · Until: ${dateFormat.format(Date(it))}" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
            payment.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onToggleActive) {
                    Text(if (payment.isActive) "Pause" else "Resume")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

fun frequencyLabel(frequency: String): String = when (frequency) {
    RecurringPayment.FREQ_DAILY -> "Daily"
    RecurringPayment.FREQ_WEEKLY -> "Weekly"
    RecurringPayment.FREQ_MONTHLY -> "Monthly"
    RecurringPayment.FREQ_YEARLY -> "Yearly"
    else -> frequency
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditDialog(
    draft: RecurringDraft,
    categories: List<Category>,
    error: String?,
    onDraftChanged: (RecurringDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Add recurring payment" else "Edit recurring payment") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onDraftChanged(draft.copy(title = it)) },
                    label = { Text("Title (e.g. Rent, Netflix)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.amount,
                    onValueChange = { onDraftChanged(draft.copy(amount = it)) },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.currency,
                        onValueChange = {},
                        label = { Text("Currency") },
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { showCurrencyPicker = true }) { Text("Pick") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = categories.find { it.id == draft.categoryId }?.name ?: "No category",
                        onValueChange = {},
                        label = { Text("Category") },
                        readOnly = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { showCategoryPicker = true }) { Text("Pick") }
                }
                Text("Frequency", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecurringPayment.FREQUENCIES.forEach { freq ->
                        FilterChip(
                            selected = draft.frequency == freq,
                            onClick = { onDraftChanged(draft.copy(frequency = freq)) },
                            label = { Text(frequencyLabel(freq)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Starts: ${dateFormat.format(Date(draft.startDate))}")
                    TextButton(onClick = { showStartPicker = true }) { Text("Pick date") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ends: ${draft.endDate?.let { dateFormat.format(Date(it)) } ?: "Never"}")
                    Row {
                        TextButton(onClick = { showEndPicker = true }) { Text("Pick") }
                        if (draft.endDate != null) {
                            TextButton(onClick = { onDraftChanged(draft.copy(endDate = null)) }) { Text("Clear") }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { onDraftChanged(draft.copy(note = it)) },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            onDismiss = { showCurrencyPicker = false },
            onCurrencySelected = { code ->
                onDraftChanged(draft.copy(currency = code))
                showCurrencyPicker = false
            }
        )
    }
    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = categories,
            onDismiss = { showCategoryPicker = false },
            onCategorySelected = { id ->
                onDraftChanged(draft.copy(categoryId = id))
                showCategoryPicker = false
            }
        )
    }
    if (showStartPicker) {
        DueDatePickerDialog(
            initialDateMillis = draft.startDate,
            onDismiss = { showStartPicker = false },
            onConfirm = { millis -> onDraftChanged(draft.copy(startDate = millis)) }
        )
    }
    if (showEndPicker) {
        DueDatePickerDialog(
            initialDateMillis = draft.endDate ?: draft.startDate,
            onDismiss = { showEndPicker = false },
            onConfirm = { millis -> onDraftChanged(draft.copy(endDate = millis)) }
        )
    }
}
