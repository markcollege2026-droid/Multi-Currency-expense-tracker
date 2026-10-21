package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Category
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.*
import com.spendingtracker.app.util.CurrencyProvider
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddExpenseScreen(
    expenseId: Long?,
    onBack: () -> Unit,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(expenseId) { viewModel.loadExpense(expenseId) }

    AddExpenseContent(
        state = uiState,
        onBack = {
            keyboard?.hide()
            onBack()
        },
        onSave = {
            keyboard?.hide()
            viewModel.saveExpense(expenseId, onBack)
        },
        onAmountChanged = viewModel::onAmountChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onOpenCurrency = { showCurrencyPicker = true },
        onOpenCategory = { showCategoryPicker = true },
        onOpenAccount = { showAccountPicker = true },
        onManualRateToggle = viewModel::onUseManualRateChanged,
        onManualRateChanged = viewModel::onManualRateChanged,
        onAllowOverdraft = viewModel::allowOverdraft
    )

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
    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = uiState.categories,
            onDismiss = { showCategoryPicker = false },
            onCategorySelected = { id ->
                viewModel.onCategoryChanged(id)
                showCategoryPicker = false
            },
            onDeleteCategory = { viewModel.deleteCategory(it) },
            onCreateCategory = { name, color -> viewModel.createCategory(name, color) }
        )
    }
    if (showAccountPicker) {
        AccountPickerDialog(
            accounts = uiState.paymentAccounts,
            selectedId = uiState.balanceAccountId ?: uiState.accountId,
            onDismiss = { showAccountPicker = false },
            onAccountSelected = { id ->
                viewModel.selectPaymentAccount(id)
                showAccountPicker = false
            }
        )
    }
}

