package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.CreditCardPayment
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AppAvatar
import com.spendingtracker.app.ui.components.AppCard
import com.spendingtracker.app.ui.components.AppConfirmDialog
import com.spendingtracker.app.ui.components.AppDot
import com.spendingtracker.app.ui.components.AppEmptyState
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppPill
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.AppLoadingScreen
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.ui.theme.LocalAppPalette
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionsScreen(
    onBack: (() -> Unit)? = null,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    onAddExpense: () -> Unit,
    onAddTopUp: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current

    // Deleting a transaction rewrites balances, so it always asks first.
    var pendingDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var showCategoryFilter by remember { mutableStateOf(false) }

    // Filters the view model already supported but the screen never exposed.
    val currencies = remember(uiState.allExpenses, uiState.balanceCurrency) {
        (uiState.allExpenses.map { it.currencyOriginal } + uiState.balanceCurrency).distinct().sorted()
    }
    val periodStart = uiState.dateRangeStart
    val filtersActive = uiState.selectedCategoryId != null ||
        uiState.selectedCurrency != null || uiState.dateRangeStart != null

    // Group by day. Memoized because date formatting is expensive on big lists.
    val grouped = remember(
        uiState.filteredExpenses,
        uiState.filteredTopUps,
        uiState.cardPayments,
        uiState.selectedType,
        uiState.searchQuery,
        uiState.selectedCategoryId,
        uiState.selectedCurrency,
        uiState.dateRangeStart,
        uiState.dateRangeEnd
    ) {
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        (
            uiState.filteredExpenses.map { it.date to it } +
                uiState.filteredTopUps.map { it.date to it } +
                uiState.cardPayments.filter {
                    (uiState.selectedType == null || uiState.selectedType == "CARD_PAYMENT") &&
                        uiState.selectedCategoryId == null &&
                        (uiState.dateRangeStart == null || it.date >= uiState.dateRangeStart) &&
                        (uiState.dateRangeEnd == null || it.date <= uiState.dateRangeEnd) &&
                        (uiState.selectedCurrency == null || uiState.selectedCurrency == uiState.balanceCurrency) &&
                        (uiState.searchQuery.isBlank() ||
                            "credit card payment ${it.status}".contains(uiState.searchQuery, ignoreCase = true))
                }.map { it.date to it }
            )
            .sortedByDescending { it.first }
            .groupBy { dateFormat.format(Date(it.first)) }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "History", onBack = onBack)
        }
    ) { padding ->
        if (uiState.isLoading) {
            AppLoadingScreen(modifier = Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = dims.screenPadding)
            ) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchChanged,
                        label = "Search",
                        placeholder = "note, amount…",
                        imeAction = ImeAction.Search,
                        modifier = Modifier.weight(1f)
                    )
                    AppPill(
                        text = "−",
                        onClick = onAddExpense,
                        contentDescription = "Add expense"
                    )
                    AppPill(
                        text = "+",
                        onClick = onAddTopUp,
                        contentDescription = "Add top-up"
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppFilterChip(
                        selected = uiState.selectedType == null,
                        onClick = { viewModel.onTypeSelected(null) },
                        label = "All"
                    )
                    AppFilterChip(
                        selected = uiState.selectedType == "EXPENSE",
                        onClick = { viewModel.onTypeSelected("EXPENSE") },
                        label = "Expenses"
                    )
                    AppFilterChip(
                        selected = uiState.selectedType == "TOPUP",
                        onClick = { viewModel.onTypeSelected("TOPUP") },
                        label = "Top-ups"
                    )
                    AppFilterChip(
                        selected = uiState.selectedType == "CARD_PAYMENT",
                        onClick = { viewModel.onTypeSelected("CARD_PAYMENT") },
                        label = "Card pmts"
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categoryName = uiState.categories.firstOrNull { it.id == uiState.selectedCategoryId }?.name
                    AppFilterChip(
                        selected = categoryName != null,
                        onClick = { showCategoryFilter = true },
                        label = categoryName ?: "Category"
                    )
                    AppFilterChip(
                        selected = uiState.selectedCurrency == null,
                        onClick = { viewModel.onCurrencySelected(null) },
                        label = "Any currency"
                    )
                    currencies.forEach { code ->
                        AppFilterChip(
                            selected = uiState.selectedCurrency == code,
                            onClick = {
                                viewModel.onCurrencySelected(if (uiState.selectedCurrency == code) null else code)
                            },
                            label = code
                        )
                    }
                    AppFilterChip(
                        selected = periodStart != null,
                        onClick = {
                            if (periodStart != null) {
                                viewModel.onDateRangeChanged(null, null)
                            } else {
                                val start = java.util.Calendar.getInstance().apply {
                                    add(java.util.Calendar.MONTH, -1)
                                }.timeInMillis
                                viewModel.onDateRangeChanged(start, System.currentTimeMillis())
                            }
                        },
                        label = "Last 30 days"
                    )
                    if (filtersActive) {
                        AppFilterChip(
                            selected = true,
                            onClick = viewModel::clearFilters,
                            label = "Clear filters"
                        )
                    }
                }

                val shownCount = uiState.filteredExpenses.size + uiState.filteredTopUps.size
                if (shownCount > 0) {
                    Text(
                        text = "$shownCount entries · spent " +
                            CurrencyFormatter.formatSimple(
                                uiState.filteredExpenses.fold(java.math.BigDecimal.ZERO) { sum, e ->
                                    sum + e.convertedAsBigDecimal()
                                },
                                uiState.balanceCurrency
                            ) + " · received " +
                            CurrencyFormatter.formatSimple(
                                uiState.filteredTopUps.fold(java.math.BigDecimal.ZERO) { sum, t ->
                                    sum + t.amountAsBigDecimal()
                                },
                                uiState.balanceCurrency
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (grouped.isEmpty()) {
                    AppEmptyState(
                        text = "No transactions yet.\nAdd an expense or top-up to get started.",
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        grouped.forEach { (day, dayItems) ->
                            item(key = "day_$day") {
                                Text(
                                    text = day.uppercase(),
                                    style = if (compact) AppType.eyebrowCompact else AppType.eyebrow,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 2.dp)
                                )
                            }
                            items(dayItems) { (_, item) ->
                                when (item) {
                                    is CreditCardPayment -> CreditCardPaymentRow(
                                        payment = item,
                                        currency = uiState.balanceCurrency
                                    )
                                    is Expense -> ExpenseRow(
                                        expense = item,
                                        currency = uiState.balanceCurrency,
                                        onEdit = { onEditExpense(item.id) },
                                        onDelete = {
                                            pendingDelete = "This expense of " +
                                                CurrencyFormatter.format(item.convertedAsBigDecimal(), uiState.balanceCurrency) +
                                                " will be removed and the balance restored." to
                                                { viewModel.deleteExpense(item) }
                                        }
                                    )
                                    is TopUp -> TopUpRow(
                                        topUp = item,
                                        currency = uiState.balanceCurrency,
                                        onEdit = { onEditTopUp(item.id) },
                                        onDelete = {
                                            pendingDelete = "This income of " +
                                                CurrencyFormatter.format(item.amountAsBigDecimal(), uiState.balanceCurrency) +
                                                " will be removed and the balance reduced." to
                                                { viewModel.deleteTopUp(item) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategoryFilter) {
        CategoryPickerDialog(
            categories = uiState.categories,
            onDismiss = { showCategoryFilter = false },
            onCategorySelected = { id ->
                viewModel.onCategorySelected(id)
                showCategoryFilter = false
            }
        )
    }

    pendingDelete?.let { (message, action) ->
        AppConfirmDialog(
            title = "Delete transaction?",
            message = message,
            confirmLabel = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                action()
                pendingDelete = null
            }
        )
    }
}

@Composable
private fun TransactionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        padding = PaddingValues(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    TransactionCard(onClick = onEdit) {
        AppAvatar(
            text = "−",
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            size = if (compact) 30.dp else 34.dp
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expense.currencyOriginal == currency) {
                        "-" + CurrencyFormatter.formatSimple(expense.convertedAsBigDecimal(), currency)
                    } else {
                        "${CurrencyFormatter.formatSimple(expense.originalAsBigDecimal(), expense.currencyOriginal)} → -" +
                            CurrencyFormatter.formatSimple(expense.convertedAsBigDecimal(), currency)
                    },
                    style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(expense.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!expense.note.isNullOrBlank()) {
                Text(
                    text = expense.note.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        AppIconButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = "Delete expense",
            modifier = Modifier.size(32.dp),
            tint = palette.expense.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun TopUpRow(
    topUp: TopUp,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    TransactionCard(onClick = onEdit) {
        AppAvatar(
            text = "+",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            size = if (compact) 30.dp else 34.dp
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+" + CurrencyFormatter.formatSimple(topUp.amountAsBigDecimal(), currency),
                    style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
                    fontWeight = FontWeight.Bold,
                    color = palette.income,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(topUp.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = topUp.note ?: "Income",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIconButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = "Delete top-up",
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CreditCardPaymentRow(payment: CreditCardPayment, currency: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatar(
                text = "V",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 30.dp
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Card payment · " + CurrencyFormatter.formatSimple(java.math.BigDecimal(payment.amount), currency),
                    style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = payment.status.replace('_', ' ').lowercase(Locale.getDefault()) +
                        " · debit → card #${payment.cardId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
