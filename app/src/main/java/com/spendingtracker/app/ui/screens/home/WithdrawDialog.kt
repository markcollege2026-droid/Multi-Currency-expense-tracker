package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal

/**
 * Move money from the main balance into one of its cash accounts. When the two
 * accounts use different currencies the conversion is previewed live using the
 * cached/fallback rate from [rateLookup] and stored with the withdrawal.
 */
@Composable
fun WithdrawDialog(
    cashAccounts: List<Account>,
    mainCurrency: String,
    mainBalance: BigDecimal,
    rateLookup: suspend (fromCurrency: String, toCurrency: String) -> BigDecimal,
    onConfirm: (cashId: Long, amount: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
    initialCashId: Long? = null
) {
    var targetId by remember(cashAccounts) {
        mutableStateOf(initialCashId?.let { id -> cashAccounts.firstOrNull { it.id == id }?.id }
            ?: cashAccounts.firstOrNull()?.id)
    }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(BigDecimal.ONE) }
    var rateReady by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val target = cashAccounts.firstOrNull { it.id == targetId }
    val parsed = amount.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    val amountError = when {
        amount.isNotBlank() && parsed == null -> "Enter a positive amount"
        parsed != null && parsed > mainBalance -> "Exceeds available main balance"
        else -> null
    }
    val converted = if (parsed != null && target != null) parsed.multiply(rate) else null

    LaunchedEffect(Unit) {
        // The amount field only exists once a cash account is available.
        if (cashAccounts.isNotEmpty()) focus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }
    LaunchedEffect(target?.currency) {
        val to = target?.currency ?: return@LaunchedEffect
        rateReady = false
        rate = try { rateLookup(mainCurrency, to) } catch (e: Exception) { BigDecimal.ONE }
        rateReady = true
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Withdraw to cash",
        confirmLabel = "Withdraw",
        confirmEnabled = cashAccounts.isNotEmpty() && target != null && parsed != null && amountError == null,
        onConfirm = {
            val value = parsed
            val cash = target
            if (value != null && cash != null) onConfirm(cash.id, value.toPlainString(), note.ifBlank { null })
        }
    ) {
        Text(
            text = "Available: " + CurrencyFormatter.format(mainBalance, mainCurrency),
            style = MaterialTheme.typography.labelMedium
        )

        if (cashAccounts.isEmpty()) {
            Text(
                text = "No cash account yet. Home → accounts → \"Set up cash\", then withdraw here.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (cashAccounts.size > 1) {
                Text(
                    text = "To",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                cashAccounts.forEach { cash ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { targetId = cash.id }
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = cash.id == targetId,
                            onClick = { targetId = cash.id }
                        )
                        Text(
                            text = "${cash.name} (${cash.currency})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Text(
                    text = "To: " + cashAccounts.first().name + " (" + cashAccounts.first().currency + ")",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

            if (target != null && target.currency != mainCurrency && parsed != null && rateReady) {
                Text(
                    text = (if (converted != null) "≈ " + CurrencyFormatter.format(converted, target.currency) else "") +
                        " · rate 1 $mainCurrency = " + rate.stripTrailingZeros().toPlainString() + " ${target.currency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AppTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note (optional)",
                imeAction = androidx.compose.ui.text.input.ImeAction.Next
            )

            Text(
                text = if (target != null && target.currency != mainCurrency)
                    "Converted at the cached rate at the moment you confirm."
                else
                    "Moves money from main into this cash balance; totals stay the same.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}