@Composable
fun AddExpenseContent(
    state: AddExpenseUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onOpenCurrency: () -> Unit,
    onOpenCategory: () -> Unit,
    onOpenAccount: () -> Unit,
    onManualRateToggle: (Boolean) -> Unit,
    onManualRateChanged: (String) -> Unit,
    onAllowOverdraft: (Boolean) -> Unit,
    hardwareKeypad: Boolean = LocalHardwareKeypad.current
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val amountFocus = remember { FocusRequester() }
    val amountParsed = state.amount.trim().replace(',', '.').toBigDecimalOrNull()
    val amountError = state.amount.isNotBlank() && amountParsed == null
    val rateError = amountParsed != null && state.convertedAmount == null
    val supporting = when {
        amountError -> "Enter a valid amount"
        rateError -> "No rate available — set a manual rate"
        state.convertedAmount != null -> "≈ -" + fmt(state.convertedAmount, state.balanceCurrency) +
            (state.rateUsed?.let { "  @ ${it.stripTrailingZeros().toPlainString()}" } ?: "")
        else -> "Amount in ${state.currency}"
    }
    val selectedAccount = state.paymentAccounts.firstOrNull {
        it.id == (state.balanceAccountId ?: state.accountId)
    }
    val accountLabel = selectedAccount?.let {
        "${it.name} · ${it.type.replace('_', ' ').lowercase(Locale.getDefault())}" +
            if (it.currency != state.mainCurrency) " · ${it.currency}" else ""
    } ?: "Main"
    val category = state.categories.firstOrNull { it.id == state.categoryId }

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            amountFocus.requestFocus()
            if (hardwareKeypad) keyboard?.hide()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (state.isEditMode) "Edit expense" else "New expense",
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = if (LocalCompactUi.current) 11.dp else 16.dp, vertical = 4.dp)
                ) {
                    state.error?.let {
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
                        AppOutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                            Text("Cancel", maxLines = 1)
                        }
                        AppButton(
                            onClick = onSave,
                            modifier = Modifier.weight(1.5f),
                            enabled = !state.isLoading && state.convertedAmount != null
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(if (state.isEditMode) "Save changes" else "Save expense", maxLines = 1)
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
                .padding(horizontal = if (LocalCompactUi.current) 11.dp else 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (LocalCompactUi.current) 7.dp else 10.dp)
        ) {
            AppTextField(
                value = state.amount,
                onValueChange = onAmountChanged,
                label = "Amount (${state.currency})",
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal,
                isError = amountError || rateError,
                supportingText = supporting,
                focusRequester = amountFocus
            )

            AppSelectorRow(
                label = "Currency",
                value = "${state.currency} · ${CurrencyProvider.getSymbol(state.currency)}",
                onClick = onOpenCurrency
            )
            AppSelectorRow(
                label = "Category",
                value = category?.name ?: "None",
                onClick = onOpenCategory,
                leadingColor = category?.let { safeColor(it.color) }
            )

            if (state.currency != state.balanceCurrency) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onManualRateToggle(!state.useManualRate) }
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.useManualRate,
                        onCheckedChange = onManualRateToggle,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Manual rate", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    if (!state.useManualRate) {
                        Text(
                            "automatic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.useManualRate) {
                    AppTextField(
                        value = state.manualRate,
                        onValueChange = onManualRateChanged,
                        label = "1 ${state.currency} = ? ${state.balanceCurrency}",
                        keyboardType = KeyboardType.Decimal,
                        placeholder = "0.00"
                    )
                }
            }

            AppSelectorRow(
                label = "Pay from",
                value = accountLabel,
                onClick = onOpenAccount,
                enabled = state.paymentAccounts.isNotEmpty()
            )
            if (state.paymentAccounts.none { it.type == "CASH" || it.type == "CREDIT_CARD" }) {
                Text(
                    text = "Create cash or credit cards on Home to use them here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (selectedAccount?.type == "CASH") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAllowOverdraft(!state.allowOverdraft) }
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.allowOverdraft,
                        onCheckedChange = onAllowOverdraft,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Allow cash overdraft",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            AppValueRow(
                label = "Date",
                value = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(state.date))
            )

            AppTextField(
                value = state.note,
                onValueChange = onNoteChanged,
                label = "Note (optional)",
                placeholder = "e.g. lunch",
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

private fun safeColor(value: Int): Color = try {
    Color(value)
} catch (e: Exception) {
    Color.Gray
}

private fun fmt(value: BigDecimal?, currency: String): String =
    if (value == null) "" else com.spendingtracker.app.util.CurrencyFormatter.formatSimple(value, currency)

@Composable
fun CurrencyPickerDialog(
    orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    balanceCurrency: String = "USD",
    onDismiss: () -> Unit,
    onCurrencySelected: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, orderedCurrencies) {
        val q = query.lowercase().trim()
        if (q.isBlank()) orderedCurrencies.take(120)
        else orderedCurrencies.filter {
            it.code.lowercase().contains(q) || it.name.lowercase().contains(q)
        }.take(120)
    }
    val compact = LocalCompactUi.current
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * if (compact) 0.48f else 0.5f).dp

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Select currency",
        dismissLabel = "Cancel",
        contentScrollable = false
    ) {
        AppTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search",
            placeholder = "USD, euro…",
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        )
        Text(
            text = "Balance: $balanceCurrency",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filtered, key = { it.code }) { currency ->
                val isBalance = currency.code == balanceCurrency
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isBalance) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .clickable { onCurrencySelected(currency.code) }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${currency.code} · ${currency.name}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isBalance) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currency.symbol + if (isBalance) " · balance currency" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    if (isBalance) {
                        Text(
                            text = "BAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCategorySelected: (Long?) -> Unit,
    onDeleteCategory: ((Category) -> Unit)? = null,
    onCreateCategory: ((String, Int) -> Unit)? = null
) {
    var adding by remember { mutableStateOf(false) }
    val compact = LocalCompactUi.current
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * if (compact) 0.45f else 0.5f).dp

    if (adding && onCreateCategory != null) {
        AddCategoryDialog(
            onDismiss = { adding = false },
            onAdd = { name, color ->
                onCreateCategory(name, color)
                adding = false
            }
        )
        return
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Select category",
        dismissLabel = "Done",
        contentScrollable = false
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item(key = "none") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { onCategorySelected(null) }
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("None (no category)", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { onCategorySelected(category.id) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppDot(color = safeColor(category.color), sizeDp = 10)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (onDeleteCategory != null) {
                        AppIconButton(
                            onClick = { onDeleteCategory(category) },
                            icon = Icons.Default.Delete,
                            contentDescription = "Delete ${category.name}",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        if (onCreateCategory != null) {
            AppTextButton(onClick = { adding = true }) {
                Text("+ New category", style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    val colors = remember {
        listOf(0xFF4F378B, 0xFF006A6A, 0xFFB3261E, 0xFF386A20, 0xFF0061A4, 0xFF8B5000)
    }
    var color by remember { mutableStateOf(colors.first()) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = "New category",
        confirmLabel = "Add",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onAdd(name.trim(), color.toInt()) }
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Category name",
            focusRequester = focus
        )
        Text(
            text = "Colour",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            colors.forEach { option ->
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(option))
                        .border(
                            width = if (option == color) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable { color = option },
                    contentAlignment = Alignment.Center
                ) {
                    if (option == color) Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AccountPickerDialog(
    accounts: List<com.spendingtracker.app.data.local.entity.Account>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onAccountSelected: (Long) -> Unit
) {
    val compact = LocalCompactUi.current
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * if (compact) 0.5f else 0.6f).dp
    AppDialog(
        onDismissRequest = onDismiss,
        title = "Pay from",
        dismissLabel = "Cancel",
        contentScrollable = false
    ) {
        if (accounts.isEmpty()) {
            Text("No payment accounts", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxListHeight),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    val selected = account.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                            .clickable { onAccountSelected(account.id) }
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = account.type.replace('_', ' ').lowercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
