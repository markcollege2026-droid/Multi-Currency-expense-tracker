package com.spendingtracker.app.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.spendingtracker.app.domain.TotalEffect
import com.spendingtracker.app.domain.TransferAmounts
import com.spendingtracker.app.domain.TransferMath
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppPill
import com.spendingtracker.app.ui.components.AppSelectorRow
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
import com.spendingtracker.app.util.parseAmountOrNull

/** Which side of the transfer the account picker is choosing. */
enum class TransferPickerSide { FROM, TO }

/** Validated state of the transfer form, computed without a window so it can be tested. */
data class TransferFormState(
    val typed: BigDecimal?,
    /** Amount leaving the source, in the source currency. */
    val amountFrom: BigDecimal?,
    /** Amount arriving on the target, in the target currency. */
    val amountTo: BigDecimal?,
    val error: String?,
    val canConfirm: Boolean
)

/**
 * Validates a transfer between two balances. The typed side is kept exact and
 * only the derived side is rounded; the source balance check always applies to
 * the amount that will actually leave the source, in its own currency.
 */
fun transferFormState(
    amount: String,
    sourceBalance: BigDecimal,
    rate: BigDecimal,
    typedInTargetCurrency: Boolean,
    hasPair: Boolean
): TransferFormState {
    val typed = amount.parseAmountOrNull()?.takeIf { it > BigDecimal.ZERO }
    if (typed == null) {
        val error = if (amount.isNotBlank()) "Enter a positive amount" else null
        return TransferFormState(null, null, null, error, false)
    }
    val amounts: TransferAmounts =
        TransferMath.amounts(typed, rate, typedInTargetCurrency = typedInTargetCurrency)
    val error = when {
        amounts.from <= BigDecimal.ZERO -> "Amount is too small after conversion"
        amounts.to <= BigDecimal.ZERO -> "Amount is too small after conversion"
        amounts.from > sourceBalance -> "More than the source balance holds"
        else -> null
    }
    return TransferFormState(
        typed = typed,
        amountFrom = amounts.from,
        amountTo = amounts.to,
        error = error,
        canConfirm = hasPair && error == null
    )
}

/**
 * Human-readable effect of a transfer on "total available". Both balances always
 * move by the transferred amount; the total only changes when money crosses
 * between a counted balance and one that is set aside.
 */
fun transferTotalEffectText(from: Account, to: Account): String? {
    val fromCounts = AccountBalances.countsTowardTotal(from)
    val toCounts = AccountBalances.countsTowardTotal(to)
    return when (TransferMath.totalEffect(fromCounts, toCounts)) {
        TotalEffect.DECREASES ->
            "${to.name} is set aside (not counted in total available), so the total falls " +
                "by the transferred amount. Both balances stay exact."
        TotalEffect.INCREASES ->
            "${from.name} is set aside, so bringing money back raises total available " +
                "by the transferred amount. Both balances stay exact."
        TotalEffect.UNCHANGED ->
            if (!fromCounts && !toCounts) {
                "Both balances are set aside - total available does not change."
            } else {
                null
            }
    }
}

/** One-line label for a transferable balance, including where it stands right now. */
fun transferAccountLabel(account: Account, balance: BigDecimal): String =
    account.name + " (" + account.currency + ") · " +
        CurrencyFormatter.formatSimple(balance, account.currency) +
        if (account.type == "CASH" && !account.includedInTotal) " · set aside" else ""

/**
 * Move money between any two balances of the active main account: main to cash,
 * cash to main, or cash to cash. Stored as one transfer row covering both sides,
 * so a balance set aside from the totals still stays exact.
 */
