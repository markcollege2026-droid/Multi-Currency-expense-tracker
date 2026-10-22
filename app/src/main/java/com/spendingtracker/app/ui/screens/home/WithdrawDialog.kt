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
 * Move money from the main balance into one of its cash accounts.
 *
 * When the cash account uses a different currency the conversion is previewed
 * live with the cached/fallback rate and stored on the withdrawal, so each
 * balance keeps its own currency.
 *
 * The dialog always offers a way forward: with no cash balance yet, the confirm
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
    val compact = LocalCompactUi.current

    val target = cashAccounts.firstOrNull { it.id == targetId }
    val parsed = amount.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    val amountError = when {
        amount.isNotBlank() && parsed == null -> "Enter a positive amount"
        parsed != null && parsed > mainBalance -> "More than the main balance holds"
        else -> null
    }
    val needsConversion = target != null && target.currency != mainCurrency
    val noCashYet = cashAccounts.isEmpty()

    LaunchedEffect(Unit) {
        if (!noCashYet) focus.requestFocus()
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
        confirmLabel = if (noCashYet) "Add cash balance" else "Withdraw",
        confirmEnabled = if (noCashYet) onCreateCash != null
        else target != null && parsed != null && amountError == null,
        onConfirm = {
            if (noCashYet) {
                onCreateCash?.invoke()
            } else {
                val value = parsed
                val cash = target
                if (value != null && cash != null) onConfirm(cash.id, value.toPlainString(), note.ifBlank { null })
            }
        }
    ) {
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

        if (noCashYet) {
            AppHairline()
            Text(
                text = "No cash balance yet. Create one and money withdrawn from main " +
                    "lands on it - it can even use a different currency.",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4
            )
        } else {
            AppHairline()
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
                            onClick = { targetId = cash.id },
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
                onValueChange = { amount = it },
                label = "Amount ($mainCurrency)",
                keyboardType = KeyboardType.Decimal,
                isError = amountError != null,
                supportingText = amountError,
                focusRequester = focus
            )

            if (target != null && needsConversion && parsed != null && rateReady) {
                val converted = parsed.multiply(rate)
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
                onValueChange = { note = it },
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
    }
}
