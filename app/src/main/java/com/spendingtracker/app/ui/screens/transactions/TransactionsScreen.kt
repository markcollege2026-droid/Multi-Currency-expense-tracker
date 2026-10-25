package com.spendingtracker.app.ui.screens.transactions

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.data.local.entity.Repayment
import com.spendingtracker.app.data.local.entity.TopUp
import com.spendingtracker.app.data.local.entity.Withdrawal
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AddMenuOption
import com.spendingtracker.app.ui.components.AddMenuOptions
import com.spendingtracker.app.ui.components.AddMenuStyle
import com.spendingtracker.app.ui.components.AppAvatar
import com.spendingtracker.app.ui.components.AppCard
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppConfirmDialog
import com.spendingtracker.app.ui.components.AppDot
import com.spendingtracker.app.ui.components.AppEmptyState
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppPill
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.AppLoadingScreen
import com.spendingtracker.app.ui.components.AppTextButton
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppValueRow
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.ui.screens.home.WithdrawDialog
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.ui.theme.LocalAppPalette
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.*

/**
 * Rows show the time while it is today and the date once the day has passed, so
 * the list stays scannable without repeating the day header in every row.
 */
fun historyTimestamp(
    date: Long,
    now: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault()
): String {
    val calendar = Calendar.getInstance(locale)
    calendar.timeInMillis = date
    val day = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)
    calendar.timeInMillis = now
    val sameDay = day == calendar.get(Calendar.DAY_OF_YEAR) && year == calendar.get(Calendar.YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        year == calendar.get(Calendar.YEAR) -> "d MMM"
        else -> "d MMM yyyy"
    }
    return SimpleDateFormat(pattern, locale).format(Date(date))
}

/**
 * Empty state of a ledger. Naming the account is the point: "No transactions yet"
 * on a healed ledger read as data loss, when the rows were simply in another account.
 */
fun historyEmptyMessage(accountName: String?): String =
    "No entries in " + (accountName ?: "this account") + " yet.\n" +
        "Add an expense or deposit to get started."

/** "12 entries are in \"Old ledger\"" - the number of rows hiding in another account. */
fun otherAccountHint(entries: Int, accountName: String): String =
    "$entries " + (if (entries == 1) "entry is" else "entries are") + " in \"" + accountName + "\""

