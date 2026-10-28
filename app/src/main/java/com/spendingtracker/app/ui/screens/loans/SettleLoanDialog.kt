package com.spendingtracker.app.ui.screens.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.data.local.entity.LoanDebt
import com.spendingtracker.app.domain.TransferMath
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppSelectorRow
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import com.spendingtracker.app.util.parseAmountOrNull

/** Validated state of the settle form, pure so the rules can be tested directly. */
data class SettlementFormState(
    /** Amount in the loan's currency (zero only when archiving a repaid loan). */
    val amount: BigDecimal?,
    /** Same payment in the linked account's currency; null when external. */
    val accountAmount: BigDecimal?,
    val error: String?,
    val canConfirm: Boolean,
    /** True when confirming closes the loan and moves it to the archive. */
    val settlesFully: Boolean,
    /** The loan was already fully repaid - confirming only archives it. */
    val alreadyRepaid: Boolean
)

/**
 * Settlement rules: the amount is typed in the loan's currency, defaults to the
 * whole remaining balance, and may not exceed it. Paying a debt from a balance
 * also requires that balance to hold the (converted) amount; receiving a loan
 * repayment into a balance has no such limit. With no account chosen - external
 * revenue, the default - no balance is involved at all.
 */
fun settlementFormState(
    amountText: String,
    remaining: BigDecimal,
    isDebt: Boolean,
    loanCurrency: String,
    account: Account?,
    accountBalance: BigDecimal?,
    rate: BigDecimal
): SettlementFormState {
    if (remaining.compareTo(BigDecimal.ZERO) == 0) {
        return SettlementFormState(
            amount = BigDecimal.ZERO,
            accountAmount = null,
            error = null,
            canConfirm = true,
            settlesFully = true,
            alreadyRepaid = true
        )
    }
    val parsed = amountText.parseAmountOrNull()?.takeIf { it > BigDecimal.ZERO }
    if (parsed == null) {
        return SettlementFormState(
            amount = null,
            accountAmount = null,
            error = if (amountText.isNotBlank()) "Enter a positive amount" else null,
            canConfirm = false,
            settlesFully = false,
            alreadyRepaid = false
        )
    }
    if (parsed > remaining) {
        return SettlementFormState(
            amount = parsed,
            accountAmount = null,
            error = "More than the remaining " + CurrencyFormatter.formatSimple(remaining, loanCurrency),
            canConfirm = false,
            settlesFully = false,
            alreadyRepaid = false
        )
    }
    if (account == null) {
        return SettlementFormState(
            amount = parsed,
            accountAmount = null,
            error = null,
            canConfirm = true,
            settlesFully = parsed.compareTo(remaining) == 0,
            alreadyRepaid = false
        )
    }
    val converted = if (account.currency == loanCurrency) {
        parsed
    } else if (rate > BigDecimal.ZERO) {
        parsed.multiply(rate).setScale(TransferMath.SCALE, RoundingMode.HALF_EVEN)
    } else {
        parsed
    }
    val error = when {
        converted <= BigDecimal.ZERO -> "Amount is too small after conversion"
        isDebt && accountBalance != null && converted > accountBalance ->
            "More than " + account.name + " holds (" +
                CurrencyFormatter.formatSimple(accountBalance, account.currency) + ")"
        else -> null
    }
    return SettlementFormState(
        amount = parsed,
        accountAmount = converted,
        error = error,
        canConfirm = error == null,
        settlesFully = parsed.compareTo(remaining) == 0,
        alreadyRepaid = false
    )
}

/** Confirm verb for the settle dialog, from the same state that enables it. */
fun settlementConfirmLabel(isDebt: Boolean, form: SettlementFormState): String = when {
    form.alreadyRepaid -> "Move to archive"
    form.settlesFully -> if (isDebt) "Settle debt" else "Settle loan"
    else -> "Record payment"
}

