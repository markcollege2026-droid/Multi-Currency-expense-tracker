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
import com.spendingtracker.app.domain.SettlementRate
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

/**
 * Validated state of the settle form, pure so the rules can be tested directly.
 *
 * The loan's outstanding balance is **always** denominated in the loan's own
 * currency ([amount]); the payment may be made in another currency ([payCurrency]),
 * in which case [payAmount] is the same payment on that side.
 */
data class SettlementFormState(
    /** What this payment reduces on the loan, in the loan's currency. */
    val amount: BigDecimal?,
    /** The same payment in [payCurrency]; null when paying in the loan's currency. */
    val payAmount: BigDecimal?,
    /** Currency the money is actually handed over in. */
    val payCurrency: String,
    val error: String?,
    val canConfirm: Boolean,
    /** True when confirming closes the loan and moves it to the archive. */
    val settlesFully: Boolean,
    /** The loan was already fully repaid - confirming only archives it. */
    val alreadyRepaid: Boolean,
    /** True when the typed digits are in [payCurrency] rather than the loan's. */
    val typedInPayCurrency: Boolean = false
) {
    /** Remaining after this payment, in the loan's currency; null when unknown. */
    fun remainingAfter(remaining: BigDecimal): BigDecimal? = amount?.let { remaining - it }
}

/**
 * Settlement rules.
 *
 * The amount is typed in the loan's currency or in the currency being paid
 * ([payCurrency]); it defaults to the whole remaining balance and may not exceed
 * it. A payment in another currency converts through [rate] - one unit of the loan
 * currency in payment-currency units - so ₪9 at 3 ILS = $1 is $3, and paying $2
 * applies ₪6 and leaves ₪3.
 *
 * Paying a debt *from a balance* also requires that balance to hold the converted
 * amount; receiving a loan repayment into a balance has no such limit. With no
 * account chosen - external revenue, the default - no balance is involved, but the
 * payment can still be made in any currency.
 *
 * A cross-currency payment needs a real rate. [rate] of zero (or less) means "no
 * rate available" and refuses to confirm: silently treating it as 1:1 is how a
 * payment could be recorded at the wrong amount.
 */
fun settlementFormState(
    amountText: String,
    remaining: BigDecimal,
    isDebt: Boolean,
    loanCurrency: String,
    account: Account?,
    accountBalance: BigDecimal?,
    rate: BigDecimal,
    /**
     * Currency the money is paid in. When a balance is chosen this is that
     * balance's currency (money cannot leave a balance in another currency).
     */
    payCurrency: String = loanCurrency,
    typedInPayCurrency: Boolean = payCurrency != loanCurrency
): SettlementFormState {
    if (remaining.compareTo(BigDecimal.ZERO) == 0) {
        return SettlementFormState(
            amount = BigDecimal.ZERO,
            payAmount = null,
            payCurrency = payCurrency,
            error = null,
            canConfirm = true,
            settlesFully = true,
            alreadyRepaid = true,
            typedInPayCurrency = typedInPayCurrency
        )
    }
    val parsed = amountText.parseAmountOrNull()?.takeIf { it > BigDecimal.ZERO }
    if (parsed == null) {
        return SettlementFormState(
            amount = null,
            payAmount = null,
            payCurrency = payCurrency,
            error = if (amountText.isNotBlank()) "Enter a positive amount" else null,
            canConfirm = false,
            settlesFully = false,
            alreadyRepaid = false,
            typedInPayCurrency = typedInPayCurrency
        )
    }

    val sameCurrency = payCurrency.equals(loanCurrency, ignoreCase = true)
    if (!sameCurrency && rate <= BigDecimal.ZERO) {
        return SettlementFormState(
            amount = null,
            payAmount = null,
            payCurrency = payCurrency,
            error = SettlementRate.missingRateMessage(loanCurrency, payCurrency),
            canConfirm = false,
            settlesFully = false,
            alreadyRepaid = false,
            typedInPayCurrency = typedInPayCurrency
        )
    }

    // Both sides of the payment, each in its own currency. The side the user typed
    // is kept exactly; only the derived side is rounded.
    val amounts = TransferMath.amounts(
        typed = parsed,
        rate = if (sameCurrency) BigDecimal.ONE else rate,
        typedInTargetCurrency = typedInPayCurrency && !sameCurrency
    )
    val amountInLoanCurrency = amounts.from
    // What moves in the paying currency - the same number as the loan side when
    // there is only one currency, but always available for the balance check below.
    val movesInPayCurrency = amounts.to
    // Only a real conversion has a second figure to show and store.
    val amountInPayCurrency = if (sameCurrency) null else amounts.to
    val error = when {
        amountInLoanCurrency <= BigDecimal.ZERO || movesInPayCurrency <= BigDecimal.ZERO ->
            "Amount is too small after conversion"
        amountInLoanCurrency > remaining ->
            "That is " + CurrencyFormatter.formatSimple(amountInLoanCurrency, loanCurrency) +
                " - more than the remaining " +
                CurrencyFormatter.formatSimple(remaining, loanCurrency) +
                (amountInPayCurrency?.let {
                    " (= " + CurrencyFormatter.formatSimple(
                        owedIn(remaining, rate, payCurrency, loanCurrency) ?: remaining,
                        payCurrency
                    ) + ")"
                } ?: "")
        isDebt && account != null && accountBalance != null && movesInPayCurrency > accountBalance ->
            "More than " + account.name + " holds (" +
                CurrencyFormatter.formatSimple(accountBalance, account.currency) + ")"
        else -> null
    }
    return SettlementFormState(
        amount = amountInLoanCurrency,
        payAmount = amountInPayCurrency,
        payCurrency = payCurrency,
        error = error,
        canConfirm = error == null,
        settlesFully = amountInLoanCurrency.compareTo(remaining) == 0,
        alreadyRepaid = false,
        typedInPayCurrency = typedInPayCurrency && !sameCurrency
    )
}

