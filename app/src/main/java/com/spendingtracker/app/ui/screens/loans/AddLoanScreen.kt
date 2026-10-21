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
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.*
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddLoanScreen(
    loanId: Long?,
    onBack: () -> Unit,
    viewModel: AddLoanViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val hardwareKeypad = LocalHardwareKeypad.current
    val compact = LocalCompactUi.current

    LaunchedEffect(loanId) {
        viewModel.loadLoan(loanId)
        focus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (uiState.isEditMode) "Edit loan/debt" else "Add loan/debt",
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
                item(key = "repayment_form") {
                    AppTextField(
                        value = uiState.newRepaymentAmount,
                        onValueChange = viewModel::onRepaymentAmountChanged,
                        label = "Repayment amount",
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    )
                }
                item(key = "repayment_note") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        AppTextField(
                            value = uiState.newRepaymentNote,
                            onValueChange = viewModel::onRepaymentNoteChanged,
                            label = "Repayment note",
                            imeAction = ImeAction.Done,
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            onClick = {
                                keyboard?.hide()
                                viewModel.addRepayment(loanId)
                            },
                            modifier = Modifier.widthIn(min = 52.dp),
                            enabled = uiState.newRepaymentAmount.isNotBlank()
                        ) { Text("Add", maxLines = 1) }
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
                                    (rep.note?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AppIconButton(
                            onClick = { viewModel.deleteRepayment(rep, loanId) },
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
    }
}