@Composable
fun TransferDialog(
    accounts: List<Account>,
    balances: Map<Long, BigDecimal>,
    rateLookup: suspend (fromCurrency: String, toCurrency: String) -> BigDecimal,
    onConfirm: (fromId: Long, toId: Long, amountFrom: String, amountTo: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
    onCreateCash: (() -> Unit)? = null,
    initialFromId: Long? = null,
    initialToId: Long? = null
) {
    val defaultFrom = initialFromId?.let { id -> accounts.firstOrNull { it.id == id }?.id }
        ?: accounts.firstOrNull { it.type == "MAIN" }?.id
        ?: accounts.firstOrNull()?.id
    var fromId by remember(accounts) { mutableStateOf(defaultFrom) }
    var toId by remember(accounts) {
        mutableStateOf(
            initialToId?.let { id -> accounts.firstOrNull { it.id == id && it.id != defaultFrom }?.id }
                ?: accounts.firstOrNull { it.id != defaultFrom }?.id
        )
    }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(BigDecimal.ONE) }
    var rateReady by remember { mutableStateOf(false) }
    var typedInTarget by remember { mutableStateOf(false) }
    var pickerSide by remember { mutableStateOf<TransferPickerSide?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val from = accounts.firstOrNull { it.id == fromId }
    val to = accounts.firstOrNull { it.id == toId }
    val hasPair = from != null && to != null && from.id != to.id
    val sourceBalance = from?.let { balances[it.id] } ?: BigDecimal.ZERO
    val form = transferFormState(amount, sourceBalance, rate, typedInTarget, hasPair)
    val needsCash = accounts.size < 2

    LaunchedEffect(Unit) {
        if (!needsCash) runCatching { focus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }
    LaunchedEffect(fromId, toId) {
        val source = from?.currency ?: return@LaunchedEffect
        val target = to?.currency ?: return@LaunchedEffect
        if (source == target) {
            rate = BigDecimal.ONE
            rateReady = true
            return@LaunchedEffect
        }
        rateReady = false
        rate = try { rateLookup(source, target) } catch (e: Exception) { BigDecimal.ONE }
        rateReady = true
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Transfer between balances",
        confirmLabel = if (needsCash && onCreateCash != null) "Add cash balance" else "Transfer",
        confirmEnabled = if (needsCash) onCreateCash != null else form.canConfirm,
        onConfirm = {
            if (needsCash) {
                onCreateCash?.invoke()
            } else {
                val fromAmount = form.amountFrom
                val toAmount = form.amountTo
                val source = from
                val target = to
                if (fromAmount != null && toAmount != null && source != null && target != null) {
                    onConfirm(
                        source.id,
                        target.id,
                        fromAmount.toPlainString(),
                        toAmount.toPlainString(),
                        note.ifBlank { null }
                    )
                }
            }
        }
    ) {
        if (needsCash) {
            Text(
                text = "A transfer needs two balances. Create a cash balance first, then " +
                    "money can move between main and cash - or from one cash balance to another.",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4
            )
        } else if (from != null && to != null) {
            TransferDialogBody(
                from = from,
                to = to,
                balances = balances,
                amount = amount,
                note = note,
                form = form,
                rate = rate,
                rateReady = rateReady,
                typedInTargetCurrency = typedInTarget,
                focusRequester = focus,
                onPickFrom = { pickerSide = TransferPickerSide.FROM },
                onPickTo = { pickerSide = TransferPickerSide.TO },
                onSwap = {
                    val previousFrom = fromId
                    fromId = toId
                    toId = previousFrom
                    typedInTarget = false
                },
                onAmountChange = { amount = it },
                onNoteChange = { note = it },
                onSideChange = { typedInTarget = it }
            )
        }
    }

    pickerSide?.let { side ->
        val excluded = if (side == TransferPickerSide.FROM) toId else fromId
        TransferAccountPickerDialog(
            accounts = accounts.filter { it.id != excluded },
            balances = balances,
            selectedId = if (side == TransferPickerSide.FROM) fromId else toId,
            title = if (side == TransferPickerSide.FROM) "Transfer from" else "Transfer to",
            onDismiss = { pickerSide = null },
            onSelect = { picked ->
                if (side == TransferPickerSide.FROM) fromId = picked else toId = picked
                pickerSide = null
            }
        )
    }
}

/** Body of the transfer dialog, with state hoisted so tests can render it directly. */
@Composable
fun TransferDialogBody(
    from: Account,
    to: Account,
    balances: Map<Long, BigDecimal>,
    amount: String,
    note: String,
    form: TransferFormState,
    rate: BigDecimal,
    rateReady: Boolean,
    typedInTargetCurrency: Boolean = false,
    focusRequester: FocusRequester? = null,
    onPickFrom: () -> Unit = {},
    onPickTo: () -> Unit = {},
    onSwap: () -> Unit = {},
    onAmountChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onSideChange: (Boolean) -> Unit = {}
) {
    val fromBalance = balances[from.id] ?: BigDecimal.ZERO
    val toBalance = balances[to.id] ?: BigDecimal.ZERO
    val needsConversion = from.currency != to.currency
    val typedCurrency = if (typedInTargetCurrency) to.currency else from.currency

    Text(
        text = "FROM",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AppSelectorRow(
        label = "Source balance",
        value = transferAccountLabel(from, fromBalance),
        onClick = onPickFrom
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        AppPill(
            text = "Swap ↓↑",
            onClick = onSwap,
            contentDescription = "Swap source and destination"
        )
    }
    Text(
        text = "TO",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    AppSelectorRow(
        label = "Destination balance",
        value = transferAccountLabel(to, toBalance),
        onClick = onPickTo
    )

    transferTotalEffectText(from, to)?.let { effect ->
        Text(
            text = effect,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 3
        )
    }

    if (needsConversion) {
        Text(
            text = "AMOUNT IN",
            style = AppType.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppFilterChip(
                selected = !typedInTargetCurrency,
                onClick = { onSideChange(false) },
                label = from.currency
            )
            AppFilterChip(
                selected = typedInTargetCurrency,
                onClick = { onSideChange(true) },
                label = to.currency
            )
        }
    }

    AppTextField(
        value = amount,
        onValueChange = onAmountChange,
        label = "Amount ($typedCurrency)",
        keyboardType = KeyboardType.Decimal,
        isError = form.error != null,
        supportingText = form.error,
        focusRequester = focusRequester
    )

    val fromAmount = form.amountFrom
    val toAmount = form.amountTo
    if (fromAmount != null && toAmount != null && form.error == null) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "-" + CurrencyFormatter.format(fromAmount, from.currency) +
                    " from " + from.name + " → " +
                    CurrencyFormatter.format(fromBalance - fromAmount, from.currency),
                style = MaterialTheme.typography.labelSmall.merge(AppType.tabular),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "+" + CurrencyFormatter.format(toAmount, to.currency) +
                    " to " + to.name + " → " +
                    CurrencyFormatter.format(toBalance + toAmount, to.currency),
                style = MaterialTheme.typography.labelSmall.merge(AppType.tabular),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (needsConversion && rateReady) {
                Text(
                    text = "1 ${from.currency} = ${rate.stripTrailingZeros().toPlainString()} ${to.currency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }

    AppTextField(
        value = note,
        onValueChange = onNoteChange,
        label = "Note (optional)"
    )

    AppHairline()
    Text(
        text = "One transfer moves both balances at once. It is not spending or " +
            "income, so category and deposit totals are unchanged.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3
    )
}

/** Picks one side of a transfer, showing every candidate with its live balance. */
@Composable
fun TransferAccountPickerDialog(
    accounts: List<Account>,
    balances: Map<Long, BigDecimal>,
    selectedId: Long?,
    title: String,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    var picked by remember(selectedId) { mutableStateOf(selectedId) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = "Use selected",
        confirmEnabled = picked != null,
        onConfirm = { picked?.let(onSelect) }
    ) {
        accounts.forEach { account ->
            val balance = balances[account.id] ?: BigDecimal.ZERO
            AppSelectorRow(
                label = if (account.type == "MAIN") "Main balance" else "Cash balance",
                value = transferAccountLabel(account, balance) +
                    if (picked == account.id) " · selected" else "",
                onClick = { picked = account.id }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Set-aside balances can send and receive transfers; only the total changes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
