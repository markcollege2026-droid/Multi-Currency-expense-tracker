package com.spendingtracker.app.ui.screens.loans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AppBusyRow
import com.spendingtracker.app.ui.components.AppCard
import com.spendingtracker.app.ui.components.AppConfirmDialog
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppEmptyState
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.AppPill
import com.spendingtracker.app.ui.components.AppSectionHeader
import com.spendingtracker.app.ui.components.AppSelectorRow
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
    onAddLoan: (String) -> Unit,
    onEditLoan: (Long) -> Unit,
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val dims = LocalAppDimens.current

    var pendingDelete by remember { mutableStateOf<LoanWithBalance?>(null) }
    var pendingSettle by remember { mutableStateOf<LoanWithBalance?>(null) }
    var pendingReopen by remember { mutableStateOf<LoanWithBalance?>(null) }
    var showTypeChooser by remember { mutableStateOf(false) }
    // Settled loans leave the active page; the archive sits behind its own button.
    var showArchive by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showArchive) { showArchive = false }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (showArchive) "Settled archive" else "Loans & debts",
                onBack = if (showArchive) ({ showArchive = false }) else onBack,
                actions = {
                    if (!showArchive) {
                        // Next to the + : where settled loans now live.
                        AppPill(
                            text = if (uiState.archived.isEmpty()) "Archive"
                            else "Archive · ${uiState.archived.size}",
                            onClick = { showArchive = true },
                            contentDescription = "Open settled loans archive"
                        )
                        AppIconButton(
                            onClick = { showTypeChooser = true },
                            icon = Icons.Default.Add,
                            contentDescription = "Add loan or debt"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else if (showArchive) {
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
                item(key = "archive_intro") {
                    Text(
                        text = "Settled loans are kept here and as transactions in History. " +
                            "Reopening one reverses its settlement transaction.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
                if (uiState.archived.isEmpty()) {
                    item(key = "archive_empty") {
                        AppEmptyState("Nothing settled yet.\nSettling a loan moves it here.")
                    }
                } else {
                    items(uiState.archived, key = { "archived_${it.loan.id}" }) { item ->
                        ArchivedLoanItem(
                            item = item,
                            onEdit = { onEditLoan(item.loan.id) },
                            onDelete = { pendingDelete = item },
                            onReopen = { pendingReopen = item }
                        )
                    }
                }
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
                uiState.busy?.let { label ->
                    item(key = "busy") { AppBusyRow(label) }
                }
                uiState.message?.let { message ->
                    item(key = "message") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                modifier = Modifier.weight(1f)
                            )
                            AppPill(text = "Dismiss", onClick = viewModel::clearMessage)
                        }
                    }
                }

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
                    LoanSectionHeader(
                        title = "I owe (debts)",
                        // The section IS the type choice: the form opens as "I owe".
                        onAdd = { onAddLoan(LoanDebt.TYPE_DEBT) }
                    )
                }
                if (uiState.debts.isEmpty()) {
                    item(key = "no_debts") {
                        AppEmptyState("No active debts yet. Use + Add to add one — settled debts move to the Archive.")
                    }
                } else {
                    items(uiState.debts, key = { "debt_${it.loan.id}" }) { item ->
                        LoanItem(
                            item = item,
                            onEdit = { onEditLoan(item.loan.id) },
                            onDelete = { pendingDelete = item },
                            onSettle = { pendingSettle = item }
                        )
                    }
                }

                item(key = "loans_header") {
                    LoanSectionHeader(
                        title = "Owed to me (loans)",
                        // Opens the form as "Owed to me" instead of the old "I owe".
                        onAdd = { onAddLoan(LoanDebt.TYPE_LOAN) }
                    )
                }
                if (uiState.loans.isEmpty()) {
                    item(key = "no_loans") {
                        AppEmptyState("No active loans yet.")
                    }
                } else {
                    items(uiState.loans, key = { "loan_${it.loan.id}" }) { item ->
                        LoanItem(
                            item = item,
                            onEdit = { onEditLoan(item.loan.id) },
                            onDelete = { pendingDelete = item },
                            onSettle = { pendingSettle = item }
                        )
                    }
                }
            }
        }
    }

    if (showTypeChooser) {
        LoanTypeChooserDialog(
            onDismiss = { showTypeChooser = false },
            onChoose = { type ->
                showTypeChooser = false
                onAddLoan(type)
            }
        )
    }

    pendingSettle?.let { item ->
        SettleLoanDialog(
            loan = item.loan,
            remaining = item.remaining,
            accounts = uiState.settleAccounts,
            balances = uiState.accountBalances,
            rateLookup = viewModel::conversionRate,
            activeAccountId = uiState.activeAccountId,
            onConfirm = { amount, accountId, rate, note ->
                viewModel.settle(item, amount, accountId, rate, note)
                pendingSettle = null
            },
            onDismiss = { pendingSettle = null }
        )
    }

    pendingDelete?.let { item ->
        AppConfirmDialog(
            title = "Delete loan?",
            message = "\"${item.loan.personName}\" and its repayment history will be deleted. " +
                "Any settlement money still sitting in a balance is reversed.",
            confirmLabel = "Delete",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                viewModel.deleteLoan(item.loan)
                pendingDelete = null
            }
        )
    }

    pendingReopen?.let { item ->
        AppConfirmDialog(
            title = "Reopen this loan?",
            message = "\"${item.loan.personName}\" goes back to the active list. " +
                "The settlement transaction is deleted, so money paid from a balance returns to it " +
                "(or is removed from the balance it was received into).",
            confirmLabel = "Reopen",
            destructive = false,
            onDismiss = { pendingReopen = null },
            onConfirm = {
                viewModel.reopen(item.loan)
                pendingReopen = null
            }
        )
    }
}