/** What the details sheet shows for one history row. */
data class TransactionDetails(
    val kind: String,
    val amount: BigDecimal,
    val currency: String,
    val secondary: Pair<BigDecimal, String>? = null,
    val rate: BigDecimal? = null,
    val category: String? = null,
    val account: String? = null,
    val method: String? = null,
    val note: String? = null,
    val date: Long,
    /** Money out of a balance (expense, debt paid) rather than money in. */
    val negative: Boolean = false,
    /** Null when the row has no editor (transfers, loans); the dialog then hides Edit. */
    val edit: (() -> Unit)? = null
)

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
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    // Tapping a row opens its details; editing is a choice, never automatic.
    var details by remember { mutableStateOf<TransactionDetails?>(null) }
    var detailsDelete by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Back closes the Add menu before leaving the tab.
    BackHandler(enabled = addOpen) { addOpen = false }

    // Filters the view model already supported but the screen never exposed.
    val currencies = remember(uiState.allExpenses, uiState.balanceCurrency) {
        (uiState.allExpenses.map { it.currencyOriginal } + uiState.balanceCurrency).distinct().sorted()
    }
    // Captured once: uiState is a delegated property, so its members cannot be
    // smart-cast inside the filter lambdas below.
    val periodStart = uiState.dateRangeStart
    val periodEnd = uiState.dateRangeEnd
    val filtersActive = uiState.selectedCategoryId != null ||
        uiState.selectedCurrency != null || periodStart != null

    // Group by day. Memoized because date formatting is expensive on big lists.
    val grouped = remember(
        uiState.filteredExpenses,
        uiState.filteredTopUps,
        uiState.filteredWithdrawals,
        uiState.filteredRepayments,
        uiState.cardPayments,
        uiState.selectedType,
        uiState.searchQuery,
        uiState.selectedCategoryId,
        uiState.selectedCurrency,
        periodStart,
        periodEnd
    ) {
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        (
            uiState.filteredExpenses.map { it.date to it } +
                uiState.filteredTopUps.map { it.date to it } +
                uiState.filteredWithdrawals.map { it.date to it } +
                uiState.filteredRepayments.map { it.date to it } +
                uiState.cardPayments.filter {
                    (uiState.selectedType == null || uiState.selectedType == "CARD_PAYMENT") &&
                        uiState.selectedCategoryId == null &&
                        (periodStart == null || it.date >= periodStart) &&
                        (periodEnd == null || it.date <= periodEnd) &&
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
                    // Same pop-out Add menu as Home, so adding is one decision
                    // everywhere instead of two unlabelled +/− buttons.
                    AppPill(
                        text = if (addOpen) "Close" else "+ Add",
                        selected = addOpen,
                        onClick = { addOpen = !addOpen },
                        contentDescription = if (addOpen) "Close add menu" else "Add transaction"
                    )
                }
                AddMenuOptions(
                    expanded = addOpen,
                    style = AddMenuStyle.Pills,
                    options = listOf(
                        AddMenuOption(
                            label = "Expense",
                            onClick = { addOpen = false; onAddExpense() },
                            primary = true
                        ),
                        AddMenuOption(
                            label = "Deposit",
                            onClick = { addOpen = false; onAddTopUp() },
                            emphasized = true
                        ),
                        AddMenuOption(
                            label = "Withdraw",
                            onClick = { addOpen = false; showWithdraw = true }
                        ),
                        AddMenuOption(
                            label = "Transfer",
                            onClick = { addOpen = false; showTransfer = true }
                        )
                    ),
                    modifier = Modifier.padding(top = 6.dp)
                )
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
                        label = "Deposits"
                    )
                    AppFilterChip(
                        selected = uiState.selectedType == "CARD_PAYMENT",
                        onClick = { viewModel.onTypeSelected("CARD_PAYMENT") },
                        label = "Card pmts"
                    )
                    AppFilterChip(
                        selected = uiState.selectedType == "WITHDRAWAL",
                        onClick = { viewModel.onTypeSelected("WITHDRAWAL") },
                        label = "Transfers"
                    )
                    AppFilterChip(
                        selected = uiState.selectedType == "LOAN",
                        onClick = { viewModel.onTypeSelected("LOAN") },
                        label = "Loans"
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

                val shownCount = uiState.filteredExpenses.size + uiState.filteredTopUps.size +
                    uiState.filteredWithdrawals.size + uiState.filteredRepayments.size
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

                // Feedback for actions started here (e.g. a withdrawal).
                uiState.message?.let { message ->
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

                // Entries in another account: never let that read as "wiped".
                val otherId = uiState.otherAccountId
                val otherName = uiState.otherAccountName
                if (otherId != null && otherName != null && uiState.otherAccountEntryCount > 0) {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = otherAccountHint(uiState.otherAccountEntryCount, otherName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                Text(
                                    text = "Shown: ${uiState.activeAccountName ?: "this account"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            AppPill(
                                text = "Switch",
                                onClick = { viewModel.switchAccount(otherId) },
                                selected = true,
                                contentDescription = "Switch to $otherName"
                            )
                        }
                    }
                }

                if (grouped.isEmpty()) {
                    AppEmptyState(
                        text = historyEmptyMessage(uiState.activeAccountName),
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
                                        // An expense paid from a foreign cash balance is stored
                                        // in that cash currency.
                                        currency = item.currencyConverted,
                                        onOpen = {
                                            details = TransactionDetails(
                                                kind = "Expense",
                                                negative = true,
                                                amount = item.convertedAsBigDecimal(),
                                                currency = item.currencyConverted,
                                                secondary = if (item.currencyOriginal != item.currencyConverted) {
                                                    item.originalAsBigDecimal() to item.currencyOriginal
                                                } else null,
                                                rate = item.rateAsBigDecimal(),
                                                category = uiState.categories.firstOrNull { it.id == item.categoryId }?.name,
                                                account = uiState.accountsById[item.balanceAccountId ?: item.accountId]?.name,
                                                method = item.paymentMethod,
                                                note = item.note,
                                                date = item.date,
                                                edit = { onEditExpense(item.id) }
                                            )
                                            detailsDelete = {
                                                pendingDelete = "This expense of " +
                                                    CurrencyFormatter.format(item.convertedAsBigDecimal(), item.currencyConverted) +
                                                    " will be removed and the balance restored." to
                                                    { viewModel.deleteExpense(item) }
                                            }
                                        },
                                        onDelete = {
                                            pendingDelete = "This expense of " +
                                                CurrencyFormatter.format(item.convertedAsBigDecimal(), uiState.balanceCurrency) +
                                                " will be removed and the balance restored." to
                                                { viewModel.deleteExpense(item) }
                                        }
                                    )
                                    is TopUp -> TopUpRow(
                                        topUp = item,
                                        // Cash income is stored in the cash account's currency.
                                        currency = uiState.accountsById[item.accountId]?.currency
                                            ?: uiState.balanceCurrency,
                                        onOpen = {
                                            details = TransactionDetails(
                                                kind = "Deposit",
                                                amount = item.amountAsBigDecimal(),
                                                currency = uiState.accountsById[item.accountId]?.currency
                                                    ?: uiState.balanceCurrency,
                                                account = uiState.accountsById[item.accountId]?.name,
                                                note = item.note,
                                                date = item.date,
                                                edit = { onEditTopUp(item.id) }
                                            )
                                            detailsDelete = {
                                                val c = uiState.accountsById[item.accountId]?.currency
                                                    ?: uiState.balanceCurrency
                                                pendingDelete = "This deposit of " +
                                                    CurrencyFormatter.format(item.amountAsBigDecimal(), c) +
                                                    " will be removed and the balance reduced." to
                                                    { viewModel.deleteTopUp(item) }
                                            }
                                        },
                                        onDelete = {
                                            pendingDelete = "This deposit of " +
                                                CurrencyFormatter.format(
                                                    item.amountAsBigDecimal(),
                                                    uiState.accountsById[item.accountId]?.currency
                                                        ?: uiState.balanceCurrency
                                                ) +
                                                " will be removed and the balance reduced." to
                                                { viewModel.deleteTopUp(item) }
                                        }
                                    )
                                    is Withdrawal -> WithdrawalRow(
                                        withdrawal = item,
                                        fromName = uiState.accountsById[item.fromAccountId]?.name,
                                        toName = uiState.accountsById[item.toAccountId]?.name,
                                        onOpen = {
                                            details = TransactionDetails(
                                                kind = "Transfer",
                                                amount = item.amountFromAsBigDecimal(),
                                                currency = item.currencyFrom,
                                                secondary = item.amountToAsBigDecimal() to item.currencyTo,
                                                rate = item.rateAsBigDecimal(),
                                                account = (uiState.accountsById[item.fromAccountId]?.name ?: "Main") +
                                                    " \u2192 " + (uiState.accountsById[item.toAccountId]?.name ?: "Cash"),
                                                note = item.note,
                                                date = item.date,
                                                negative = true,
                                                edit = null
                                            )
                                            detailsDelete = {
                                                pendingDelete = "This transfer of " +
                                                    CurrencyFormatter.format(item.amountFromAsBigDecimal(), item.currencyFrom) +
                                                    " returns to " + (uiState.accountsById[item.fromAccountId]?.name ?: "the source") +
                                                    " and is removed from " +
                                                    (uiState.accountsById[item.toAccountId]?.name ?: "the destination") + "." to
                                                    { viewModel.deleteWithdrawal(item) }
                                            }
                                        },
                                        onDelete = {
                                            pendingDelete = "This transfer of " +
                                                CurrencyFormatter.format(
                                                    item.amountFromAsBigDecimal(),
                                                    item.currencyFrom
                                                ) + " returns to " +
                                                (uiState.accountsById[item.fromAccountId]?.name ?: "the source balance") +
                                                " and is removed from " +
                                                (uiState.accountsById[item.toAccountId]?.name ?: "the destination") +
                                                "." to { viewModel.deleteWithdrawal(item) }
                                        }
                                    )
                                    is Repayment -> {
                                        val loan = uiState.loansById[item.loanDebtId]
                                        if (loan != null) {
                                            LoanRepaymentRow(
                                                repayment = item,
                                                loan = loan,
                                                accountName = item.accountId?.let { uiState.accountsById[it]?.name },
                                                onOpen = {
                                                    val settles = loan.settlementRepaymentId == item.id
                                                    details = TransactionDetails(
                                                        kind = if (settles) "Loan settlement" else "Loan repayment",
                                                        amount = item.amountAsBigDecimal(),
                                                        currency = loan.currencyOriginal,
                                                        secondary = item.accountAmountAsBigDecimalOrNull()?.let { moved ->
                                                            if (item.accountCurrency != null &&
                                                                item.accountCurrency != loan.currencyOriginal
                                                            ) moved to item.accountCurrency else null
                                                        },
                                                        rate = item.rateAsBigDecimalOrNull(),
                                                        account = (if (loan.type == LoanDebt.TYPE_DEBT) "Paid from " else "Received into ") +
                                                            (item.accountId?.let { uiState.accountsById[it]?.name }
                                                                ?: if (loan.type == LoanDebt.TYPE_DEBT) "external revenue" else "external"),
                                                        method = if (loan.type == LoanDebt.TYPE_DEBT) "I owe" else "Owed to me",
                                                        note = (loan.personName + (item.note?.takeIf { n -> n.isNotBlank() }?.let { " - $it" } ?: "")),
                                                        date = item.date,
                                                        negative = loan.type == LoanDebt.TYPE_DEBT,
                                                        edit = null
                                                    )
                                                    detailsDelete = {
                                                        pendingDelete = loanDeleteMessage(item, loan, settles) to
                                                            { viewModel.deleteRepayment(item) }
                                                    }
                                                },
                                                onDelete = {
                                                    val settles = loan.settlementRepaymentId == item.id
                                                    pendingDelete = loanDeleteMessage(item, loan, settles) to
                                                        { viewModel.deleteRepayment(item) }
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

    details?.let { detail ->
        TransactionDetailsDialog(
            details = detail,
            onDismiss = { details = null },
            onEdit = detail.edit?.let { edit ->
                {
                    details = null
                    edit()
                }
            },
            onDelete = detailsDelete?.let { delete ->
                {
                    details = null
                    delete()
                }
            }
        )
    }

    if (showTransfer) {
        TransferDialog(
            accounts = uiState.transferAccounts,
            balances = uiState.accountBalances,
            rateLookup = viewModel::conversionRate,
            onConfirm = { fromId, toId, amountFrom, amountTo, note ->
                viewModel.transfer(fromId, toId, amountFrom, amountTo, note)
                showTransfer = false
            },
            onDismiss = { showTransfer = false }
        )
    }

    if (showWithdraw) {
        WithdrawDialog(
            cashAccounts = uiState.cashAccounts,
            mainCurrency = uiState.balanceCurrency,
            mainBalance = uiState.mainBalance,
            // Cached/fallback rate only: History must not block on a refresh.
            rateLookup = viewModel::conversionRate,
            onConfirm = { cashId, amountFrom, amountTo, note ->
                viewModel.withdraw(cashId, amountFrom, amountTo, note)
                showWithdraw = false
            },
            onDismiss = { showWithdraw = false }
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

/**
 * Read-only details for a history row.
 *
 * Tapping a row used to jump straight into the editor, which made a quick look
 * impossible and risked accidental changes. Editing is now an explicit choice,
 * next to Delete and Close.
 */
@Composable
fun TransactionDetailsDialog(
    details: TransactionDetails,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val deleteAction: (@Composable () -> Unit)? = if (onDelete == null) null else {
        // Captured as a non-null local so the lambda never relies on a smart cast.
        val remove = onDelete
        {
            AppTextButton(onClick = remove) {
                Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
    val fullDate = remember(details.date) {
        SimpleDateFormat("EEEE d MMMM yyyy · HH:mm", Locale.getDefault()).format(Date(details.date))
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = details.kind + " details",
        confirmLabel = if (onEdit != null) "Edit" else null,
        onConfirm = onEdit,
        dismissLabel = "Close",
        extraAction = deleteAction
    ) {
        Text(
            text = details.kind.uppercase(),
            style = AppType.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = (if (details.negative) "-" else "+") +
                CurrencyFormatter.format(details.amount, details.currency),
            style = MaterialTheme.typography.headlineSmall.merge(AppType.tabular),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val secondary = details.secondary
        if (secondary != null && details.kind == "Transfer") {
            Text(
                text = "→ +" + CurrencyFormatter.format(secondary.first, secondary.second),
                style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (secondary != null && details.kind != "Transfer") {
            Text(
                text = "Entered as " + CurrencyFormatter.format(secondary.first, secondary.second),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        AppHairline()
        AppValueRow("Date", fullDate)
        details.account?.let { AppValueRow("Account", it) }
        details.category?.let { AppValueRow("Category", it) }
        details.method?.let { AppValueRow("Method", it.replace('_', ' ').lowercase(Locale.getDefault())) }
        val rate = details.rate
        if (rate != null && rate != BigDecimal.ONE && secondary != null) {
            AppValueRow("Rate", "1 ${details.currency} = ${rate.stripTrailingZeros().toPlainString()} ${secondary.second}")
        }
        details.note?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onEdit == null) {
            Text(
                text = when (details.kind) {
                    "Transfer" -> "Transfers are deleted and re-created instead of edited, so both balances always match."
                    "Loan settlement", "Loan repayment" ->
                        "Deleting this transaction reopens the loan and reverses the money through its account. Amounts are changed from the Loans tab."
                    else -> "This entry cannot be edited."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
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
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    TransactionCard(onClick = onOpen) {
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
                    text = historyTimestamp(expense.date),
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
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    TransactionCard(onClick = onOpen) {
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
                    text = historyTimestamp(topUp.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = topUp.note ?: "Deposit",
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
private fun WithdrawalRow(
    withdrawal: Withdrawal,
    fromName: String?,
    toName: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    TransactionCard(onClick = onOpen) {
        AppAvatar(
            text = "→",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            size = if (compact) 30.dp else 34.dp
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "-" + CurrencyFormatter.formatSimple(
                        withdrawal.amountFromAsBigDecimal(),
                        withdrawal.currencyFrom
                    ) + (if (withdrawal.isSameCurrency()) " → +" else " ≈ +") +
                        CurrencyFormatter.formatSimple(withdrawal.amountToAsBigDecimal(), withdrawal.currencyTo),
                    style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = historyTimestamp(withdrawal.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Transfer · " + (fromName ?: "source") + " \u2192 " + (toName ?: "cash") +
                    (if (!withdrawal.note.isNullOrBlank()) " · " + withdrawal.note else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIconButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = "Delete transfer",
            modifier = Modifier.size(32.dp),
            tint = palette.expense.copy(alpha = 0.75f)
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

/** Confirmation copy for deleting a loan repayment/settlement transaction. */
fun loanDeleteMessage(repayment: Repayment, loan: LoanDebt, settlesLoan: Boolean): String {
    val amount = CurrencyFormatter.format(repayment.amountAsBigDecimal(), loan.currencyOriginal)
    val money = when {
        repayment.accountId == null -> " No balance was involved."
        loan.type == LoanDebt.TYPE_DEBT -> " The money returns to the account it was paid from."
        else -> " The money is removed from the account it was received into."
    }
    val reopen = if (settlesLoan) {
        " \u201C" + loan.personName + "\u201D reopens and leaves the settled archive."
    } else {
        " The remaining amount owed increases again."
    }
    return "This loan payment of " + amount + " will be deleted." + reopen + money
}

@Composable
private fun LoanRepaymentRow(
    repayment: Repayment,
    loan: LoanDebt,
    accountName: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    val isDebt = loan.type == LoanDebt.TYPE_DEBT
    val settles = loan.settlementRepaymentId == repayment.id
    TransactionCard(onClick = onOpen) {
        AppAvatar(
            text = "L",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            size = if (compact) 30.dp else 34.dp
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = (if (isDebt) "-" else "+") +
                        CurrencyFormatter.formatSimple(repayment.amountAsBigDecimal(), loan.currencyOriginal),
                    style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDebt) MaterialTheme.colorScheme.onSurface else palette.income,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = historyTimestamp(repayment.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = (if (settles) "Loan settled" else "Loan repayment") + " · " + loan.personName +
                    " · " + (accountName ?: if (isDebt) "external revenue" else "external"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AppIconButton(
            onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = "Delete loan transaction",
            modifier = Modifier.size(32.dp),
            tint = palette.expense.copy(alpha = 0.75f)
        )
    }
}
