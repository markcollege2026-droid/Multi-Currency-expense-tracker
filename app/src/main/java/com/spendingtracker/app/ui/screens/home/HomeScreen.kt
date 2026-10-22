package com.spendingtracker.app.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.local.entity.Expense
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.ui.components.*
import com.spendingtracker.app.ui.theme.AppType
import com.spendingtracker.app.ui.theme.LocalAppPalette
import com.spendingtracker.app.util.CurrencyFormatter
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTopUp: () -> Unit,
    onNavigateToRecurring: () -> Unit = {},
    onNavigateToTransactions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showQuickAdd by remember { mutableStateOf(false) }
    var showAdjustBalance by remember { mutableStateOf(false) }
    var showWithdraw by remember { mutableStateOf(false) }
    // The pop-out Add menu doubles as the primary "what happened?" entry point.
    var addMenuOpen by rememberSaveable { mutableStateOf(false) }
    // Hoisted so both the accounts panel and the withdraw dialog can open the
    // create-balance sheet (a dialog must never dead-end on Cancel).
    var createType by remember { mutableStateOf<String?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current
    val cashAccounts = remember(uiState.balanceAccounts) {
        uiState.balanceAccounts.filter { it.type == "CASH" }
    }
    var withdrawTargetId by remember { mutableStateOf<Long?>(null) }

    // While the pop-out menu is open, Back closes it instead of leaving the screen.
    BackHandler(enabled = addMenuOpen) { addMenuOpen = false }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Spending",
                actions = {
                    Box {
                        AppPill(
                            text = uiState.activeAccount?.name ?: "Account",
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.widthIn(max = if (compact) 88.dp else 110.dp),
                            contentDescription = "Switch account"
                        )
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        ) {
                            uiState.accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${account.name} (${account.currency})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = {
                                        if (account.id == uiState.activeAccount?.id) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    onClick = {
                                        viewModel.switchAccount(account.id)
                                        accountMenuExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            DropdownMenuItem(
                                text = { Text("Manage accounts…", style = MaterialTheme.typography.bodyMedium) },
                                onClick = {
                                    accountMenuExpanded = false
                                    onNavigateToSettings()
                                }
                            )
                        }
                    }
                    AppIconButton(
                        onClick = { showQuickAdd = true },
                        icon = Icons.Default.Add,
                        contentDescription = "Quick add"
                    )
                    AppIconButton(
                        onClick = onNavigateToSettings,
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            AppLoadingScreen(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = dims.screenPadding,
                    end = dims.screenPadding,
                    top = 8.dp,
                    bottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(dims.sectionSpacing)
            ) {
                item(key = "balance") {
                    BalanceHero(
                        balance = uiState.balance,
                        currency = uiState.balanceCurrency,
                        isNegative = uiState.isNegativeBalance,
                        isLow = uiState.isLowBalance,
                        ratesStale = uiState.isRatesStale,
                        week = uiState.totalSpentWeek,
                        month = uiState.totalSpentMonth,
                        onAdjust = { showAdjustBalance = true }
                    )
                }

                item(key = "quick_actions") {
                    AddMenu(
                        expanded = addMenuOpen,
                        onToggle = { addMenuOpen = !addMenuOpen },
                        onExpense = { addMenuOpen = false; onNavigateToAddExpense() },
                        onIncome = { addMenuOpen = false; onNavigateToAddTopUp() },
                        onWithdraw = { addMenuOpen = false; showWithdraw = true }
                    )
                }

                item(key = "accounts") {
                    BalanceAccountsPanel(
                        state = uiState,
                        viewModel = viewModel,
                        createType = createType,
                        onCreateTypeChange = { createType = it },
                        onWithdraw = { cashId ->
                            showWithdraw = true
                            // Remember which cash row started it.
                            withdrawTargetId = cashId
                        }
                    )
                }

                item(key = "recent_header") {
                    AppSectionHeader(
                        title = "Recent",
                        trailing = {
                            AppPill(
                                text = "See all",
                                onClick = onNavigateToTransactions,
                                contentDescription = "See all transactions"
                            )
                        }
                    )
                }

                val recent = uiState.recentExpenses.take(if (compact) 6 else 10)
                if (recent.isEmpty()) {
                    item(key = "recent_empty") {
                        AppEmptyState("No transactions yet.\nAdd an expense or income to get started.")
                    }
                } else {
                    items(recent, key = { "expense_${it.id}" }) { expense ->
                        ExpenseItem(
                            expense = expense,
                            onEdit = { onEditExpense(expense.id) },
                            categoryName = uiState.categories.firstOrNull { it.id == expense.categoryId }?.name
                        )
                    }
                }

                if (uiState.topCategories.isNotEmpty()) {
                    item(key = "categories") {
                        TopCategoriesCard(
                            entries = uiState.topCategories.take(if (compact) 4 else 5),
                            currency = uiState.balanceCurrency
                        )
                    }
                }

                if (uiState.spendingTrend.isNotEmpty()) {
                    item(key = "trend") {
                        SpendingTrendCard(
                            trend = uiState.spendingTrend,
                            currency = uiState.balanceCurrency
                        )
                    }
                }

                item(key = "tools") {
                    ToolsCard(
                        onRecurring = onNavigateToRecurring,
                        onLoans = onNavigateToLoans
                    )
                }

                item(key = "bottom_space") { Spacer(Modifier.height(2.dp)) }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddBottomSheet(
            onDismiss = { showQuickAdd = false },
            onAddExpense = { showQuickAdd = false; onNavigateToAddExpense() },
            onAddMoney = { showQuickAdd = false; onNavigateToAddTopUp() },
            onWithdraw = { showQuickAdd = false; withdrawTargetId = null; showWithdraw = true },
            onAddRecurring = { showQuickAdd = false; onNavigateToRecurring() }
        )
    }

    if (showWithdraw) {
        WithdrawDialog(
            cashAccounts = cashAccounts,
            mainCurrency = uiState.balanceCurrency,
            mainBalance = uiState.balance,
            rateLookup = viewModel::conversionRate,
            initialCashId = withdrawTargetId,
            onConfirm = { cashId, amount, note ->
                viewModel.withdraw(cashId, amount, note)
                showWithdraw = false
                withdrawTargetId = null
            },
            onCreateCash = {
                showWithdraw = false
                withdrawTargetId = null
                createType = "CASH"
            },
            onDismiss = {
                showWithdraw = false
                withdrawTargetId = null
            }
        )
    }

    if (showAdjustBalance) {
        AdjustBalanceDialog(
            currentBalance = uiState.balance,
            balanceCurrency = uiState.balanceCurrency,
            onDismiss = { showAdjustBalance = false },
            onConfirm = { desired ->
                viewModel.adjustBalance(desired)
                showAdjustBalance = false
            }
        )
    }
}

/**
 * The single gradient surface in the app: total balance, the two spend windows and
 * any warnings, with the adjust action kept on the card itself.
 */
@Composable
private fun BalanceHero(
    balance: BigDecimal,
    currency: String,
    isNegative: Boolean,
    isLow: Boolean,
    ratesStale: Boolean,
    week: BigDecimal,
    month: BigDecimal,
    onAdjust: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    val shape = RoundedCornerShape(if (compact) 20.dp else 28.dp)
    val warnings = buildList {
        if (isLow) add("Low balance")
        if (isNegative) add("Negative balance")
        if (ratesStale) add("Rates stale")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd)))
            .padding(if (compact) 14.dp else 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TOTAL BALANCE",
                style = if (compact) AppType.eyebrowCompact else AppType.eyebrow,
                color = palette.onHeroMuted,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, palette.onHeroFaint, RoundedCornerShape(50))
                    .clickable(onClick = onAdjust)
                    .padding(horizontal = 11.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Adjust",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onHero
                )
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            text = CurrencyFormatter.format(balance, currency),
            fontSize = if (compact) 32.sp else 42.sp,
            lineHeight = if (compact) 36.sp else 48.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = palette.onHero,
            style = MaterialTheme.typography.headlineLarge.merge(AppType.tabular)
        )
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.onHeroFaint)
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HeroFigure(
                label = "This week",
                value = CurrencyFormatter.formatSimple(week, currency),
                modifier = Modifier.weight(1f)
            )
            HeroFigure(
                label = "This month",
                value = CurrencyFormatter.formatSimple(month, currency),
                modifier = Modifier.weight(1f)
            )
        }
        if (warnings.isNotEmpty()) {
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.onHeroFaint)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "⚠ " + warnings.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onHero,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: String, modifier: Modifier = Modifier) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = if (compact) AppType.eyebrowCompact else AppType.eyebrow,
            color = palette.onHeroMuted,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
            fontWeight = FontWeight.SemiBold,
            color = palette.onHero,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopCategoriesCard(entries: List<Pair<com.spendingtracker.app.data.local.entity.Category?, BigDecimal>>, currency: String) {
    val total = remember(entries) {
        entries.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.second }
    }
    AppSectionCard(trailing = { Text("October", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }, title = "Top categories") {
        entries.forEachIndexed { index, (category, amount) ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            val dotColor = try {
                Color(category?.color ?: 0xFF888888.toInt())
            } catch (e: Exception) {
                Color.Gray
            }
            val share = if (total <= BigDecimal.ZERO) 0f else (amount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppDot(color = dotColor, sizeDp = 8)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category?.name ?: "Uncategorized",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyFormatter.format(amount, currency),
                        style = MaterialTheme.typography.bodyMedium.merge(AppType.tabular),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(share)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpendingTrendCard(trend: List<Pair<Long, BigDecimal>>, currency: String) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    val max = remember(trend) { trend.maxOfOrNull { it.second.toDouble() } ?: 0.0 }
    val average = remember(trend) {
        if (trend.isEmpty()) BigDecimal.ZERO
        else trend.fold(BigDecimal.ZERO) { sum, entry -> sum + entry.second }
            .divide(BigDecimal(trend.size), 2, java.math.RoundingMode.HALF_UP)
    }
    val barMax = if (compact) 34.dp else 56.dp
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LAST 7 DAYS",
                    style = if (compact) AppType.eyebrowCompact else AppType.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "avg ${CurrencyFormatter.formatSimple(average, currency)}/day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                trend.forEach { (date, amount) ->
                    val ratio = if (max <= 0.0) 0.08f else (amount.toDouble() / max).toFloat().coerceIn(0.08f, 1f)
                    val isToday = trend.lastOrNull()?.first == date
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barMax),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(ratio)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isToday) palette.heroStart
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    )
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = SimpleDateFormat("d", Locale.getDefault()).format(Date(date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsCard(onRecurring: () -> Unit, onLoans: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppSectionHeader(title = "Tools")
        Spacer(Modifier.height(6.dp))
        AppCard(padding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
            AppListRow(
                title = "Recurring payments",
                meta = "Manage repeating payments",
                leading = {
                    AppAvatar(
                        text = "↻",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                showChevron = true,
                onClick = onRecurring
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
            )
            AppListRow(
                title = "Loans & debts",
                meta = "I owe / owed to me",
                leading = {
                    AppAvatar(
                        text = "⇄",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                },
                showChevron = true,
                onClick = onLoans
            )
        }
    }
}

/** Compact summary tile kept for reuse by other screens. */
@Composable
fun StatCard(modifier: Modifier = Modifier, title: String, amount: String) {
    AppStatTile(label = title, value = amount, modifier = modifier)
}

@Composable
fun ExpenseItem(
    expense: Expense,
    onEdit: () -> Unit,
    categoryName: String? = null
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    val name = categoryName ?: expense.note ?: "Expense"
    AppCard(
        onClick = onEdit,
        padding = PaddingValues(
            horizontal = if (compact) 10.dp else 12.dp,
            vertical = if (compact) 8.dp else 10.dp
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppAvatar(
                text = name,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                size = if (compact) 32.dp else 38.dp
            )
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(expense.date)) +
                        " · " + CurrencyFormatter.formatSimple(expense.originalAsBigDecimal(), expense.currencyOriginal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                // The deduction is stored in the paying account's currency: normally
                // main, but a cash balance can hold a different currency.
                text = "-" + CurrencyFormatter.formatSimple(expense.convertedAsBigDecimal(), expense.currencyConverted),
                style = MaterialTheme.typography.titleSmall.merge(AppType.tabular),
                fontWeight = FontWeight.Bold,
                color = palette.expense,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBottomSheet(
    onDismiss: () -> Unit,
    onAddExpense: () -> Unit,
    onAddMoney: () -> Unit,
    onWithdraw: () -> Unit = {},
    onAddRecurring: () -> Unit = {}
) {
    val compact = LocalCompactUi.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 16.dp else 20.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "QUICK ADD",
                style = if (compact) AppType.eyebrowCompact else AppType.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "What happened?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            AppButton(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) { Text("Add expense") }
            AppOutlinedButton(onClick = onAddMoney, modifier = Modifier.fillMaxWidth()) { Text("Add income") }
            AppOutlinedButton(onClick = onWithdraw, modifier = Modifier.fillMaxWidth()) { Text("Withdraw to cash") }
            AppOutlinedButton(onClick = onAddRecurring, modifier = Modifier.fillMaxWidth()) { Text("Recurring payment") }
        }
    }
}

/**
 * The "+ Add" control from the feature pass, rebuilt on the Ledger tokens.
 *
 * Collapsed it is one primary button; opened, three options pop out with a short
 * staggered scale/fade. The row stacks into a column on the 240dp Qin window so
 * no label is truncated and every option stays reachable with the D-pad.
 */
@Composable
fun AddMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onExpense: () -> Unit,
    onIncome: () -> Unit,
    onWithdraw: () -> Unit
) {
    val compact = LocalCompactUi.current
    val palette = LocalAppPalette.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AppButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Close" else "+ Add", maxLines = 1)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
        ) {
            // Each option pops in 60ms after the previous one.
            if (compact) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PopOutOption(0, expanded) {
                        AppButton(onClick = onExpense, modifier = Modifier.fillMaxWidth()) {
                            Text("Expense", maxLines = 1)
                        }
                    }
                    PopOutOption(1, expanded) {
                        AppOutlinedButton(
                            onClick = onIncome,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Income", maxLines = 1, color = palette.income) }
                    }
                    PopOutOption(2, expanded) {
                        AppOutlinedButton(
                            onClick = onWithdraw,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Withdraw to cash", maxLines = 1) }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PopOutOption(0, expanded, modifier = Modifier.weight(1f)) {
                        AppButton(onClick = onExpense, fillWidth = true) {
                            Text("Expense", maxLines = 1)
                        }
                    }
                    PopOutOption(1, expanded, modifier = Modifier.weight(1f)) {
                        AppOutlinedButton(onClick = onIncome, fillWidth = true) {
                            Text("Income", maxLines = 1, color = palette.income)
                        }
                    }
                    PopOutOption(2, expanded, modifier = Modifier.weight(1f)) {
                        AppOutlinedButton(onClick = onWithdraw, fillWidth = true) {
                            Text("Withdraw", maxLines = 1)
                        }
                    }
                }
            }
        }
        if (expanded) {
            Text(
                text = "Moves money from main into a cash balance, converting it " +
                    "automatically when that cash balance uses another currency.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}

/** One menu entry: scales up and fades in, with a per-index delay. */
@Composable
private fun PopOutOption(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val delay = index * 60
    val exitDelay = (2 - index) * 40
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.72f,
            animationSpec = tween(220, delayMillis = delay, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(180, delayMillis = delay)),
        exit = scaleOut(
            targetScale = 0.82f,
            animationSpec = tween(140, delayMillis = exitDelay, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(120, delayMillis = exitDelay))
    ) {
        content()
    }
}

@Composable
fun AdjustBalanceDialog(
    currentBalance: BigDecimal,
    balanceCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal) -> Unit
) {
    var input by remember(currentBalance, balanceCurrency) { mutableStateOf(currentBalance.toPlainString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    val currentFormatted = remember(currentBalance, balanceCurrency) {
        CurrencyFormatter.format(currentBalance, balanceCurrency)
    }
    val desired = remember(input) {
        try { BigDecimal(input.trim().replace(',', '.')) } catch (e: Exception) { null }
    }
    val diff = remember(desired, currentBalance) { desired?.subtract(currentBalance) }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (hardwareKeypad) keyboard?.hide()
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Adjust balance",
        confirmLabel = "Adjust",
        confirmEnabled = desired != null,
        onConfirm = {
            val parsed = try { BigDecimal(input.trim().replace(',', '.')) } catch (e: Exception) { null }
            if (parsed == null) error = "Invalid number" else onConfirm(parsed)
        }
    ) {
        Text(
            text = "Current: $currentFormatted",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = "New balance ($balanceCurrency)",
            keyboardType = KeyboardType.Decimal,
            allowNegative = true,
            isError = error != null,
            supportingText = error,
            focusRequester = focus
        )
        if (diff != null && diff.compareTo(BigDecimal.ZERO) != 0) {
            val positive = diff > BigDecimal.ZERO
            Text(
                text = (if (positive) "Will add " else "Will deduct ") +
                    CurrencyFormatter.format(diff.abs(), balanceCurrency) +
                    " as ${if (positive) "income" else "expense"} (balance adjustment)",
                style = MaterialTheme.typography.labelSmall,
                color = if (positive) LocalAppPalette.current.income else LocalAppPalette.current.expense
            )
        } else {
            Text(
                text = "Enter the desired total. The difference is logged as an adjustment.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
