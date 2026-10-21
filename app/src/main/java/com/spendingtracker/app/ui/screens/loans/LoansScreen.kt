package com.spendingtracker.app.ui.screens.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AppCard
import com.spendingtracker.app.ui.components.AppEmptyState
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.AppSectionHeader
import com.spendingtracker.app.ui.components.AppStatTile
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.ui.components.AppValueRow
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.ui.theme.LocalAppPalette
import com.spendingtracker.app.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LoansScreen(
    onBack: (() -> Unit)? = null,
    onAddLoan: () -> Unit,
    onEditLoan: (Long) -> Unit,
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dims = LocalAppDimens.current
    val compact = LocalCompactUi.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Loans & debts",
                onBack = onBack,
                actions = {
                    AppIconButton(
                        onClick = onAddLoan,
                        icon = Icons.Default.Add,
                        contentDescription = "Add loan or debt"
                    )
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = dims.screenPadding,
                    end = dims.screenPadding,
                    top = 4.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
            ) {
                item(key = "summary") {
                    val net = uiState.totalOwedToMe.subtract(uiState.totalOwedByMe)
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AppStatTile(
                                    label = "I owe",
                                    value = CurrencyFormatter.format(uiState.totalOwedByMe, uiState.balanceCurrency),
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.width(8.dp))
                                AppStatTile(
                                    label = "Owed to me",
                                    value = CurrencyFormatter.format(uiState.totalOwedToMe, uiState.balanceCurrency),
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            AppValueRow(
                                label = "Net position in ${uiState.balanceCurrency}",
                                value = CurrencyFormatter.format(net, uiState.balanceCurrency),
                                valueColor = if (net < java.math.BigDecimal.ZERO) LocalAppPalette.current.expense
                                else LocalAppPalette.current.income
                            )
                        }
                    }
                }

                item(key = "debts_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppSectionHeader("I owe (debts)", modifier = Modifier.weight(1f))
                        Text(
                            text = "+ Add",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onAddLoan)
                                .padding(horizontal = 5.dp, vertical = 4.dp)
                        )
                    }
                }
                if (uiState.debts.isEmpty()) {
                    item(key = "no_debts") {
                        AppEmptyState("No debts yet. Use + Add or the keypad to add one.")
                    }
                } else {
                    items(uiState.debts, key = { "debt_${it.loan.id}" }) { item ->
                        LoanItem(
                            item = item,
                            onEdit = { onEditLoan(item.loan.id) },
                            onDelete = { viewModel.deleteLoan(item.loan) },
                            onToggleSettled = { viewModel.toggleSettled(item.loan) }
                        )
                    }
                }

                item(key = "loans_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppSectionHeader("Owed to me (loans)", modifier = Modifier.weight(1f))
                        Text(
                            text = "+ Add",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onAddLoan)
                                .padding(horizontal = 5.dp, vertical = 4.dp)
                        )
                    }
                }
                if (uiState.loans.isEmpty()) {
                    item(key = "no_loans") {
                        AppEmptyState("No loans yet.")
                    }
                } else {
                    items(uiState.loans, key = { "loan_${it.loan.id}" }) { item ->
                        LoanItem(
                            item = item,
                            onEdit = { onEditLoan(item.loan.id) },
                            onDelete = { viewModel.deleteLoan(item.loan) },
                            onToggleSettled = { viewModel.toggleSettled(item.loan) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoanItem(item: LoanWithBalance, onEdit: () -> Unit, onDelete: () -> Unit, onToggleSettled: () -> Unit) {
    val compact = LocalCompactUi.current
    val accent = when {
        item.isOverdue -> MaterialTheme.colorScheme.error
        item.loan.isSettled -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (item.isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
    Column(
        modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.loan.personName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (item.isOverdue) {
                Text(
                    text = "OVERDUE",
                    style = AppType.eyebrow,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (item.loan.isSettled) {
                Text(
                    text = "SETTLED",
                    style = AppType.eyebrow,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = CurrencyFormatter.formatSimple(item.loan.amountAsBigDecimal(), item.loan.currencyOriginal) +
                " · left " + CurrencyFormatter.formatSimple(item.remaining, item.loan.currencyOriginal),
            style = MaterialTheme.typography.bodySmall.merge(AppType.tabular),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainer
        )
        Text(
            text = "Made " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.loan.date)) +
                " · Due " + (item.loan.dueDate?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                } ?: "none"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.loan.note.isNullOrBlank()) {
            Text(
                text = item.loan.note.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (item.loan.isSettled) "Unsettle" else "Settle",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleSettled)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            AppIconButton(
                onClick = onDelete,
                icon = Icons.Default.Delete,
                contentDescription = "Delete loan",
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    }
}
