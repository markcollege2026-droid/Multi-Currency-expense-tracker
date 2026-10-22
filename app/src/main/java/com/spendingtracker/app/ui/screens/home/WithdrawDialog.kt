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
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal

/**
 * What the withdraw form currently allows. Pure data so the rules can be tested
 * without a Compose window (the layout is covered separately).
 */
data class WithdrawFormState(
    val parsed: BigDecimal?,
    val error: String?,
    val canConfirm: Boolean
)

/** Validation for "move [amount] out of a balance holding [mainBalance]". */
fun withdrawFormState(
    amount: String,
    mainBalance: BigDecimal,
    hasTarget: Boolean
): WithdrawFormState {
    val parsed = amount.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    val error = when {
        amount.isNotBlank() && parsed == null -> "Enter a positive amount"
        parsed != null && parsed > mainBalance -> "More than the main balance holds"
        else -> null
    }
    return WithdrawFormState(parsed, error, hasTarget && parsed != null && error == null)
}

/** The confirm verb doubles as the way forward when no cash balance exists yet. */
fun withdrawConfirmLabel(cashCount: Int): String =
    if (cashCount == 0) "Add cash balance" else "Withdraw"

fun withdrawConfirmEnabled(cashCount: Int, hasCreatePath: Boolean, form: WithdrawFormState): Boolean =
    if (cashCount == 0) hasCreatePath else form.canConfirm

/**
 * Move money from the main balance into one of its cash accounts.
 *
 * When the cash account uses a different currency the conversion is previewed
 * live with the cached/fallback rate and stored on the withdrawal, so each
 * balance keeps its own currency.
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
    onConfirm: (cashId: Long, amount: String, note: String?) -> Unit,
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
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val target = cashAccounts.firstOrNull { it.id == targetId }
    val form = withdrawFormState(amount, mainBalance, target != null)
    val noCashYet = cashAccounts.isEmpty()

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
                val value = form.parsed
                val cash = target
                if (value != null && cash != null) onConfirm(cash.id, value.toPlainString(), note.ifBlank { null })
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
            focusRequester = focus,
            onTargetChange = { targetId = it },
            onAmountChange = { amount = it },
            onNoteChange = { note = it }
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
    focusRequester: FocusRequester? = null,
    onTargetChange: (Long) -> Unit = {},
    onAmountChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {}
) {
    val compact = LocalCompactUi.current
    val target = cashAccounts.firstOrNull { it.id == targetId }
    val needsConversion = target != null && target.currency != mainCurrency

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

    AppTextField(
        value = amount,
        onValueChange = onAmountChange,
        label = "Amount ($mainCurrency)",
        keyboardType = KeyboardType.Decimal,
        isError = form.error != null,
        supportingText = form.error,
        focusRequester = focusRequester
    )

    if (target != null && needsConversion && form.parsed != null && rateReady) {
        val converted = form.parsed.multiply(rate)
        Text(
            text = "≈ " + CurrencyFormatter.format(converted, target.currency) +
                " · 1 $mainCurrency = ${rate.stripTrailingZeros().toPlainString()} ${target.currency}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2
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
        } else {
            "Moves money from main into this cash balance. Total available stays the same."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 4
    )
}
