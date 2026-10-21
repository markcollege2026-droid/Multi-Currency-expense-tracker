package com.spendingtracker.app.ui.screens.transactions

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
import com.spendingtracker.app.data.local.entity.Withdrawal
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AppDot
import com.spendingtracker.app.ui.components.AppEmptyState
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppPill
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.AppLoadingScreen
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
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
        uiState.filteredWithdrawals,
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
                uiState.filteredWithdrawals.map { it.date to it } +
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
            AppTopBar(title = "Transactions", onBack = onBack)
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
                    AppFilterChip(
                        selected = uiState.selectedType == "WITHDRAWAL",
                        onClick = { viewModel.onTypeSelected("WITHDRAWAL") },
                        label = "Withdrawals"
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
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        grouped.forEach { (day, dayItems) ->
                            item(key = "day_$day") {
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 3.dp, bottom = 1.dp)
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
                                        // Cash income is stored in the cash account's currency,
                                        // which may differ from the main balance currency.
                                        currency = uiState.accountsById[item.accountId]?.currency
                                            ?: uiState.balanceCurrency,
                                        onEdit = { onEditTopUp(item.id) },
                                        onDelete = { viewModel.deleteTopUp(item) }
                                    )
                                    is Withdrawal -> WithdrawalRow(
                                        withdrawal = item,
                                        toName = uiState.accountsById[item.toAccountId]?.name,
                                        onDelete = { viewModel.deleteWithdrawal(item) }
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
private fun ExpenseRow(
    expense: Expense,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(onClick = onEdit)
            .padding(start = if (compact) 9.dp else 12.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expense.currencyOriginal == currency) {
                        "-" + CurrencyFormatter.formatSimple(expense.convertedAsBigDecimal(), currency)
                    } else {
                        "${CurrencyFormatter.formatSimple(expense.originalAsBigDecimal(), expense.currencyOriginal)} → -" +
                            CurrencyFormatter.formatSimple(expense.convertedAsBigDecimal(), currency)
                    },
                    style = MaterialTheme.typography.bodyMedium,
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
                    style = MaterialTheme.typography.labelSmall,
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
            modifier = Modifier.size(30.dp)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
            .clickable(onClick = onEdit)
            .padding(start = if (compact) 9.dp else 12.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppDot(color = MaterialTheme.colorScheme.primary, sizeDp = 6)
        Spacer(Modifier.width(5.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+" + CurrencyFormatter.formatSimple(topUp.amountAsBigDecimal(), currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
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
                text = topUp.note ?: "Top-up",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIconButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = "Delete top-up",
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun WithdrawalRow(
    withdrawal: Withdrawal,
    toName: String?,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
            .padding(start = if (compact) 9.dp else 12.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "-" + CurrencyFormatter.formatSimple(withdrawal.amountFromAsBigDecimal(), withdrawal.currencyFrom) +
                        (if (withdrawal.currencyFrom == withdrawal.currencyTo) " → +" else " ≈ +") +
                        CurrencyFormatter.formatSimple(withdrawal.amountToAsBigDecimal(), withdrawal.currencyTo),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(withdrawal.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Withdrawal · main → " + (toName ?: "cash") +
                    (if (!withdrawal.note.isNullOrBlank()) " · " + withdrawal.note else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIconButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = "Delete withdrawal",
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun CreditCardPaymentRow(payment: CreditCardPayment, currency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Card payment · " + CurrencyFormatter.formatSimple(java.math.BigDecimal(payment.amount), currency),
                style = MaterialTheme.typography.bodyMedium,
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
