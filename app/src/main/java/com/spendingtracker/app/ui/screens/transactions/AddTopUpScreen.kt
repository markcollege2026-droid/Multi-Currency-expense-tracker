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
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.DueDatePickerDialog
import com.spendingtracker.app.ui.components.AppOutlinedButton
import com.spendingtracker.app.ui.components.AppSectionHeader
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.ui.components.AppSelectorRow
import com.spendingtracker.app.ui.theme.AppType
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

    LaunchedEffect(topUpId) {
        viewModel.loadTopUp(topUpId)
        amountFocus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (uiState.isEditMode) "Edit income" else "Add income",
                onBack = {
                    keyboard?.hide()
                    onBack()
                },
                actions = {
                    if (uiState.isEditMode) {
                        AppIconButton(
                            onClick = { showDeleteConfirm = true },
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete income",
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
                            enabled = !uiState.isLoading && uiState.amount.isNotBlank()
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (uiState.isEditMode) "Save changes" else "Add money", maxLines = 1)
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
                        text = "INCOME TO ${uiState.balanceCurrency}",
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
                    Text(
                        text = "Balance operations use ${uiState.balanceCurrency} only",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    if (showDeleteConfirm && topUpId != null) {
        AppConfirmDialog(
            title = "Delete income?",
            message = "The income entry will be removed and the balance reduced. This cannot be undone.",
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