/** Section title with its own + Add, which creates a loan of that section's type. */
@Composable
private fun LoanSectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppSectionHeader(title, modifier = Modifier.weight(1f))
        Text(
            text = "+ Add",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 5.dp, vertical = 4.dp)
        )
    }
}

/**
 * Chooses the loan type *before* the form opens, so the form can never come up
 * as "I owe" when the user meant "Owed to me".
 */
@Composable
fun LoanTypeChooserDialog(onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = "Add loan or debt",
        confirmLabel = null,
        onConfirm = null
    ) {
        AppSelectorRow(
            label = "I owe",
            value = "Money I have to pay back",
            onClick = { onChoose(LoanDebt.TYPE_DEBT) }
        )
        AppSelectorRow(
            label = "Owed to me",
            value = "Money someone will pay me back",
            onClick = { onChoose(LoanDebt.TYPE_LOAN) }
        )
    }
}

@Composable
fun LoanItem(item: LoanWithBalance, onEdit: () -> Unit, onDelete: () -> Unit, onSettle: () -> Unit) {
    val compact = LocalCompactUi.current
    val accent = if (item.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
                    text = "Settle",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onSettle)
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

/** A settled loan in the archive: read-mostly, with Reopen and Delete. */
@Composable
fun ArchivedLoanItem(
    item: LoanWithBalance,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReopen: () -> Unit
) {
    val compact = LocalCompactUi.current
    val isDebt = item.loan.type == LoanDebt.TYPE_DEBT
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                Text(
                    text = "SETTLED",
                    style = AppType.eyebrow,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = (if (isDebt) "I owed · " else "Owed to me · ") +
                    CurrencyFormatter.formatSimple(item.loan.amountAsBigDecimal(), item.loan.currencyOriginal) +
                    " · repaid " + CurrencyFormatter.formatSimple(item.totalRepaid, item.loan.currencyOriginal),
                style = MaterialTheme.typography.bodySmall.merge(AppType.tabular),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Settled " + (item.loan.settledAt?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                } ?: "before this version") +
                    " · Made " + SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.loan.date)) +
                    " · in History as a transaction",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Reopen",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onReopen)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
                Spacer(Modifier.weight(1f))
                AppIconButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete settled loan",
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
