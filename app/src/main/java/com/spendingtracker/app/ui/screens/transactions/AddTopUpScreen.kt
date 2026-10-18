package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.spendingtracker.app.ui.components.AppOutlinedButton
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.ui.components.AppValueRow
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

    LaunchedEffect(topUpId) {
        viewModel.loadTopUp(topUpId)
        amountFocus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (uiState.isEditMode) "Edit top-up" else "Add money",
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
            Text(
                text = "Balance operations use ${uiState.balanceCurrency} only",
                style = MaterialTheme.typography.labelSmall,
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
            AppTextField(
                value = uiState.note,
                onValueChange = viewModel::onNoteChanged,
                label = "Note (optional)",
                placeholder = "e.g. salary",
                imeAction = ImeAction.Done
            )
            AppValueRow(
                label = "Date",
                value = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(uiState.date))
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}
