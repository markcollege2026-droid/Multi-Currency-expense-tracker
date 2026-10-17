package com.spendingtracker.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.data.local.entity.Account
import com.spendingtracker.app.domain.AccountBalances
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun BalanceAccountsPanel(state: HomeUiState, viewModel: HomeViewModel) {
    var createType by remember { mutableStateOf<String?>(null) }
    var paymentCard by remember { mutableStateOf<Account?>(null) }
    var configureCard by remember { mutableStateOf<Account?>(null) }
    var cashAccount by remember { mutableStateOf<Account?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Balances", style = MaterialTheme.typography.titleLarge)
            Text("Main/debit: ${CurrencyFormatter.format(state.balance, state.balanceCurrency)}")
            val cash = state.balanceAccounts.firstOrNull { it.type == "CASH" }
            Text("Cash: ${CurrencyFormatter.format(cash?.let { state.accountBalances[it.id] } ?: BigDecimal.ZERO, state.balanceCurrency)}")
            if (cash == null) TextButton(onClick = { createType = "CASH" }) { Text("Set up cash balance") }
            else TextButton(onClick = { cashAccount = cash }) { Text("Add cash income") }
            state.balanceAccounts.filter { it.type == "CREDIT_CARD" }.forEach { card ->
                val balance = state.accountBalances[card.id] ?: BigDecimal.ZERO
                HorizontalDivider()
                Text("${card.name}: ${CurrencyFormatter.format(balance, card.currency)}", style = MaterialTheme.typography.titleMedium)
                if (card.autopayEnabled) {
                    val today = LocalDate.now()
                    val due = AccountBalances.dueDate(card, YearMonth.from(today))
                    val attempted = state.cardPayments.any { it.cardId == card.id && it.autopayPeriod == YearMonth.from(today).toString() }
                    val next = if (attempted || due < today) AccountBalances.dueDate(card, YearMonth.from(today).plusMonths(1)) else due
                    val amount = AccountBalances.paymentAmount(card, balance)
                    Text("Autopay $next · ${CurrencyFormatter.format(amount, card.currency)} · ${card.autopayMode.replace('_', ' ')}")
                    if (amount > state.balance) Text("Warning: main/debit funds are too low for autopay", color = MaterialTheme.colorScheme.error)
                } else Text("Autopay disabled")
                Row {
                    TextButton(onClick = { paymentCard = card }) { Text("Pay card") }
                    TextButton(onClick = { configureCard = card }) { Text("Autopay settings") }
                }
            }
            TextButton(onClick = { createType = "CREDIT_CARD" }) { Text("Add credit card") }
            HorizontalDivider()
            Text("Total available: ${CurrencyFormatter.format(state.totalAvailable, state.balanceCurrency)}", style = MaterialTheme.typography.titleLarge)
            Text("Main + cash − card debt (includes any card credit)", style = MaterialTheme.typography.bodySmall)
            state.cardPayments.filter { it.status == "FAILED_INSUFFICIENT_FUNDS" && !it.dismissed }.forEach { failed ->
                Text("Payment failed: insufficient main/debit funds — ${state.balanceAccounts.find { it.id == failed.cardId }?.name ?: "Card"}", color = MaterialTheme.colorScheme.error)
                Row {
                    TextButton(onClick = { viewModel.payCard(failed.cardId, failed.amount, false) }) { Text("Retry") }
                    TextButton(onClick = { paymentCard = state.balanceAccounts.find { it.id == failed.cardId } }) { Text("Partial payment") }
                    TextButton(onClick = { viewModel.dismissPayment(failed) }) { Text("Ignore") }
                }
            }
            if (state.message != null) {
                Text(state.message)
                TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
            }
        }
    }
    createType?.let { type ->
        var name by remember(type) { mutableStateOf(if (type == "CASH") "Cash" else "Credit card") }
        var opening by remember(type) { mutableStateOf("0") }
        var error by remember(type) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { createType = null }, title = { Text("Add ${type.replace('_', ' ')}") }, text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(opening, { opening = it }, label = { Text(if (type == "CREDIT_CARD") "Opening debt" else "Opening cash") })
                Text("Opening amounts do not withdraw money from main/debit.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = {
            if (name.isBlank() || opening.toBigDecimalOrNull()?.let { it < BigDecimal.ZERO } != false) error = "Enter a name and non-negative amount"
            else { viewModel.createBalanceAccount(name, type, opening); createType = null }
        }) { Text("Create") } }, dismissButton = { TextButton(onClick = { createType = null }) { Text("Cancel") } })
    }
    cashAccount?.let { cash ->
        MoneyDialog("Add cash income", "Adds external cash, not a withdrawal from main", false, { cashAccount = null }) { amount, _ ->
            viewModel.fundCash(cash.id, amount); cashAccount = null
        }
    }
    paymentCard?.let { card ->
        MoneyDialog("Pay ${card.name}", "Deducts from main/debit, not category spending", true, { paymentCard = null }) { amount, overpayment ->
            viewModel.payCard(card.id, amount, overpayment); paymentCard = null
        }
    }
    configureCard?.let { card -> AutopayDialog(card, { configureCard = null }) { viewModel.configureAutopay(it); configureCard = null } }
}

@Composable
private fun MoneyDialog(title: String, description: String, showOverpayment: Boolean, dismiss: () -> Unit, save: (String, Boolean) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var overpayment by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column {
            Text(description)
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount") })
            if (showOverpayment) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(overpayment, { overpayment = it }); Text("Explicitly allow overpayment")
            }
        }
    }, confirmButton = { TextButton(enabled = amount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true, onClick = { save(amount, overpayment) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun AutopayDialog(card: Account, dismiss: () -> Unit, save: (Account) -> Unit) {
    var enabled by remember { mutableStateOf(card.autopayEnabled) }
    var day by remember { mutableStateOf(card.autopayDay.toString()) }
    var mode by remember { mutableStateOf(card.autopayMode) }
    var fixed by remember { mutableStateOf(card.autopayFixedAmount ?: "") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Autopay · ${card.name}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text("Enable autopay") }
            OutlinedTextField(day, { day = it }, label = { Text("Due day (1–31)") })
            Text("Days 29–31 clamp to the last day in shorter months. Android may delay background work; due payments also run when Home opens. These are local ledger payments, not bank transfers.")
            listOf("FULL_BALANCE", "FIXED_AMOUNT").forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == option, { mode = option }); Text(option.replace('_', ' ')) }
            }
            if (mode == "FIXED_AMOUNT") OutlinedTextField(fixed, { fixed = it }, label = { Text("Fixed amount (capped at debt)") })
        }
    }, confirmButton = {
        TextButton(enabled = day.toIntOrNull() in 1..31 && (mode != "FIXED_AMOUNT" || fixed.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true), onClick = {
            save(card.copy(autopayEnabled = enabled, autopayDay = day.toInt(), autopayMode = mode, autopayFixedAmount = fixed.ifBlank { null }))
        }) { Text("Save") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