/** Label of the external (default) dropdown entry. */
fun externalSourceLabel(isDebt: Boolean): String =
    if (isDebt) "External revenue (default)" else "External (default)"

/**
 * Settle a loan or debt: choose how much (the whole remainder by default) and
 * which balance the money moves through. The dropdown defaults to external
 * revenue - the settlement is still recorded as a transaction, it just does not
 * touch a tracked balance.
 */
@Composable
fun SettleLoanDialog(
    loan: LoanDebt,
    remaining: BigDecimal,
    accounts: List<Account>,
    balances: Map<Long, BigDecimal>,
    rateLookup: suspend (fromCurrency: String, toCurrency: String) -> BigDecimal,
    onConfirm: (amountLoanCurrency: String, accountId: Long?, rate: String, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val isDebt = loan.type == LoanDebt.TYPE_DEBT
    var amount by remember(loan.id, remaining) {
        mutableStateOf(remaining.stripTrailingZeros().toPlainString())
    }
    var note by remember(loan.id) { mutableStateOf("") }
    // External revenue is the default source: nothing leaves a balance.
    var accountId by remember(loan.id) { mutableStateOf<Long?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var rate by remember(loan.id) { mutableStateOf(BigDecimal.ONE) }
    var rateReady by remember(loan.id) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val account = accounts.firstOrNull { it.id == accountId }
    val form = settlementFormState(
        amountText = amount,
        remaining = remaining,
        isDebt = isDebt,
        loanCurrency = loan.currencyOriginal,
        account = account,
        accountBalance = account?.let { balances[it.id] } ?: BigDecimal.ZERO,
        rate = rate
    )

    LaunchedEffect(Unit) {
        if (!form.alreadyRepaid) runCatching { focus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }
    LaunchedEffect(accountId) {
        val target = account?.currency
        if (target == null || target == loan.currencyOriginal) {
            rate = BigDecimal.ONE
            rateReady = true
            return@LaunchedEffect
        }
        rateReady = false
        rate = try { rateLookup(loan.currencyOriginal, target) } catch (e: Exception) { BigDecimal.ONE }
        rateReady = true
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = (if (isDebt) "Settle debt · " else "Settle loan · ") + loan.personName,
        confirmLabel = settlementConfirmLabel(isDebt, form),
        confirmEnabled = form.canConfirm,
        onConfirm = {
            val settledAmount = form.amount
            if (settledAmount != null) {
                onConfirm(
                    settledAmount.toPlainString(),
                    account?.id,
                    if (account == null || account.currency == loan.currencyOriginal) "1"
                    else rate.toPlainString(),
                    note.ifBlank { null }
                )
            }
        }
    ) {
        SettleLoanDialogBody(
            loan = loan,
            remaining = remaining,
            accounts = accounts,
            balances = balances,
            amount = amount,
            note = note,
            accountId = accountId,
            dropdownExpanded = dropdownExpanded,
            form = form,
            rate = rate,
            rateReady = rateReady,
            focusRequester = focus,
            onAmountChange = { amount = it },
            onNoteChange = { note = it },
            onDropdownChange = { dropdownExpanded = it },
            onAccountSelected = {
                accountId = it
                dropdownExpanded = false
            }
        )
    }
}

/** Body of the settle dialog with state hoisted, so tests can render it directly. */
@Composable
fun SettleLoanDialogBody(
    loan: LoanDebt,
    remaining: BigDecimal,
    accounts: List<Account>,
    balances: Map<Long, BigDecimal>,
    amount: String,
    note: String,
    accountId: Long?,
    dropdownExpanded: Boolean,
    form: SettlementFormState,
    rate: BigDecimal,
    rateReady: Boolean,
    focusRequester: FocusRequester? = null,
    onAmountChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onDropdownChange: (Boolean) -> Unit = {},
    onAccountSelected: (Long?) -> Unit = {}
) {
    val isDebt = loan.type == LoanDebt.TYPE_DEBT
    val account = accounts.firstOrNull { it.id == accountId }

    Text(
        text = if (isDebt) "YOU STILL OWE" else "STILL OWED TO YOU",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = CurrencyFormatter.format(remaining, loan.currencyOriginal) +
            " of " + CurrencyFormatter.formatSimple(loan.amountAsBigDecimal(), loan.currencyOriginal),
        style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    AppHairline()

    if (form.alreadyRepaid) {
        Text(
            text = "This " + (if (isDebt) "debt" else "loan") + " is already fully repaid. " +
                "Confirming moves it to the settled archive - no money moves.",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3
        )
        return
    }

    AppTextField(
        value = amount,
        onValueChange = onAmountChange,
        label = "Amount (${loan.currencyOriginal})",
        keyboardType = KeyboardType.Decimal,
        isError = form.error != null,
        supportingText = form.error,
        focusRequester = focusRequester
    )
    if (!form.settlesFully && form.amount != null && form.error == null) {
        Text(
            text = "Part payment: " +
                CurrencyFormatter.formatSimple(remaining - form.amount, loan.currencyOriginal) +
                " stays open afterwards.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }

    Text(
        text = if (isDebt) "PAY FROM" else "RECEIVE INTO",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        AppSelectorRow(
            label = if (isDebt) "Source" else "Destination",
            value = account?.let {
                it.name + " · " + CurrencyFormatter.formatSimple(balances[it.id] ?: BigDecimal.ZERO, it.currency)
            } ?: externalSourceLabel(isDebt),
            onClick = { onDropdownChange(true) }
        )
        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { onDropdownChange(false) },
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        externalSourceLabel(isDebt),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    if (accountId == null) Text("✓", color = MaterialTheme.colorScheme.primary)
                },
                onClick = { onAccountSelected(null) }
            )
            accounts.forEach { candidate ->
                val balance = balances[candidate.id] ?: BigDecimal.ZERO
                DropdownMenuItem(
                    text = {
                        Text(
                            candidate.name + " · " +
                                CurrencyFormatter.formatSimple(balance, candidate.currency) +
                                if (candidate.type == "CASH" && !candidate.includedInTotal) " · set aside" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        if (accountId == candidate.id) Text("✓", color = MaterialTheme.colorScheme.primary)
                    },
                    onClick = { onAccountSelected(candidate.id) }
                )
            }
        }
    }
    Text(
        text = if (account == null) {
            if (isDebt) "External revenue pays this without touching a balance."
            else "Received outside your balances - no balance changes."
        } else if (account.type == "CASH" && !account.includedInTotal) {
            account.name + " is set aside, so total available does not change."
        } else {
            if (isDebt) "The money leaves " + account.name + " when you confirm."
            else "The money lands on " + account.name + " when you confirm."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2
    )

    val accountAmount = form.accountAmount
    if (account != null && accountAmount != null && form.error == null) {
        val balance = balances[account.id] ?: BigDecimal.ZERO
        Text(
            text = (if (isDebt) "-" else "+") + CurrencyFormatter.format(accountAmount, account.currency) +
                " · " + account.name + " → " +
                CurrencyFormatter.format(
                    if (isDebt) balance - accountAmount else balance + accountAmount,
                    account.currency
                ),
            style = MaterialTheme.typography.labelSmall.merge(AppType.tabular),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        if (account.currency != loan.currencyOriginal && rateReady) {
            Text(
                text = "1 ${loan.currencyOriginal} = ${rate.stripTrailingZeros().toPlainString()} ${account.currency}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }

    AppTextField(
        value = note,
        onValueChange = onNoteChange,
        label = "Note (optional)"
    )
    Text(
        text = if (form.settlesFully) {
            "Recorded as a transaction in History; the loan then moves to the settled archive."
        } else {
            "Recorded as a transaction in History."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3
    )
}
