package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.domain.TransferAmounts
import com.spendingtracker.app.domain.TransferMath
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal

/** Which currency the typed withdrawal amount is expressed in. */
enum class AmountSide { MAIN, CASH }

/**
 * Everything the withdraw form needs once the amount has been typed, computed
 * without a Compose window so the rules can be tested directly.
 */
data class WithdrawFormState(
    val typed: BigDecimal?,
    /** Amount leaving main, in the main currency. */
    val amountFrom: BigDecimal?,
    /** Amount arriving on cash, in the cash currency. */
    val amountTo: BigDecimal?,
    val error: String?,
    val canConfirm: Boolean
)

/**
 * Validates a withdrawal.
 *
 * [inCashCurrency] lets the user type the amount in the cash account's currency;
 * the main side is then derived from the rate, and the main balance check still
 * applies to the derived side so a withdrawal can never overdraw main.
 */
fun withdrawFormState(
    amount: String,
    mainBalance: BigDecimal,
    rate: BigDecimal,
    inCashCurrency: Boolean,
    hasTarget: Boolean
): WithdrawFormState {
    val typed = amount.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    if (typed == null) {
        val error = if (amount.isNotBlank()) "Enter a positive amount" else null
        return WithdrawFormState(null, null, null, error, false)
    }
    val amounts: TransferAmounts = TransferMath.amounts(typed, rate, typedInTargetCurrency = inCashCurrency)
    val error = when {
        amounts.from <= BigDecimal.ZERO -> "Amount is too small after conversion"
        amounts.to <= BigDecimal.ZERO -> "Amount is too small after conversion"
        amounts.from > mainBalance -> "More than the main balance holds"
        else -> null
    }
    return WithdrawFormState(
        typed = typed,
        amountFrom = amounts.from,
        amountTo = amounts.to,
        error = error,
        canConfirm = hasTarget && error == null
    )
}

/** The confirm verb doubles as the way forward when no cash balance exists yet. */
fun withdrawConfirmLabel(cashCount: Int): String =
    if (cashCount == 0) "Add cash balance" else "Withdraw"

fun withdrawConfirmEnabled(cashCount: Int, hasCreatePath: Boolean, form: WithdrawFormState): Boolean =
    if (cashCount == 0) hasCreatePath else form.canConfirm

/**
 * Move money from the main balance into one of its cash accounts.
 *
 * When the cash account uses a different currency the amount can be typed in
 * either currency and the other side is previewed live with the cached rate, so
 * a user who knows they took out €100 does not have to convert in their head.
 *
 * The dialog always offers a way forward: with no cash balance yet the confirm
 * action becomes "Add cash balance" - there is never a dialog that only offers
 * Cancel.
 */
