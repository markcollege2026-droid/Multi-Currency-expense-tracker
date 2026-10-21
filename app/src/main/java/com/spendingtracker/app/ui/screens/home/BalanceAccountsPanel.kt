package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun BalanceAccountsPanel(state: HomeUiState, viewModel: HomeViewModel) {
    var createType by remember { mutableStateOf<String?>(null) }
    var paymentCard by remember { mutableStateOf<Account?>(null) }
    var configureCard by remember { mutableStateOf<Account?>(null) }
    var cashAccount by remember { mutableStateOf<Account?>(null) }
    var withdrawTarget by remember { mutableStateOf<Account?>(null) }
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current
    // On the small screen the detailed account controls start collapsed; the
    // summary (total available) is always visible and one D-pad press away.
    var expanded by rememberSaveable { mutableStateOf(!compact) }

    val cashAccounts = state.balanceAccounts.filter { it.type == "CASH" }
    val cards = state.balanceAccounts.filter { it.type == "CREDIT_CARD" }
    val cardTotal = cards.fold(BigDecimal.ZERO) { sum, card ->
        sum + (state.accountBalances[card.id] ?: BigDecimal.ZERO)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(dims.cardPadding)) {
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
                        text = "Total available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(state.totalAvailable, state.balanceCurrency),
                        style = MaterialTheme.typography.titleSmall,
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

            AppHairline()
            AppValueRow("Main/debit", CurrencyFormatter.format(state.balance, state.balanceCurrency))
            cashAccounts.forEach { cash ->
                val cashBalance = state.accountBalances[cash.id] ?: BigDecimal.ZERO
                AppHairline()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cash.name + if (cash.currency != state.balanceCurrency) " (${cash.currency})" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyFormatter.format(cashBalance, cash.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                if (!cash.includedInTotal) {
                    Text(
                        text = "Set aside — excluded from totals",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppTextButton(onClick = { cashAccount = cash }, modifier = Modifier.weight(1f)) {
                        Text("+ Income", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    AppTextButton(onClick = { withdrawTarget = cash }, modifier = Modifier.weight(1f)) {
                        Text("Withdraw", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    AppTextButton(
                        onClick = { viewModel.setCashIncluded(cash, !cash.includedInTotal) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (cash.includedInTotal) "Set aside" else "Include",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AppTextButton(onClick = { createType = "CASH" }, modifier = Modifier.weight(1f)) {
                    Text("+ Cash account", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                AppTextButton(onClick = { createType = "CREDIT_CARD" }, modifier = Modifier.weight(1f)) {
                    Text("+ Credit card", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }

            cards.forEach { card ->
                val balance = state.accountBalances[card.id] ?: BigDecimal.ZERO
                AppHairline()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyFormatter.format(balance, card.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (balance < BigDecimal.ZERO) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                if (card.autopayEnabled) {
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
                    Text(
                        text = "Autopay $next · ${CurrencyFormatter.format(amount, card.currency)} · ${card.autopayMode.replace('_', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (amount > state.balance) {
                        Text(
                            text = "Main funds are too low for autopay",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = "Autopay disabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Autopay settings", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            AppHairline()
            Text(
                text = "Total available",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyFormatter.format(state.totalAvailable, state.balanceCurrency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "Main + cash − card debt (includes card credit)",
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
            defaultCurrency = state.balanceCurrency,
            onDismiss = { createType = null },
            onCreate = { name, opening, currency ->
                viewModel.createBalanceAccount(name, type, opening, currency)
                createType = null
            }
        )
    }
    cashAccount?.let { cash ->
        MoneyDialog(
            title = "Add cash income",
            description = "Adds external cash, not a withdrawal from main",
            showOverpayment = false,
            amountLabel = "Amount (${cash.currency})",
            onDismiss = { cashAccount = null }
        ) { amount, _ ->
            viewModel.fundCash(cash.id, amount)
            cashAccount = null
        }
    }
    withdrawTarget?.let { cash ->
        WithdrawDialog(
            cashAccounts = cashAccounts,
            initialCashId = cash.id,
            mainCurrency = state.balanceCurrency,
            mainBalance = state.balance,
            rateLookup = viewModel::conversionRate,
            onConfirm = { cashId, amount, note ->
                viewModel.withdraw(cashId, amount, note)
                withdrawTarget = null
            },
            onDismiss = { withdrawTarget = null }
        )
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
}

@Composable
private fun CreateAccountDialog(
    type: String,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember(type) { mutableStateOf(if (type == "CASH") "Cash" else "Credit card") }
    var opening by remember(type) { mutableStateOf("0") }
    var currency by remember(type) { mutableStateOf(defaultCurrency) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Add ${type.replace('_', ' ').lowercase()}",
        confirmLabel = "Create",
        onConfirm = {
            val value = opening.trim().replace(',', '.').toBigDecimalOrNull()
            if (name.isBlank() || value == null || value < BigDecimal.ZERO) {
                error = "Enter a name and non-negative amount"
            } else {
                onCreate(name, value.toPlainString(), currency)
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
            AppSelectorRow(
                label = "Currency",
                value = currency,
                onClick = { showCurrencyPicker = true }
            )
        }
        AppTextField(
            value = opening,
            onValueChange = { opening = it; error = null },
            label = if (type == "CREDIT_CARD") "Opening debt" else "Opening cash (${if (type == "CASH") currency else defaultCurrency})",
            keyboardType = KeyboardType.Decimal,
            isError = error != null,
            supportingText = error
        )
        Text(
            text = if (type == "CASH") {
                "Cash may use a different currency — withdrawals convert automatically. Opening amounts do not withdraw money from main/debit."
            } else {
                "Opening amounts do not withdraw money from main/debit."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4
        )
    }
    if (showCurrencyPicker) {
        CurrencyPickerDialog(
            orderedCurrencies = CurrencyProvider.allCurrencies,
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
    val valid = amount.trim().replace(',', '.').toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    LaunchedEffect(Unit) {
        focus.requestFocus()
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
        (mode != "FIXED_AMOUNT" || fixed.trim().replace(',', '.').toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true)

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
