package com.spendingtracker.app.ui.screens.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.data.local.entity.RecurringPayment
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.*
import com.spendingtracker.app.ui.components.AppConfirmDialog
import com.spendingtracker.app.ui.screens.transactions.CategoryPickerDialog
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RecurringScreen(
    onBack: (() -> Unit)? = null,
    viewModel: RecurringViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val dims = LocalAppDimens.current
    var pendingDelete by remember { mutableStateOf<RecurringPayment?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Recurring payments",
                onBack = onBack,
                actions = {
                    AppIconButton(
                        onClick = viewModel::openAdd,
                        icon = Icons.Default.Add,
                        contentDescription = "Add recurring payment"
                    )
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = dims.screenPadding,
                    end = dims.screenPadding,
                    top = 4.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
            ) {
                if (uiState.payments.isEmpty()) {
                    item(key = "empty") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppEmptyState(
                                "No recurring payments yet.\nRent, subscriptions or any repeating payment posts " +
                                    "automatically to ${uiState.account?.name ?: "your account"}."
                            )
                            AppButton(onClick = viewModel::openAdd) { Text("Add recurring payment", maxLines = 1) }
                        }
                    }
                }
                items(uiState.payments, key = { it.id }) { payment ->
                    RecurringItem(
                        payment = payment,
                        dateFormat = dateFormat,
                        onEdit = { viewModel.openEdit(payment) },
                        onDelete = { pendingDelete = payment },
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

    pendingDelete?.let { payment ->
        AppConfirmDialog(
            title = "Delete recurring payment?",
            message = "\"${payment.title}\" stops posting to " +
                (uiState.account?.name ?: "your account") + ". Payments already recorded stay in the history.",
            confirmLabel = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.delete(payment)
                pendingDelete = null
            }
        )
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
    val compact = LocalCompactUi.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (payment.isActive) MaterialTheme.colorScheme.surfaceContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(onClick = onEdit)
            .padding(horizontal = if (compact) 9.dp else 12.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = payment.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = CurrencyFormatter.formatSimple(
                    payment.amountAsBigDecimal(),
                    payment.currencyOriginal
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        Text(
            text = frequencyLabel(payment.frequency) + " · next " +
                (if (payment.isActive) dateFormat.format(Date(payment.nextDueAt)) else "paused") +
                (payment.endDate?.let { " · until " + dateFormat.format(Date(it)) } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        payment.note?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (payment.isActive) "Pause" else "Resume",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleActive)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            AppIconButton(
                onClick = onDelete,
                icon = Icons.Default.Delete,
                contentDescription = "Delete recurring payment",
                modifier = Modifier.size(28.dp)
            )
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

    AppDialog(
        onDismissRequest = onDismiss,
        title = if (draft.id == null) "Add recurring payment" else "Edit recurring payment",
        confirmLabel = "Save",
        onConfirm = onSave,
        contentMaxHeightFraction = 0.62f
    ) {
        AppTextField(
            value = draft.title,
            onValueChange = { onDraftChanged(draft.copy(title = it)) },
            label = "Title (e.g. Rent)",
            imeAction = ImeAction.Next
        )
        AppTextField(
            value = draft.amount,
            onValueChange = { onDraftChanged(draft.copy(amount = it)) },
            label = "Amount",
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        )
        AppSelectorRow(
            label = "Currency",
            value = draft.currency,
            onClick = { showCurrencyPicker = true }
        )
        AppSelectorRow(
            label = "Category",
            value = categories.find { it.id == draft.categoryId }?.name ?: "No category",
            onClick = { showCategoryPicker = true }
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            RecurringPayment.FREQUENCIES.forEach { freq ->
                AppFilterChip(
                    selected = draft.frequency == freq,
                    onClick = { onDraftChanged(draft.copy(frequency = freq)) },
                    label = frequencyLabel(freq)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Starts ${dateFormat.format(Date(draft.startDate))}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            SmallAction("Pick") { showStartPicker = true }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ends " + (draft.endDate?.let { dateFormat.format(Date(it)) } ?: "never"),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            SmallAction("Pick") { showEndPicker = true }
            if (draft.endDate != null) {
                SmallAction("Clear") { onDraftChanged(draft.copy(endDate = null)) }
            }
        }
        AppTextField(
            value = draft.note,
            onValueChange = { onDraftChanged(draft.copy(note = it)) },
            label = "Note (optional)",
            imeAction = ImeAction.Done
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
    }

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
            onConfirm = { millis ->
                onDraftChanged(draft.copy(startDate = millis))
                showStartPicker = false
            }
        )
    }
    if (showEndPicker) {
        DueDatePickerDialog(
            initialDateMillis = draft.endDate ?: draft.startDate,
            onDismiss = { showEndPicker = false },
            onConfirm = { millis ->
                onDraftChanged(draft.copy(endDate = millis))
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp)
    )
}
