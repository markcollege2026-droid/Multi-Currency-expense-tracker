package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.domain.AccountBalances
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.*
import com.spendingtracker.app.ui.screens.transactions.CurrencyPickerDialog
import com.spendingtracker.app.util.CurrencyFormatter
import com.spendingtracker.app.util.CurrencyProvider
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import com.spendingtracker.app.util.parseAmountOrNull

@Composable
fun BalanceAccountsPanel(
    state: HomeUiState,
    viewModel: HomeViewModel,
    createType: String?,
    onCreateTypeChange: (String?) -> Unit,
    onWithdraw: (Long) -> Unit = {}
) {
    var paymentCard by remember { mutableStateOf<Account?>(null) }
    var configureCard by remember { mutableStateOf<Account?>(null) }
    var cashAccount by remember { mutableStateOf<Account?>(null) }
    var editAccount by remember { mutableStateOf<Account?>(null) }
    var deleteAccount by remember { mutableStateOf<Account?>(null) }
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current
    // On the small screen the detailed account controls start collapsed; the
    // summary (total available) is always visible and one D-pad press away.
    var expanded by rememberSaveable { mutableStateOf(!compact) }

    // Any number of cash balances may exist per main balance, each in its own currency.
    val cashAccounts = state.balanceAccounts.filter { it.type == "CASH" }
    val cards = state.balanceAccounts.filter { it.type == "CREDIT_CARD" }
    val cardTotal = cards.fold(BigDecimal.ZERO) { sum, card ->
        sum + (state.accountBalances[card.id] ?: BigDecimal.ZERO)
    }

    AppCard {
        Column {
            val headerSource = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = headerSource,
                        indication = LocalIndication.current,
                        onClick = { expanded = !expanded }
                    )
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL AVAILABLE",
                        style = com.spendingtracker.app.ui.theme.AppType.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(state.totalAvailable, state.balanceCurrency),
                        style = MaterialTheme.typography.titleMedium.merge(com.spendingtracker.app.ui.theme.AppType.tabular),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!expanded) {
                Text(
                    text = "Cash " + CurrencyFormatter.formatSimple(state.cashTotal, state.balanceCurrency) +
                        " · Cards " + CurrencyFormatter.formatSimple(cardTotal, state.balanceCurrency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                return@Column
            }

            AppListRow(
                title = state.activeAccount?.name ?: "Main / debit",
                meta = "Main / debit · tap to edit",
                leading = { AccountBadge("M") },
                trailing = { AmountLabel(state.balance, state.balanceCurrency) },
                divider = true,
                onClick = { state.activeAccount?.let { editAccount = it } }
            )

            cashAccounts.forEach { cash ->
                val cashBalance = state.accountBalances[cash.id] ?: BigDecimal.ZERO
                AppListRow(
                    title = cash.name,
                    meta = "Cash · ${cash.currency} · tap to edit",
                    leading = {
                        AccountBadge(
                            "C",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    },
                    trailing = { AmountLabel(cashBalance, cash.currency) },
                    divider = true,
                    onClick = { editAccount = cash }
                )
                if (!cash.includedInTotal) {
                    Text(
                        text = "Set aside · not counted in total available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CashActions(
                    cash = cash,
                    mainCurrency = state.balanceCurrency,
                    onIncome = { cashAccount = cash },
                    // Seeding the id means the dialog opens on this very balance.
                    onWithdraw = { onWithdraw(cash.id) },
                    onToggleIncluded = { viewModel.setCashIncluded(cash, !cash.includedInTotal) }
                )
            }

            AppTextButton(onClick = { onCreateTypeChange("CASH") }) {
                Text(
                    text = if (cashAccounts.isEmpty()) "Set up cash balance" else "+ Add cash balance",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }

            cards.forEach { card ->
                val balance = state.accountBalances[card.id] ?: BigDecimal.ZERO
                AppListRow(
                    title = card.name,
                    meta = autopaySummary(card, balance, state),
                    leading = {
                        AccountBadge(
                            "V",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    },
                    trailing = { AmountLabel(balance, card.currency, negativeIsError = true) },
                    divider = true,
                    onClick = { editAccount = card }
                )
                if (card.autopayEnabled && AccountBalances.paymentAmount(card, balance) > state.balance) {
                    Text(
                        text = "Main funds are too low for autopay",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppTextButton(onClick = { paymentCard = card }, modifier = Modifier.weight(1f)) {
                        Text("Pay card", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    AppTextButton(onClick = { configureCard = card }, modifier = Modifier.weight(1f)) {
                        Text("Autopay", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    AppTextButton(onClick = { editAccount = card }, modifier = Modifier.weight(1f)) {
                        Text("Edit", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            AppTextButton(onClick = { onCreateTypeChange("CREDIT_CARD") }) {
                Text("+ Add credit card", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }

            Text(
                text = "Total available = main + cash counted in totals (converted) − card debt.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            state.cardPayments.filter { it.status == "FAILED_INSUFFICIENT_FUNDS" && !it.dismissed }.forEach { failed ->
                AppHairline()
                Text(
                    text = "Payment failed: insufficient main/debit funds — " +
                        (state.balanceAccounts.find { it.id == failed.cardId }?.name ?: "Card"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppTextButton(
                        onClick = { viewModel.payCard(failed.cardId, failed.amount, false) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Retry", style = MaterialTheme.typography.labelSmall) }
                    AppTextButton(
                        onClick = {
                            paymentCard = state.balanceAccounts.find { it.id == failed.cardId }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Partial", style = MaterialTheme.typography.labelSmall) }
                    AppTextButton(
                        onClick = { viewModel.dismissPayment(failed) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Ignore", style = MaterialTheme.typography.labelSmall) }
                }
            }

            val message = state.message
            if (message != null) {
                AppHairline()
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3
                )
                AppTextButton(onClick = viewModel::clearMessage) {
                    Text("Dismiss", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    createType?.let { type ->
        CreateAccountDialog(
            type = type,
            mainCurrency = state.balanceCurrency,
            orderedCurrencies = state.orderedCurrencies,
            onDismiss = { onCreateTypeChange(null) },
            onCreate = { name, opening, currency ->
                viewModel.createBalanceAccount(name, type, opening, currency)
                onCreateTypeChange(null)
            }
        )
    }
    cashAccount?.let { cash ->
        MoneyDialog(
            title = "Add cash to ${cash.name}",
            description = "External cash in ${cash.currency} - not a withdrawal from main",
            showOverpayment = false,
            amountLabel = "Amount (${cash.currency})",
            onDismiss = { cashAccount = null }
        ) { amount, _ ->
            viewModel.fundCash(cash.id, amount)
            cashAccount = null
        }
    }
    paymentCard?.let { card ->
        MoneyDialog(
            title = "Pay ${card.name}",
            description = "Deducts from main/debit, not category spending",
            showOverpayment = true,
            onDismiss = { paymentCard = null }
        ) { amount, overpayment ->
            viewModel.payCard(card.id, amount, overpayment)
            paymentCard = null
        }
    }
    configureCard?.let { card ->
        AutopayDialog(
            card = card,
            onDismiss = { configureCard = null }
        ) {
            viewModel.configureAutopay(it)
            configureCard = null
        }
    }
    editAccount?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { editAccount = null },
            onSave = { updated ->
                viewModel.updateBalanceAccount(updated)
                editAccount = null
            },
            onDelete = {
                editAccount = null
                deleteAccount = account
            }
        )
    }
    deleteAccount?.let { account ->
        val entries = state.accountEntries[account.id] ?: 0
        AppConfirmDialog(
            title = "Delete ${account.name}?",
            message = "This removes only the ${account.type.replace('_', ' ').lowercase()} balance \u201C${account.name}\u201D and its " +
                "$entries " + (if (entries == 1) "entry" else "entries") +
                " (expenses, deposits, payments and transfers); " +
                "transferred money returns to the balances it came from. " +
                "Every other account and its data are untouched, and loans are shared and kept " +
                "(settlements paid from this balance become external). This cannot be undone.",
            confirmLabel = "Delete",
            onDismiss = { deleteAccount = null },
            onConfirm = {
                viewModel.deleteBalanceAccount(account)
                deleteAccount = null
            }
        )
    }
}

/**
 * Actions under a cash balance. They wrap on the 240dp window instead of being
 * squeezed into three equal columns, so no label is ever clipped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CashActions(
    cash: Account,
    mainCurrency: String,
    onIncome: () -> Unit,
    onWithdraw: () -> Unit,
    onToggleIncluded: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        maxItemsInEachRow = 3
    ) {
        AppTextButton(onClick = onIncome, fillWidth = false) {
            Text("+ Income", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        AppTextButton(onClick = onWithdraw, fillWidth = false) {
            Text(
                text = if (cash.currency == mainCurrency) "Withdraw" else "Withdraw (${mainCurrency}→${cash.currency})",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        AppTextButton(onClick = onToggleIncluded, fillWidth = false) {
            Text(
                text = if (cash.includedInTotal) "Set aside" else "Count in total",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

/** Square badge used in front of an account row. */
@Composable
private fun AccountBadge(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    AppAvatar(text = text, containerColor = containerColor, contentColor = contentColor, size = 30.dp)
}

/** Right-aligned monetary value for a row. */
@Composable
private fun AmountLabel(
    amount: BigDecimal,
    currency: String,
    negativeIsError: Boolean = true
) {
    Text(
        text = CurrencyFormatter.format(amount, currency),
        style = MaterialTheme.typography.titleSmall.merge(com.spendingtracker.app.ui.theme.AppType.tabular),
        fontWeight = FontWeight.SemiBold,
        color = if (negativeIsError && amount < BigDecimal.ZERO) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

/** One-line autopay status for a card row. */
@Composable
private fun autopaySummary(card: Account, balance: BigDecimal, state: HomeUiState): String {
    if (!card.autopayEnabled) return "Credit card · autopay off"
    val today = LocalDate.now()
    val due = AccountBalances.dueDate(card, YearMonth.from(today))
    val attempted = state.cardPayments.any {
        it.cardId == card.id && it.autopayPeriod == YearMonth.from(today).toString()
    }
    val next = if (attempted || due < today) {
        AccountBalances.dueDate(card, YearMonth.from(today).plusMonths(1))
    } else {
        due
    }
    val amount = AccountBalances.paymentAmount(card, balance)
    return "Autopay " + next + " · " + CurrencyFormatter.format(amount, card.currency) +
        " · " + card.autopayMode.replace('_', ' ').lowercase()
}

/**
 * Edit the parts of a balance account that are safe to change: its name, the low
 * balance warning threshold, and (for cards) the credit limit. Opening balances are
 * never rewritten here - they are corrected from the Adjust action so history stays
 * auditable.
 */
@Composable
private fun EditAccountDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(account.id) { mutableStateOf(account.name) }
    var threshold by remember(account.id) { mutableStateOf(account.lowBalanceThreshold.orEmpty()) }
    var limit by remember(account.id) { mutableStateOf(account.creditLimit.orEmpty()) }
    var error by remember(account.id) { mutableStateOf<String?>(null) }
    val isCard = account.type == "CREDIT_CARD"
    val isCash = account.type == "CASH"
    var includedInTotal by remember(account.id) { mutableStateOf(account.includedInTotal) }
    val typeLabel = account.type.replace('_', ' ').lowercase()
    // Explicit type: a bare lambda in an if-expression would not be inferred as
    // @Composable and the delete action would not compile.
    val deleteAction: (@Composable () -> Unit)? = {
        AppTextButton(onClick = onDelete) {
            Text(
                "Delete",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Edit ${account.name}",
        confirmLabel = "Save",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            val parsedThreshold = threshold.trim().replace(',', '.').ifBlank { null }
            val parsedLimit = limit.trim().replace(',', '.').ifBlank { null }
            when {
                name.isBlank() -> error = "Enter a name"
                parsedThreshold != null && parsedThreshold.toBigDecimalOrNull() == null ->
                    error = "Low balance threshold must be a number"
                parsedLimit != null && parsedLimit.toBigDecimalOrNull() == null ->
                    error = "Credit limit must be a number"
                else -> onSave(
                    account.copy(
                        name = name.trim(),
                        lowBalanceThreshold = parsedThreshold,
                        creditLimit = if (isCard) parsedLimit else account.creditLimit,
                        includedInTotal = if (isCash) includedInTotal else account.includedInTotal
                    )
                )
            }
        },
        extraAction = if (account.type != "MAIN") deleteAction else null
    ) {
        Text(
            text = "$typeLabel · ${account.currency} · opening " +
                CurrencyFormatter.format(account.startingBalanceAsBigDecimal(), account.currency),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = "Name"
        )
        AppTextField(
            value = threshold,
            onValueChange = { threshold = it; error = null },
            label = "Low balance warning (optional)",
            keyboardType = KeyboardType.Decimal
        )
        if (isCard) {
            AppTextField(
                value = limit,
                onValueChange = { limit = it; error = null },
                label = "Credit limit (optional)",
                keyboardType = KeyboardType.Decimal
            )
        }
        if (isCash) {
            // A cash balance can be parked outside the totals (e.g. savings cash).
            AppSelectorRow(
                label = "Count in total available",
                value = if (includedInTotal) "Yes" else "No - set aside",
                onClick = { includedInTotal = !includedInTotal }
            )
            Text(
                text = "Currency ${account.currency} is fixed after creation; money withdrawn " +
                    "into this balance is converted automatically.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
        Text(
            text = if (account.type == "MAIN") {
                "Opening balances are corrected with Adjust on the balance card, so history stays auditable."
            } else {
                "Deleting this balance also removes its expenses, income and payments."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
        if (error != null) {
            Text(
                text = error!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CreateAccountDialog(
    type: String,
    mainCurrency: String,
    orderedCurrencies: List<CurrencyProvider.CurrencyInfo> = CurrencyProvider.allCurrencies,
    onDismiss: () -> Unit,
    onCreate: (name: String, opening: String, currency: String) -> Unit
) {
    var name by remember(type) { mutableStateOf(if (type == "CASH") "Cash" else "Credit card") }
    var opening by remember(type) { mutableStateOf("0") }
    var currency by remember(type, mainCurrency) { mutableStateOf(mainCurrency) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = if (type == "CREDIT_CARD") "Add credit card" else "Add cash balance",
        confirmLabel = "Create",
        confirmEnabled = name.isNotBlank(),
        onConfirm = {
            val value = opening.parseAmountOrNull()
            // A card opening balance is signed (debt is negative); cash cannot be negative.
            val invalid = name.isBlank() || value == null ||
                (type != "CREDIT_CARD" && value < BigDecimal.ZERO)
            if (invalid) {
                error = if (type == "CREDIT_CARD") "Enter a name and an amount (negative = debt)"
                else "Enter a name and a non-negative amount"
            } else {
                onCreate(name, value!!.toPlainString(), currency)
            }
        }
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it; error = null },
            label = "Name",
            focusRequester = focus
        )
        if (type == "CASH") {
            // Cash may hold a different currency from main; withdrawals convert into it.
            AppSelectorRow(
                label = "Currency",
                value = if (currency == mainCurrency) "$currency (main)" else currency,
                onClick = { showCurrencyPicker = true }
            )
        }
        AppTextField(
            value = opening,
            onValueChange = { opening = it; error = null },
            label = when (type) {
                "CREDIT_CARD" -> "Opening debt"
                else -> "Opening cash (optional, $currency)"
            },
            keyboardType = KeyboardType.Decimal,
            allowNegative = type == "CREDIT_CARD",
            isError = error != null,
            supportingText = error
        )
        Text(
            text = if (type == "CASH" && currency != mainCurrency) {
                "Opening amounts do not withdraw money from main/debit. Later withdrawals from " +
                    "main into this balance are converted into $currency."
            } else {
                "Opening amounts do not withdraw money from main/debit."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            orderedCurrencies = orderedCurrencies,
            balanceCurrency = currency,
            onDismiss = { showCurrencyPicker = false },
            onCurrencySelected = { code ->
                currency = code
                showCurrencyPicker = false
            }
        )
    }
}

@Composable
private fun MoneyDialog(
    title: String,
    description: String,
    showOverpayment: Boolean,
    onDismiss: () -> Unit,
    amountLabel: String = "Amount",
    save: (String, Boolean) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var overpayment by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current
    val valid = amount.parseAmountOrNull()?.let { it > BigDecimal.ZERO } == true

    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = "Confirm",
        confirmEnabled = valid,
        onConfirm = { save(amount.trim().replace(',', '.'), overpayment) }
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppTextField(
            value = amount,
            onValueChange = { amount = it },
            label = amountLabel,
            keyboardType = KeyboardType.Decimal,
            focusRequester = focus
        )
        if (showOverpayment) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = overpayment, onCheckedChange = { overpayment = it })
                Text(
                    text = "Explicitly allow overpayment",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun AutopayDialog(
    card: Account,
    onDismiss: () -> Unit,
    save: (Account) -> Unit
) {
    var enabled by remember { mutableStateOf(card.autopayEnabled) }
    var day by remember { mutableStateOf(card.autopayDay.toString()) }
    var mode by remember { mutableStateOf(card.autopayMode) }
    var fixed by remember { mutableStateOf(card.autopayFixedAmount ?: "") }
    val valid = (day.toIntOrNull() ?: 0) in 1..31 &&
        (mode != "FIXED_AMOUNT" || fixed.parseAmountOrNull()?.let { it > BigDecimal.ZERO } == true)

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Autopay · ${card.name}",
        confirmLabel = "Save",
        confirmEnabled = valid,
        onConfirm = {
            save(
                card.copy(
                    autopayEnabled = enabled,
                    autopayDay = day.toInt(),
                    autopayMode = mode,
                    autopayFixedAmount = fixed.trim().replace(',', '.').ifBlank { null }
                )
            )
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Switch(checked = enabled, onCheckedChange = { enabled = it })
            Spacer(Modifier.width(6.dp))
            Text("Enable autopay", style = MaterialTheme.typography.bodySmall)
        }
        AppTextField(
            value = day,
            onValueChange = { day = it },
            label = "Due day (1–31)",
            keyboardType = KeyboardType.Number
        )
        Text(
            text = "Days 29–31 clamp to the last day in shorter months. Android may delay background work; due payments also run when Home opens. These are local ledger payments, not bank transfers.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        listOf("FULL_BALANCE", "FIXED_AMOUNT").forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { mode = option }
            ) {
                RadioButton(selected = mode == option, onClick = { mode = option })
                Text(
                    text = option.replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (mode == "FIXED_AMOUNT") {
            AppTextField(
                value = fixed,
                onValueChange = { fixed = it },
                label = "Fixed amount (capped at debt)",
                keyboardType = KeyboardType.Decimal
            )
        }
    }
}
