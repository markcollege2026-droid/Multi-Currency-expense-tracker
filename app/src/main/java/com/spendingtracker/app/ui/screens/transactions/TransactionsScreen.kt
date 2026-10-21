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

    // Group by day. Memoized because date formatting is expensive on big lists.
    val grouped = remember(
        uiState.filteredExpenses,
        uiState.filteredTopUps,
        uiState.cardPayments,
        uiState.selectedType,
        uiState.searchQuery,
        uiState.selectedCategoryId,
        uiState.selectedCurrency
    ) {
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        (
            uiState.filteredExpenses.map { it.date to it } +
                uiState.filteredTopUps.map { it.date to it } +
                uiState.cardPayments.filter {
                    (uiState.selectedType == null || uiState.selectedType == "CARD_PAYMENT") &&
                        uiState.selectedCategoryId == null &&
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
                                        onDelete = { viewModel.deleteExpense(item) }
                                    )
                                    is TopUp -> TopUpRow(
                                        topUp = item,
                                        currency = uiState.balanceCurrency,
                                        onEdit = { onEditTopUp(item.id) },
                                        onDelete = { viewModel.deleteTopUp(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
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
