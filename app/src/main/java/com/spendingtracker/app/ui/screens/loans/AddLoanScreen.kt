package com.spendingtracker.app.ui.screens.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.Repayment
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.*
import com.spendingtracker.app.ui.components.AppConfirmDialog
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import com.spendingtracker.app.util.parseAmountOrNull
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddLoanScreen(
    loanId: Long?,
    initialType: String? = null,
    onBack: () -> Unit,
    viewModel: AddLoanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var pendingRepaymentDelete by remember { mutableStateOf<Repayment?>(null) }
    var showSettle by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val hardwareKeypad = LocalHardwareKeypad.current
    val compact = LocalCompactUi.current

    LaunchedEffect(loanId, initialType) {
        viewModel.loadLoan(loanId, initialType)
        runCatching { focus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when {
                    uiState.isEditMode -> "Edit loan/debt"
                    uiState.type == LoanDebt.TYPE_LOAN -> "Add loan (owed to me)"
                    else -> "Add debt (I owe)"
                },
                onBack = {
                    keyboard?.hide()
                    onBack()
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = if (compact) 11.dp else 16.dp, vertical = 4.dp)
                ) {
                    uiState.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AppOutlinedButton(
                            onClick = {
                                keyboard?.hide()
                                onBack()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel", maxLines = 1) }
                        AppButton(
                            onClick = {
                                keyboard?.hide()
                                viewModel.saveLoan(loanId, onBack)
                            },
                            modifier = Modifier.weight(1.4f),
                            enabled = !uiState.isLoading &&
                                uiState.personName.isNotBlank() &&
                                uiState.amount.isNotBlank()
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (uiState.isEditMode) "Save changes" else "Save", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = if (compact) 11.dp else 16.dp,
                end = if (compact) 11.dp else 16.dp,
                top = 4.dp,
                bottom = 10.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            item(key = "type") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppFilterChip(
                        selected = uiState.type == LoanDebt.TYPE_DEBT,
                        onClick = { viewModel.onTypeChanged(LoanDebt.TYPE_DEBT) },
                        label = "I owe"
                    )
                    AppFilterChip(
                        selected = uiState.type == LoanDebt.TYPE_LOAN,
                        onClick = { viewModel.onTypeChanged(LoanDebt.TYPE_LOAN) },
                        label = "Owed to me"
                    )
                }
            }
            if (uiState.isSettled) {
                item(key = "settled_banner") {
                    Text(
                        text = "Settled - this loan is in the Archive. Reopen it from the Loans tab to record more payments.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 3
                    )
                }
            }
            item(key = "person") {
                AppTextField(
                    value = uiState.personName,
                    onValueChange = viewModel::onPersonChanged,
                    label = "Person / name",
                    imeAction = ImeAction.Next,
                    focusRequester = focus
                )
            }
            item(key = "amount") {
                AppTextField(
                    value = uiState.amount,
                    onValueChange = viewModel::onAmountChanged,
                    label = "Amount",
                    keyboardType = KeyboardType.Decimal,
                    placeholder = "0.00",
                    imeAction = ImeAction.Next
                )
            }
            item(key = "currency") {
                AppSelectorRow(
                    label = "Currency",
                    value = uiState.currency,
                    onClick = { showCurrencyPicker = true }
                )
            }
            item(key = "note") {
                AppTextField(
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChanged,
                    label = "Note (optional)",
                    imeAction = ImeAction.Done
                )
            }
            item(key = "dates") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        AppValueRow(
                            label = "Made",
                            value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(uiState.date))
                        )
                        AppValueRow(
                            label = "Due",
                            value = uiState.dueDate?.let {
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                            } ?: "Not set"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AppOutlinedButton(
                                onClick = { showDueDatePicker = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("Due date", maxLines = 1) }
                            AppOutlinedButton(
                                onClick = {
                                    viewModel.onDueDateChanged(
                                        System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("+7 days", maxLines = 1) }
                            if (uiState.dueDate != null) {
                                AppOutlinedButton(
                                    onClick = { viewModel.onDueDateChanged(null) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Clear", maxLines = 1) }
                            }
                        }
                    }
                }
            }

            if (uiState.isEditMode && loanId != null) {
                item(key = "repayments_header") {
                    AppSectionHeader("Repayments")
                }
                if (!uiState.isSettled) {
                    item(key = "record_payment") {
                        // One repayment path for the whole app: this opens the same
                        // dialog the Loans tab uses, so the payment can come from any
                        // balance in any currency instead of being external-only.
                        AppOutlinedButton(onClick = { showSettle = true }) {
                            Text("Record a payment…", maxLines = 1)
                        }
                    }
                }
                items(uiState.repayments, key = { "rep_${it.id}" }) { rep ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = CurrencyFormatter.formatSimple(
                                    try {
                                        java.math.BigDecimal(rep.amount)
                                    } catch (e: Exception) {
                                        java.math.BigDecimal.ZERO
                                    },
                                    uiState.currency
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(rep.date)) +
                                    (if (rep.id == uiState.settlementRepaymentId) " · settlement" else "") +
                                    (rep.note?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AppIconButton(
                            onClick = { pendingRepaymentDelete = rep },
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete repayment",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
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

        if (showDueDatePicker) {
            DueDatePickerDialog(
                initialDateMillis = uiState.dueDate,
                onDismiss = { showDueDatePicker = false },
                onConfirm = { millis ->
                    viewModel.onDueDateChanged(millis)
                    showDueDatePicker = false
                }
            )
        }

        if (showSettle && uiState.isEditMode && loanId != null) {
            SettleLoanDialog(
                loan = LoanDebt(
                    id = loanId,
                    type = uiState.type,
                    personName = uiState.personName,
                    // The field may be mid-edit or blank; the dialog only needs a
                    // readable "of X" figure, and remaining stays authoritative.
                    amountOriginal = (uiState.amount.parseAmountOrNull()
                        ?: uiState.remaining.max(BigDecimal.ZERO)).toPlainString(),
                    currencyOriginal = uiState.currency,
                    date = uiState.date,
                    dueDate = uiState.dueDate,
                    note = uiState.note.ifBlank { null },
                    isSettled = uiState.isSettled
                ),
                remaining = uiState.remaining,
                accounts = uiState.settleAccounts,
                balances = uiState.accountBalances,
                rateLookup = viewModel::conversionRate,
                activeAccountId = uiState.activeAccountId,
                onConfirm = { amount, accountId, rate, note ->
                    keyboard?.hide()
                    viewModel.settle(loanId, amount, accountId, rate, note)
                    showSettle = false
                },
                onDismiss = { showSettle = false }
            )
        }

        pendingRepaymentDelete?.let { repayment ->
            AppConfirmDialog(
                title = "Delete repayment?",
                message = "Removing this repayment reopens the loan if it settled it, and reverses any balance it moved. It re-adds " +
                    CurrencyFormatter.formatSimple(
                        try { java.math.BigDecimal(repayment.amount) } catch (e: Exception) { java.math.BigDecimal.ZERO },
                        uiState.currency
                    ) + " of the remaining balance.",
                confirmLabel = "Delete",
                onDismiss = { pendingRepaymentDelete = null },
                onConfirm = {
                    if (loanId != null) viewModel.deleteRepayment(repayment, loanId)
                    pendingRepaymentDelete = null
                }
            )
        }
    }
}
