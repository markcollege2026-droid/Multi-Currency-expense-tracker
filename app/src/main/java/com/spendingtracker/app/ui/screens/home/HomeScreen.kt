package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.*
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTopUp: () -> Unit,
    onNavigateToRecurring: () -> Unit = {},
    onNavigateToTransactions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showQuickAdd by remember { mutableStateOf(false) }
    var showAdjustBalance by remember { mutableStateOf(false) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Spending",
                actions = {
                    Box {
                        AppPill(
                            text = uiState.activeAccount?.name ?: "Account",
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.widthIn(max = if (compact) 88.dp else 110.dp),
                            contentDescription = "Switch account"
                        )
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                            modifier = Modifier.widthIn(max = 220.dp)
                        ) {
                            uiState.accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${account.name} (${account.currency})",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        if (account.id == uiState.activeAccount?.id) Text("✓")
                                    },
                                    onClick = {
                                        viewModel.switchAccount(account.id)
                                        accountMenuExpanded = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Manage accounts…", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    accountMenuExpanded = false
                                    onNavigateToSettings()
                                }
                            )
                        }
                    }
                    AppIconButton(
                        onClick = { showQuickAdd = true },
                        icon = Icons.Default.Add,
                        contentDescription = "Quick add"
                    )
                    AppIconButton(
                        onClick = onNavigateToSettings,
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            AppLoadingScreen(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = dims.screenPadding,
                    end = dims.screenPadding,
                    top = 4.dp,
                    bottom = 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
            ) {
                item(key = "balance") {
                    BalanceSummaryCard(
                        balance = uiState.balance,
                        currency = uiState.balanceCurrency,
                        isNegative = uiState.isNegativeBalance,
                        isLow = uiState.isLowBalance,
                        ratesStale = uiState.isRatesStale,
                        week = uiState.totalSpentWeek,
                        month = uiState.totalSpentMonth,
                        onAdjust = { showAdjustBalance = true }
                    )
                }

                item(key = "quick_actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppButton(
                            onClick = onNavigateToAddExpense,
                            modifier = Modifier.weight(1f)
                        ) { Text("+ Expense", maxLines = 1) }
                        AppButton(
                            onClick = onNavigateToAddTopUp,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) { Text("+ Money", maxLines = 1) }
                    }
                }

                item(key = "accounts") { BalanceAccountsPanel(uiState, viewModel) }

                item(key = "recent_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent transactions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        AppPill(
                            text = "See all",
                            onClick = onNavigateToTransactions,
                            contentDescription = "See all transactions"
                        )
                    }
                }

                val recent = uiState.recentExpenses.take(if (compact) 6 else 10)
                if (recent.isEmpty()) {
                    item(key = "recent_empty") {
                        AppEmptyState("No transactions yet. Add an expense or income above.")
                    }
                } else {
                    items(recent, key = { "expense_${it.id}" }) { expense ->
                        ExpenseItem(
                            expense = expense,
                            balanceCurrency = uiState.balanceCurrency,
                            onEdit = { onEditExpense(expense.id) },
                            categoryName = uiState.categories.firstOrNull { it.id == expense.categoryId }?.name
                        )
                    }
                }

                if (uiState.topCategories.isNotEmpty()) {
                    item(key = "categories_header") {
                        Text(
                            text = "Top categories",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(uiState.topCategories.take(if (compact) 4 else 5), key = { "cat_${it.first?.id ?: -1}" }) { (category, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppDot(
                                color = try {
                                    androidx.compose.ui.graphics.Color(category?.color ?: 0xFF888888.toInt())
                                } catch (e: Exception) {
                                    androidx.compose.ui.graphics.Color.Gray
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = category?.name ?: "Uncategorized",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = CurrencyFormatter.format(amount, uiState.balanceCurrency),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (uiState.spendingTrend.isNotEmpty()) {
                    item(key = "trend") {
                        SpendingTrendCard(
                            trend = uiState.spendingTrend,
                            currency = uiState.balanceCurrency
                        )
                    }
                }

                item(key = "recurring") {
                    AppSelectorRow(
                        label = "Recurring",
                        value = "Manage repeating payments",
                        onClick = onNavigateToRecurring
                    )
                }

                item(key = "loans") {
                    AppSelectorRow(
                        label = "Loans",
                        value = "I owe / owed to me",
                        onClick = onNavigateToLoans
                    )
                }

                item(key = "bottom_space") { Spacer(Modifier.height(2.dp)) }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddBottomSheet(
            onDismiss = { showQuickAdd = false },
            onAddExpense = { showQuickAdd = false; onNavigateToAddExpense() },
            onAddMoney = { showQuickAdd = false; onNavigateToAddTopUp() },
            onAddRecurring = { showQuickAdd = false; onNavigateToRecurring() }
        )
    }

    if (showAdjustBalance) {
        AdjustBalanceDialog(
            currentBalance = uiState.balance,
            balanceCurrency = uiState.balanceCurrency,
            onDismiss = { showAdjustBalance = false },
            onConfirm = { desired ->
                viewModel.adjustBalance(desired)
                showAdjustBalance = false
            }
        )
    }
}

@Composable
private fun BalanceSummaryCard(
    balance: BigDecimal,
    currency: String,
    isNegative: Boolean,
    isLow: Boolean,
    ratesStale: Boolean,
    week: BigDecimal,
    month: BigDecimal,
    onAdjust: () -> Unit
) {
    val compact = LocalCompactUi.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Main balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                AppPill(
                    text = "Adjust",
                    onClick = onAdjust,
                    contentDescription = "Adjust balance"
                )
            }
            Text(
                text = CurrencyFormatter.format(balance, currency),
                fontSize = if (compact) 24.sp else 25.sp,
                lineHeight = if (compact) 27.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                color = if (isNegative) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Week ${CurrencyFormatter.formatSimple(week, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Month ${CurrencyFormatter.formatSimple(month, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val warnings = buildList {
                if (isLow) add("Low balance")
                if (isNegative) add("Negative balance")
                if (ratesStale) add("Rates stale")
            }
            if (warnings.isNotEmpty()) {
                Text(
                    text = "⚠ " + warnings.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SpendingTrendCard(trend: List<Pair<Long, BigDecimal>>, currency: String) {
    val max = remember(trend) { trend.maxOfOrNull { it.second.toDouble() } ?: 0.0 }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Spending · last 7 days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                trend.forEach { (date, amount) ->
                    val height = if (max <= 0.0) 3.dp
                    else ((amount.toDouble() / max) * 26).dp.coerceAtLeast(3.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(height)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(date)),
                            fontSize = 7.sp,
                            lineHeight = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, amount: String) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    balanceCurrency: String,
    onEdit: () -> Unit,
    categoryName: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryName ?: expense.note ?: "Expense",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(expense.date))} · " +
                        CurrencyFormatter.formatSimple(expense.originalAsBigDecimal(), expense.currencyOriginal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(5.dp))
            Text(
                text = "-${CurrencyFormatter.formatSimple(expense.convertedAsBigDecimal(), balanceCurrency)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onAddExpense: () -> Unit,
    onAddMoney: () -> Unit,
    onAddRecurring: () -> Unit = {}
) {
    val compact = LocalCompactUi.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 15.dp else 20.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Quick add", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AppButton(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) { Text("Expense") }
            AppOutlinedButton(onClick = onAddMoney, modifier = Modifier.fillMaxWidth()) { Text("Income / add money") }
            AppOutlinedButton(onClick = onAddRecurring, modifier = Modifier.fillMaxWidth()) { Text("Recurring payment") }
        }
    }
}

@Composable
fun AdjustBalanceDialog(
    currentBalance: BigDecimal,
    balanceCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal) -> Unit
) {
    var input by remember(currentBalance, balanceCurrency) { mutableStateOf(currentBalance.toPlainString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val currentFormatted = remember(currentBalance, balanceCurrency) {
        CurrencyFormatter.format(currentBalance, balanceCurrency)
    }
    val desired = remember(input) {
        try { BigDecimal(input.trim().replace(',', '.')) } catch (e: Exception) { null }
    }
    val diff = remember(desired, currentBalance) { desired?.subtract(currentBalance) }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Adjust balance",
        confirmLabel = "Adjust",
        confirmEnabled = desired != null,
        onConfirm = {
            val parsed = try { BigDecimal(input.trim().replace(',', '.')) } catch (e: Exception) { null }
            if (parsed == null) error = "Invalid number" else onConfirm(parsed)
        }
    ) {
        Text(
            text = "Current: $currentFormatted",
            style = MaterialTheme.typography.labelMedium
        )
        AppTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = "New balance ($balanceCurrency)",
            keyboardType = KeyboardType.Decimal,
            allowNegative = true,
            isError = error != null,
            supportingText = error,
            focusRequester = focus
        )
        if (diff != null && diff.compareTo(BigDecimal.ZERO) != 0) {
            val positive = diff > BigDecimal.ZERO
            Text(
                text = (if (positive) "Will add " else "Will deduct ") +
                    CurrencyFormatter.format(diff.abs(), balanceCurrency) +
                    " as ${if (positive) "top-up" else "expense"} (balance adjustment)",
                style = MaterialTheme.typography.labelSmall,
                color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "Enter the desired total. The difference is logged as an adjustment.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