@Composable
fun WithdrawDialog(
    cashAccounts: List<Account>,
    mainCurrency: String,
    mainBalance: BigDecimal,
    rateLookup: suspend (fromCurrency: String, toCurrency: String) -> BigDecimal,
    onConfirm: (cashId: Long, amountFrom: String, amountTo: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
    onCreateCash: (() -> Unit)? = null,
    initialCashId: Long? = null
) {
    var targetId by remember(cashAccounts) {
        mutableStateOf(
            initialCashId?.let { id -> cashAccounts.firstOrNull { it.id == id }?.id }
                ?: cashAccounts.firstOrNull()?.id
        )
    }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(BigDecimal.ONE) }
    var rateReady by remember { mutableStateOf(false) }
    var side by remember { mutableStateOf(AmountSide.MAIN) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val target = cashAccounts.firstOrNull { it.id == targetId }
    val noCashYet = cashAccounts.isEmpty()
    val form = withdrawFormState(amount, mainBalance, rate, side == AmountSide.CASH, target != null)

    LaunchedEffect(Unit) {
        // Focus is a convenience: a dialog window may not be attached on the
        // first frame, and asking too early throws.
        if (!noCashYet) runCatching { focus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }
    // Rates are fetched once per destination currency, never per keystroke.
    LaunchedEffect(targetId) {
        val to = target?.currency ?: return@LaunchedEffect
        rateReady = false
        rate = try { rateLookup(mainCurrency, to) } catch (e: Exception) { BigDecimal.ONE }
        rateReady = true
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Withdraw to cash",
        confirmLabel = withdrawConfirmLabel(cashAccounts.size),
        confirmEnabled = withdrawConfirmEnabled(cashAccounts.size, onCreateCash != null, form),
        onConfirm = {
            if (noCashYet) {
                onCreateCash?.invoke()
            } else {
                val from = form.amountFrom
                val to = form.amountTo
                val cash = target
                if (from != null && to != null && cash != null) {
                    onConfirm(cash.id, from.toPlainString(), to.toPlainString(), note.ifBlank { null })
                }
            }
        }
    ) {
        WithdrawDialogBody(
            cashAccounts = cashAccounts,
            mainCurrency = mainCurrency,
            mainBalance = mainBalance,
            targetId = targetId,
            amount = amount,
            note = note,
            form = form,
            rate = rate,
            rateReady = rateReady,
            side = side,
            focusRequester = focus,
            onTargetChange = { targetId = it },
            onAmountChange = { amount = it },
            onNoteChange = { note = it },
            onSideChange = { side = it }
        )
    }
}

/**
 * Body of the withdraw dialog. State is hoisted so the content can be rendered
 * and asserted without a Dialog window.
 */
@Composable
fun WithdrawDialogBody(
    cashAccounts: List<Account>,
    mainCurrency: String,
    mainBalance: BigDecimal,
    targetId: Long?,
    amount: String,
    note: String,
    form: WithdrawFormState,
    rate: BigDecimal,
    rateReady: Boolean,
    side: AmountSide = AmountSide.MAIN,
    focusRequester: FocusRequester? = null,
    onTargetChange: (Long) -> Unit = {},
    onAmountChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onSideChange: (AmountSide) -> Unit = {}
) {
    val compact = LocalCompactUi.current
    val target = cashAccounts.firstOrNull { it.id == targetId }
    val cashCurrency = target?.currency
    val needsConversion = cashCurrency != null && cashCurrency != mainCurrency
    val typedCurrency = if (side == AmountSide.CASH) cashCurrency ?: mainCurrency else mainCurrency

    Text(
        text = "MAIN / DEBIT",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = CurrencyFormatter.format(mainBalance, mainCurrency) + " available",
        style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    AppHairline()

    if (cashAccounts.isEmpty()) {
        Text(
            text = "No cash balance yet. Create one and money withdrawn from main " +
                "lands on it - it can even use a different currency.",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 4
        )
        return
    }

    Text(
        text = "TO",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (cashAccounts.size == 1) {
        val only = cashAccounts.first()
        Text(
            text = "${only.name} · ${only.currency}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        // Several cash balances: pick the destination explicitly.
        cashAccounts.forEach { cash ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compact) 0.dp else 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = cash.id == targetId,
                    onClick = { onTargetChange(cash.id) },
                    modifier = Modifier.size(width = 30.dp, height = 30.dp)
                )
                Text(
                    text = "${cash.name} (${cash.currency})",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (needsConversion && cashCurrency != null) {
        // Type the amount in whichever currency you know it in.
        Text(
            text = "AMOUNT IN",
            style = AppType.eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppFilterChip(
                selected = side == AmountSide.MAIN,
                onClick = { onSideChange(AmountSide.MAIN) },
                label = mainCurrency
            )
            AppFilterChip(
                selected = side == AmountSide.CASH,
                onClick = { onSideChange(AmountSide.CASH) },
                label = cashCurrency
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

    if (needsConversion && cashCurrency != null && form.typed != null && rateReady) {
        val other = if (side == AmountSide.CASH) form.amountFrom else form.amountTo
        val otherCurrency = if (side == AmountSide.CASH) mainCurrency else cashCurrency
        if (other != null) {
            Text(
                text = "≈ " + CurrencyFormatter.format(other, otherCurrency) +
                    if (side == AmountSide.CASH) " leaves main" else " arrives on cash",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2
            )
        }
        Text(
            text = "1 $mainCurrency = ${rate.stripTrailingZeros().toPlainString()} $cashCurrency",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }

    AppTextField(
        value = note,
        onValueChange = onNoteChange,
        label = "Note (optional)"
    )

    Text(
        text = if (needsConversion) {
            "Converted at the cached rate when you confirm. This is a transfer, " +
                "not spending, so category totals are unchanged."
        } else if (target != null && !target.includedInTotal) {
            // The cash side is exact either way; only the counted total drops.
            "Moves money from main into this set-aside cash balance, so total " +
                "available falls by the amount while both balances stay exact."
        } else {
            "Moves money from main into this cash balance. Total available stays the same."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 4
    )
}
