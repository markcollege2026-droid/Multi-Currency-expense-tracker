package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.AppButton
import com.spendingtracker.app.ui.components.AppCard
import com.spendingtracker.app.ui.components.AppConfirmDialog
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.DueDatePickerDialog
import com.spendingtracker.app.ui.components.AppOutlinedButton
import com.spendingtracker.app.ui.components.AppSectionHeader
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.ui.components.AppSelectorRow
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddTopUpScreen(
    topUpId: Long?,
    onBack: () -> Unit,
    viewModel: AddTopUpViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val amountFocus = remember { FocusRequester() }
    val hardwareKeypad = LocalHardwareKeypad.current
    val compact = LocalCompactUi.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    // Re-evaluated on every state change, so the confirm button and the cash
    // preview always agree with the repository's own validation.
    val form = deriveDepositForm(uiState)

    LaunchedEffect(topUpId) {
        viewModel.loadTopUp(topUpId)
        amountFocus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (uiState.isEditMode) "Edit deposit" else "Deposit",
                onBack = {
                    keyboard?.hide()
                    onBack()
                },
                actions = {
                    if (uiState.isEditMode) {
                        AppIconButton(
                            onClick = { showDeleteConfirm = true },
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete deposit",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
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
                                viewModel.saveTopUp(topUpId, onBack)
                            },
                            modifier = Modifier.weight(1.6f),
                            enabled = !uiState.isLoading && form.canSave
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (uiState.isEditMode) "Save changes" else "Add deposit", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 11.dp else 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DEPOSIT TO ${uiState.balanceCurrency}",
                        style = if (compact) AppType.eyebrowCompact else AppType.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppTextField(
                        value = uiState.amount,
                        onValueChange = viewModel::onAmountChanged,
                        label = "Amount in ${uiState.balanceCurrency}",
                        keyboardType = KeyboardType.Decimal,
                        placeholder = "0.00",
                        imeAction = ImeAction.Next,
                        focusRequester = amountFocus
                    )
                    val cash = uiState.sourceCash
                    Text(
                        text = if (cash == null) {
                            "External money - it does not come out of any balance."
                        } else {
                            "Moves money out of ${cash.name}; recorded as a transfer."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                    val cashSide = form.cashAmount
                    if (cash != null && cashSide != null) {
                        val rateNote = if (cash.currency != uiState.balanceCurrency) {
                            " · 1 " + cash.currency + " = " +
                                uiState.rate.stripTrailingZeros().toPlainString() + " " + uiState.balanceCurrency
                        } else {
                            ""
                        }
                        Text(
                            text = "≈ -" + CurrencyFormatter.format(cashSide, cash.currency) +
                                " from " + cash.name + rateNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2
                        )
                    }
                    form.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2
                        )
                    }
                }
            }
            if (!uiState.isEditMode && uiState.cashAccounts.isNotEmpty()) {
                AppSectionHeader("Source")
                AppCard {
                    AppSelectorRow(
                        label = "From",
                        value = uiState.sourceCash?.let { "${it.name} (${it.currency})" }
                            ?: "External - not from cash",
                        onClick = { showSourcePicker = true }
                    )
                }
            }
            AppSectionHeader("Details")
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppSelectorRow(
                        label = "Date",
                        value = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(uiState.date)),
                        onClick = { showDatePicker = true }
                    )
                    AppTextField(
                        value = uiState.note,
                        onValueChange = viewModel::onNoteChanged,
                        label = "Note (optional)",
                        placeholder = "e.g. salary",
                        imeAction = ImeAction.Done
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }

    if (showDatePicker) {
        DueDatePickerDialog(
            initialDateMillis = uiState.date,
            onDismiss = { showDatePicker = false },
            onConfirm = { millis ->
                viewModel.onDateChanged(millis)
                showDatePicker = false
            }
        )
    }
    if (showSourcePicker) {
        DepositSourceDialog(
            cashAccounts = uiState.cashAccounts,
            cashBalances = uiState.cashBalances,
            selectedId = uiState.sourceCashId,
            onDismiss = { showSourcePicker = false },
            onSelect = { id ->
                viewModel.selectSource(id)
                showSourcePicker = false
            }
        )
    }
    if (showDeleteConfirm && topUpId != null) {
        AppConfirmDialog(
            title = "Delete deposit?",
            message = "The deposit will be removed and the balance reduced. This cannot be undone.",
            confirmLabel = "Delete",
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                keyboard?.hide()
                viewModel.deleteTopUp(topUpId, onBack)
            }
        )
    }
}

/**
 * Mirrors [depositFormState] for the screen. Kept next to the UI so the enabled
 * state, the cash preview and the save call all read the same numbers.
 */
private fun deriveDepositForm(state: AddTopUpUiState): DepositFormState {
    val cash = state.sourceCash
    return depositFormState(
        amount = state.amount,
        rate = state.rate,
        cashBalance = if (cash == null) null else state.cashBalances[cash.id] ?: java.math.BigDecimal.ZERO,
        cashCurrency = cash?.currency
    )
}

/**
 * Picks where a deposit comes from. The first row is always the default:
 * external money that does not touch any balance.
 */
@Composable
private fun DepositSourceDialog(
    cashAccounts: List<com.spendingtracker.app.data.local.entity.Account>,
    cashBalances: Map<Long, java.math.BigDecimal>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    // Hoisted: the confirm action needs the same value the rows set.
    var picked by remember(selectedId) { mutableStateOf(selectedId) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = "Deposit from",
        confirmLabel = "Use selected",
        onConfirm = { onSelect(picked) }
    ) {
        Text(
            text = "EXTERNAL",
            style = AppType.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppSelectorRow(
            label = "Source",
            value = if (picked == null) "Not from cash (default) · selected" else "Not from cash (default)",
            onClick = { picked = null }
        )
        AppHairline()
        Text(
            text = "FROM A CASH BALANCE",
            style = AppType.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        cashAccounts.forEach { cash ->
            val balance = cashBalances[cash.id] ?: java.math.BigDecimal.ZERO
            AppSelectorRow(
                label = cash.name,
                value = CurrencyFormatter.formatSimple(balance, cash.currency) +
                    if (picked == cash.id) " · selected" else "",
                onClick = { picked = cash.id }
            )
        }
        Text(
            text = "Deposits funded from cash are transfers: the cash balance drops " +
                "and main rises by the same value, converted when the currencies differ.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
}
