package com.spendingtracker.app.ui.screens.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import com.spendingtracker.app.domain.SettlementAccounts
import com.spendingtracker.app.domain.TransferMath
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppHairline
import com.spendingtracker.app.ui.components.AppSelectorRow
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
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
    val alreadyRepaid: Boolean,
    /** Whichever side the user is typing, so the field can keep their exact digits. */
    val typedInAccountCurrency: Boolean = false
)

/**
 * Settlement rules: the amount may be typed in the loan's currency **or** in the
 * currency of the balance it moves through, defaults to the whole remaining
 * balance, and may not exceed it. Paying a debt from a balance also requires that
 * balance to hold the (converted) amount; receiving a loan repayment into a
 * balance has no such limit. With no account chosen - external revenue, the
 * default - no balance is involved at all.
 *
 * A cross-currency settlement needs a real rate. [rate] of zero (or less) means
 * "no rate available" and refuses to confirm: silently treating it as 1:1 is how
 * a payment could leave a wallet by the wrong amount.
 */
fun settlementFormState(
    amountText: String,
    remaining: BigDecimal,
    isDebt: Boolean,
    loanCurrency: String,
    account: Account?,
    accountBalance: BigDecimal?,
    rate: BigDecimal,
    typedInAccountCurrency: Boolean = false
): SettlementFormState {
    if (remaining.compareTo(BigDecimal.ZERO) == 0) {
        return SettlementFormState(
            amount = BigDecimal.ZERO,
            accountAmount = null,
            error = null,
            canConfirm = true,
            settlesFully = true,
            alreadyRepaid = true,
            typedInAccountCurrency = typedInAccountCurrency
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
            alreadyRepaid = false,
            typedInAccountCurrency = typedInAccountCurrency
        )
    }
    if (account == null) {
        // External: the typed amount is the loan-currency amount, nothing converts.
        if (parsed > remaining) {
            return SettlementFormState(
                amount = parsed,
                accountAmount = null,
                error = "More than the remaining " + CurrencyFormatter.formatSimple(remaining, loanCurrency),
                canConfirm = false,
                settlesFully = false,
                alreadyRepaid = false,
                typedInAccountCurrency = typedInAccountCurrency
            )
        }
        return SettlementFormState(
            amount = parsed,
            accountAmount = null,
            error = null,
            canConfirm = true,
            settlesFully = parsed.compareTo(remaining) == 0,
            alreadyRepaid = false,
            typedInAccountCurrency = typedInAccountCurrency
        )
    }

    val sameCurrency = account.currency == loanCurrency
    if (!sameCurrency && rate <= BigDecimal.ZERO) {
        return SettlementFormState(
            amount = null,
            accountAmount = null,
            error = "No exchange rate for $loanCurrency \u2192 ${account.currency}. Refresh rates in Settings.",
            canConfirm = false,
            settlesFully = false,
            alreadyRepaid = false,
            typedInAccountCurrency = typedInAccountCurrency
        )
    }
    // Both sides of the payment, each in its own currency. The side the user typed
    // is kept exactly; only the derived side is rounded.
    val amounts = TransferMath.amounts(
        typed = parsed,
        rate = if (sameCurrency) BigDecimal.ONE else rate,
        typedInTargetCurrency = typedInAccountCurrency
    )
    val amountInLoanCurrency = amounts.from
    val amountInAccountCurrency = amounts.to
    val error = when {
        amountInLoanCurrency <= BigDecimal.ZERO || amountInAccountCurrency <= BigDecimal.ZERO ->
            "Amount is too small after conversion"
        amountInLoanCurrency > remaining ->
            "More than the remaining " + CurrencyFormatter.formatSimple(remaining, loanCurrency)
        isDebt && accountBalance != null && amountInAccountCurrency > accountBalance ->
            "More than " + account.name + " holds (" +
                CurrencyFormatter.formatSimple(accountBalance, account.currency) + ")"
        else -> null
    }
    return SettlementFormState(
        amount = amountInLoanCurrency,
        accountAmount = amountInAccountCurrency,
        error = error,
        canConfirm = error == null,
        settlesFully = amountInLoanCurrency.compareTo(remaining) == 0,
        alreadyRepaid = false,
        typedInAccountCurrency = typedInAccountCurrency
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

/** Column the amount field is currently expressed in. */
fun settlementAmountCurrency(loanCurrency: String, account: Account?, typedInAccountCurrency: Boolean): String =
    if (account != null && typedInAccountCurrency) account.currency else loanCurrency

/**
 * Settle a loan or debt: choose how much (the whole remainder by default), which
 * balance the money moves through, and which of the two currencies the amount is
 * typed in. The dropdown defaults to external revenue - the settlement is still
 * recorded as a transaction, it just does not touch a tracked balance.
 */
@Composable
fun SettleLoanDialog(
    loan: LoanDebt,
    remaining: BigDecimal,
    accounts: List<Account>,
    balances: Map<Long, BigDecimal>,
    rateLookup: suspend (fromCurrency: String, toCurrency: String) -> BigDecimal?,
    onConfirm: (amountLoanCurrency: String, accountId: Long?, rate: String, note: String?) -> Unit,
    onDismiss: () -> Unit,
    activeAccountId: Long? = null
) {
    val isDebt = loan.type == LoanDebt.TYPE_DEBT
    var amount by remember(loan.id, remaining) {
        mutableStateOf(remaining.stripTrailingZeros().toPlainString())
    }
    var note by remember(loan.id) { mutableStateOf("") }
    // External revenue is the default source: nothing leaves a balance.
    var accountId by remember(loan.id) { mutableStateOf<Long?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var typedInAccountCurrency by remember(loan.id) { mutableStateOf(false) }
    var rate by remember(loan.id) { mutableStateOf(BigDecimal.ONE) }
    var rateReady by remember(loan.id) { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val account = accounts.firstOrNull { it.id == accountId }
    // Typing in the account's currency is meaningless without an account.
    val usesAccountCurrency = account != null && account.currency != loan.currencyOriginal
    val typedInAccount = typedInAccountCurrency && usesAccountCurrency
    val form = settlementFormState(
        amountText = amount,
        remaining = remaining,
        isDebt = isDebt,
        loanCurrency = loan.currencyOriginal,
        account = account,
        accountBalance = account?.let { balances[it.id] } ?: BigDecimal.ZERO,
        rate = rate,
        typedInAccountCurrency = typedInAccount
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
        // A null rate stays null (zero here) instead of quietly becoming 1:1.
        rate = try { rateLookup(loan.currencyOriginal, target) ?: BigDecimal.ZERO } catch (e: Exception) { BigDecimal.ZERO }
        rateReady = true
        typedInAccountCurrency = false
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
            typedInAccountCurrency = typedInAccount,
            activeAccountId = activeAccountId,
            onAmountChange = { amount = it },
            onNoteChange = { note = it },
            onDropdownChange = { dropdownExpanded = it },
            onToggleTypedSide = { typedInAccountCurrency = !typedInAccountCurrency },
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
    typedInAccountCurrency: Boolean = false,
    activeAccountId: Long? = null,
    onAmountChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onDropdownChange: (Boolean) -> Unit = {},
    onToggleTypedSide: () -> Unit = {},
    onAccountSelected: (Long?) -> Unit = {}
) {
    val isDebt = loan.type == LoanDebt.TYPE_DEBT
    val account = accounts.firstOrNull { it.id == accountId }
    val amountCurrency = settlementAmountCurrency(loan.currencyOriginal, account, typedInAccountCurrency)

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
        label = "Amount ($amountCurrency)",
        keyboardType = KeyboardType.Decimal,
        isError = form.error != null,
        supportingText = form.error,
        focusRequester = focusRequester
    )
    // Why there is no currency choice here: the amount is in the loan's currency
    // because no balance is involved yet. Without this line the dialog looked like
    // it could only ever record payments in one currency - the "Type in ILS / Type
    // in EUR" chips only appear once a balance in another currency is chosen.
    if (account == null) {
        val otherCurrency = accounts.firstOrNull { it.currency != loan.currencyOriginal }
        if (otherCurrency != null) {
            Text(
                text = "Paying in another currency? Choose a balance under " +
                    (if (isDebt) "PAY FROM" else "RECEIVE INTO") + " - e.g. " +
                    otherCurrency.name + " (" + otherCurrency.currency + ") - and the amount " +
                    "can be typed in either currency.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3
            )
        }
    }
    // Which currency the typed amount is in. Only offered when it can differ.
    if (account != null && account.currency != loan.currencyOriginal) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AppFilterChip(
                selected = !typedInAccountCurrency,
                onClick = { if (typedInAccountCurrency) onToggleTypedSide() },
                label = "Type in ${loan.currencyOriginal}"
            )
            AppFilterChip(
                selected = typedInAccountCurrency,
                onClick = { if (!typedInAccountCurrency) onToggleTypedSide() },
                label = "Type in ${account.currency}"
            )
        }
    }
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
                SettlementAccounts.label(it, accounts, balances[it.id] ?: BigDecimal.ZERO, activeAccountId)
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
                    if (accountId == null) Text("\u2713", color = MaterialTheme.colorScheme.primary)
                },
                onClick = { onAccountSelected(null) }
            )
            accounts.forEach { candidate ->
                val balance = balances[candidate.id] ?: BigDecimal.ZERO
                DropdownMenuItem(
                    text = {
                        Text(
                            SettlementAccounts.label(candidate, accounts, balance, activeAccountId),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        if (accountId == candidate.id) Text("\u2713", color = MaterialTheme.colorScheme.primary)
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
                " \u00b7 " + account.name + " \u2192 " +
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
            if (rate > BigDecimal.ZERO) {
                Text(
                    text = "1 ${loan.currencyOriginal} = ${rate.stripTrailingZeros().toPlainString()} ${account.currency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else {
                Text(
                    text = "No ${loan.currencyOriginal} \u2192 ${account.currency} rate on this device. " +
                        "Refresh rates in Settings to settle in ${account.currency}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3
                )
            }
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