/**
 * What the outstanding balance is worth in [payCurrency] at [rate], i.e. how much
 * has to be paid to clear it. Null when the two currencies are the same or no rate
 * is available.
 */
fun owedIn(remaining: BigDecimal, rate: BigDecimal, payCurrency: String, loanCurrency: String): BigDecimal? =
    if (rate <= BigDecimal.ZERO || payCurrency.equals(loanCurrency, ignoreCase = true)) null
    else remaining.multiply(rate).setScale(TransferMath.SCALE, java.math.RoundingMode.HALF_EVEN)

/** Currency the amount field is currently expressed in. */
fun settlementAmountCurrency(
    loanCurrency: String,
    payCurrency: String,
    typedInPayCurrency: Boolean
): String = if (typedInPayCurrency && !payCurrency.equals(loanCurrency, ignoreCase = true)) payCurrency else loanCurrency

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
 * Settle a loan or debt: choose how much, which currency the money is paid in, and
 * which balance it moves through.
 *
 * The loan's balance stays in the loan's own currency no matter what is chosen
 * here; the payment converts into it. **Pay in** offers every currency when no
 * balance is selected (external revenue, the default), and the balance's own
 * currency once one is - money cannot leave a balance in a different currency.
 */
@Composable
fun SettleLoanDialog(
    loan: LoanDebt,
    remaining: BigDecimal,
    accounts: List<Account>,
    balances: Map<Long, BigDecimal>,
    rateLookup: suspend (fromCurrency: String, toCurrency: String) -> BigDecimal?,
    onConfirm: (
        amountLoanCurrency: String,
        accountId: Long?,
        rate: String,
        note: String?,
        payCurrency: String,
        payAmount: String?
    ) -> Unit,
    onDismiss: () -> Unit,
    activeAccountId: Long? = null,
    /** Currencies offered by the "Pay in" selector, in the user's configured order. */
    currencies: List<String> = listOf(loan.currencyOriginal)
) {
    val isDebt = loan.type == LoanDebt.TYPE_DEBT
    var amount by remember(loan.id, remaining) {
        mutableStateOf(remaining.stripTrailingZeros().toPlainString())
    }
    var note by remember(loan.id) { mutableStateOf("") }
    // External revenue is the default source: nothing leaves a balance.
    var accountId by remember(loan.id) { mutableStateOf<Long?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var currencyMenuExpanded by remember { mutableStateOf(false) }
    // Currency the money is handed over in. Defaults to the loan's own.
    var payCurrency by remember(loan.id) { mutableStateOf(loan.currencyOriginal) }
    var typedInPayCurrency by remember(loan.id) { mutableStateOf(false) }
    var rate by remember(loan.id) { mutableStateOf(BigDecimal.ONE) }
    var rateReady by remember(loan.id) { mutableStateOf(true) }
    // Set when the user picks a currency to pay in, so the field is rewritten with
    // the equivalent owed once the rate for that pair arrives: choosing USD should
    // leave a dollar figure in a field labelled "Amount (USD)", not shekels.
    var pendingPrefill by remember(loan.id) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val account = accounts.firstOrNull { it.id == accountId }
    // A chosen balance dictates the payment currency; without one, anything goes.
    val effectivePayCurrency = account?.currency ?: payCurrency
    val crossCurrency = !effectivePayCurrency.equals(loan.currencyOriginal, ignoreCase = true)
    val form = settlementFormState(
        amountText = amount,
        remaining = remaining,
        isDebt = isDebt,
        loanCurrency = loan.currencyOriginal,
        account = account,
        accountBalance = account?.let { balances[it.id] } ?: BigDecimal.ZERO,
        rate = rate,
        payCurrency = effectivePayCurrency,
        typedInPayCurrency = typedInPayCurrency
    )

    LaunchedEffect(Unit) {
        if (!form.alreadyRepaid) runCatching { focus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }
    // Fetch the rate whenever the paying side changes. A missing rate stays at zero,
    // which the form refuses rather than converting 1:1.
    LaunchedEffect(accountId, effectivePayCurrency) {
        if (!crossCurrency) {
            rate = BigDecimal.ONE
            rateReady = true
            return@LaunchedEffect
        }
        rateReady = false
        rate = try {
            rateLookup(loan.currencyOriginal, effectivePayCurrency) ?: BigDecimal.ZERO
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
        rateReady = true
        pendingPrefill?.takeIf { it.equals(effectivePayCurrency, ignoreCase = true) }?.let { target ->
            owedIn(remaining, rate, target, loan.currencyOriginal)?.let { owed ->
                amount = owed.stripTrailingZeros().toPlainString()
            }
            pendingPrefill = null
        }
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
                    if (crossCurrency) rate.toPlainString() else "1",
                    note.ifBlank { null },
                    effectivePayCurrency.uppercase(),
                    if (crossCurrency) form.payAmount?.toPlainString() else null
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
            typedInPayCurrency = typedInPayCurrency,
            payCurrency = effectivePayCurrency,
            currencies = currencies,
            currencyMenuOpen = currencyMenuExpanded,
            activeAccountId = activeAccountId,
            onAmountChange = { amount = it },
            onNoteChange = { note = it },
            onDropdownChange = { dropdownExpanded = it },
            onCurrencyMenuChange = { currencyMenuExpanded = it },
            onToggleTypedSide = { typedInPayCurrency = !typedInPayCurrency },
            onPayCurrencySelected = { chosen ->
                val foreign = !chosen.equals(loan.currencyOriginal, ignoreCase = true)
                payCurrency = chosen
                currencyMenuExpanded = false
                // Choosing another currency means the user is thinking in it, so the
                // field switches to it and is refilled with what is owed in it.
                typedInPayCurrency = foreign
                when {
                    !foreign -> amount = remaining.stripTrailingZeros().toPlainString()
                    // Already showing this currency's rate: refill straight away.
                    chosen.equals(effectivePayCurrency, ignoreCase = true) ->
                        owedIn(remaining, rate, chosen, loan.currencyOriginal)
                            ?.let { owed -> amount = owed.stripTrailingZeros().toPlainString() }
                    // Otherwise the rate arrives below and refills the field then.
                    else -> pendingPrefill = chosen
                }
            },
            onAccountSelected = {
                accountId = it
                dropdownExpanded = false
                // Choosing a balance resets the paying side to that balance's currency.
                typedInPayCurrency = false
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
    typedInPayCurrency: Boolean = false,
    /** Currency the money is paid in; the loan's own when nothing else is chosen. */
    payCurrency: String = loan.currencyOriginal,
    /** Currencies offered by the "Pay in" selector (ignored while a balance is chosen). */
    currencies: List<String> = listOf(loan.currencyOriginal),
    currencyMenuOpen: Boolean = false,
    activeAccountId: Long? = null,
    onAmountChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onDropdownChange: (Boolean) -> Unit = {},
    onCurrencyMenuChange: (Boolean) -> Unit = {},
    onToggleTypedSide: () -> Unit = {},
    onPayCurrencySelected: (String) -> Unit = {},
    onAccountSelected: (Long?) -> Unit = {}
) {
    val isDebt = loan.type == LoanDebt.TYPE_DEBT
    val account = accounts.firstOrNull { it.id == accountId }
    val crossCurrency = !payCurrency.equals(loan.currencyOriginal, ignoreCase = true)
    val amountCurrency = settlementAmountCurrency(loan.currencyOriginal, payCurrency, typedInPayCurrency)
    val owedInPayCurrency = owedIn(remaining, rate, payCurrency, loan.currencyOriginal)

    Text(
        text = if (isDebt) "YOU STILL OWE" else "STILL OWED TO YOU",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        // Always in the currency the loan was created in - the balance is never
        // re-denominated, whatever currency the payment is made in.
        text = CurrencyFormatter.format(remaining, loan.currencyOriginal) +
            " of " + CurrencyFormatter.formatSimple(loan.amountAsBigDecimal(), loan.currencyOriginal),
        style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (crossCurrency) {
        // What is still owed, expressed in the currency being paid. This is the line
        // that was missing: the balance used to look un-payable in any other currency.
        Text(
            text = if (!rateReady) "Working out the " + payCurrency + " amount\u2026"
            else if (owedInPayCurrency == null) "No rate for " + loan.currencyOriginal + " \u2192 " + payCurrency
            else "= " + CurrencyFormatter.format(owedInPayCurrency, payCurrency) +
                " at 1 " + loan.currencyOriginal + " = " + rate.stripTrailingZeros().toPlainString() +
                " " + payCurrency,
            style = MaterialTheme.typography.labelSmall.merge(AppType.tabular),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
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
    // Pay in: every currency when no balance is chosen; the balance's own currency
    // (as a two-way switch) once one is, because money cannot leave a balance in a
    // currency it does not hold.
    Text(
        text = "PAY IN",
        style = AppType.eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (account == null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AppSelectorRow(
                label = "Currency",
                value = payCurrency + " \u00b7 " + CurrencyFormatter.format(
                    owedInPayCurrency ?: remaining, if (crossCurrency) payCurrency else loan.currencyOriginal
                ),
                onClick = { onCurrencyMenuChange(true) }
            )
            DropdownMenu(
                expanded = currencyMenuOpen,
                onDismissRequest = { onCurrencyMenuChange(false) },
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                // The loan's own currency first, so the default is always one tap away.
                val options = (listOf(loan.currencyOriginal) + currencies).distinct()
                options.forEach { code ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                code + if (code.equals(loan.currencyOriginal, ignoreCase = true)) " (loan currency)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            if (code.equals(payCurrency, ignoreCase = true)) {
                                Text("\u2713", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = { onPayCurrencySelected(code) }
                    )
                }
            }
        }
    }
    // Which of the two currencies the typed amount is in. Offered whenever the
    // payment is in another currency, whether it goes through a balance or not.
    if (crossCurrency) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AppFilterChip(
                selected = !typedInPayCurrency,
                onClick = { if (typedInPayCurrency) onToggleTypedSide() },
                label = "Type in " + loan.currencyOriginal
            )
            AppFilterChip(
                selected = typedInPayCurrency,
                onClick = { if (!typedInPayCurrency) onToggleTypedSide() },
                label = "Type in " + payCurrency
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

    // What confirming does: for a balance, both sides of the movement; for an
    // external payment in another currency, the loan's reduction - the number that
    // actually changes on the loans page.
    val payAmount = form.payAmount
    if (form.error == null && form.amount != null) {
        if (account != null) {
            val moved = payAmount ?: form.amount
            val balance = balances[account.id] ?: BigDecimal.ZERO
            Text(
                text = (if (isDebt) "-" else "+") + CurrencyFormatter.format(moved, account.currency) +
                    " \u00b7 " + account.name + " \u2192 " +
                    CurrencyFormatter.format(
                        if (isDebt) balance - moved else balance + moved,
                        account.currency
                    ),
                style = MaterialTheme.typography.labelSmall.merge(AppType.tabular),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
        }
        if (crossCurrency) {
            Text(
                text = if (typedInPayCurrency) {
                    // The user typed in the payment currency, so show what it pays off.
                    CurrencyFormatter.format(form.amount, loan.currencyOriginal) + " comes off the loan"
                } else {
                    // The user typed in the loan currency, so show what to hand over.
                    "Paying " + CurrencyFormatter.format(payAmount ?: form.amount, payCurrency) +
                        " covers it"
                },
                style = MaterialTheme.typography.labelSmall.merge(AppType.tabular),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
        }
        // A missing rate is never silent: it reaches this branch as form.error (the
        // field's supporting text) instead, so nothing is confirmed 1:1.
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
