package com.spendingtracker.app.ui.screens.converter

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendingtracker.app.data.repository.HistoricalRateSeries
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.components.AppButton
import com.spendingtracker.app.ui.components.AppDialog
import com.spendingtracker.app.ui.components.AppFilterChip
import com.spendingtracker.app.ui.components.AppIconButton
import com.spendingtracker.app.ui.components.AppTextField
import com.spendingtracker.app.ui.components.AppTopBar
import com.spendingtracker.app.util.CurrencyProvider
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val ConverterBlue = Color(0xFF2563EB)
private val PositiveGreenBg = Color(0xFFE6F4EA)
private val PositiveGreenText = Color(0xFF1E8E3E)
private val NegativeRedBg = Color(0xFFFCE8E6)
private val NegativeRedText = Color(0xFFD93025)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current

    var showAmountDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var actionSheetItemIndex by remember { mutableStateOf<Int?>(null) }
    var chartPickerMode by remember { mutableStateOf<String?>(null) } // "BASE" or "TARGET"

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Converter",
                actions = {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        AppIconButton(
                            onClick = { viewModel.refreshRatesFromApi(showFeedback = true) },
                            icon = Icons.Default.Refresh,
                            contentDescription = "Refresh rates"
                        )
                    }
                    AppIconButton(
                        onClick = { showAddDialog = true },
                        icon = Icons.Default.Add,
                        contentDescription = "Add currency or commodity"
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sub-navigation segmented bar: Converter | Charts (matching screenshot options)
            ConverterModeBar(
                selected = uiState.subTab,
                onSelect = { viewModel.selectSubTab(it) }
            )

            when (uiState.subTab) {
                ConverterSubTab.CONVERTER -> {
                    ConverterListContent(
                        uiState = uiState,
                        onLongPressTop = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAmountDialog = true
                        },
                        onPromoteToTop = { code ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.promoteToTop(code)
                        },
                        onLongPressTarget = { index ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            actionSheetItemIndex = index
                        },
                        onOpenChart = { code ->
                            viewModel.openChartForTarget(code)
                        },
                        onOpenAddDialog = { showAddDialog = true }
                    )
                }
                ConverterSubTab.CHARTS -> {
                    ConverterChartsContent(
                        uiState = uiState,
                        onSwapPair = { viewModel.swapChartPair() },
                        onPickBase = { chartPickerMode = "BASE" },
                        onPickTarget = { chartPickerMode = "TARGET" },
                        onSelectTargetQuick = { code ->
                            if (code != uiState.chartBaseCode) {
                                viewModel.setChartPair(uiState.chartBaseCode, code)
                            }
                        },
                        onSelectTimeframe = { tf -> viewModel.setChartTimeframe(tf) }
                    )
                }
            }
        }
    }

    // Long-press on top card -> Select / Enter Amount dialog
    if (showAmountDialog) {
        val topItem = uiState.items.firstOrNull()
        if (topItem != null) {
            AmountSelectionDialog(
                topItem = topItem,
                previewTargets = uiState.items.drop(1).take(3),
                initialAmountStr = uiState.baseAmountInput,
                onDismiss = { showAmountDialog = false },
                onConfirmAmount = { newAmount ->
                    viewModel.updateBaseAmount(newAmount)
                    showAmountDialog = false
                }
            )
        }
    }

    // Add / Manage Currencies & Commodities Dialog
    if (showAddDialog) {
        AddCurrencyOrCommodityDialog(
            selectedCodes = uiState.selectedCodes,
            onToggleCode = { code -> viewModel.toggleCurrencyInList(code) },
            onMakeTop = { code ->
                viewModel.addAndMakeTop(code)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // Context action dialog for non-top items
    actionSheetItemIndex?.let { index ->
        val item = uiState.items.getOrNull(index)
        if (item != null) {
            AppDialog(
                onDismissRequest = { actionSheetItemIndex = null },
                title = "${item.info.code} — ${item.info.name}",
                confirmLabel = null,
                dismissLabel = "Close",
                onConfirm = null,
                contentMaxHeightFraction = 0.62f
            ) {
                ConverterActionButton("⬆ Make top (set as base)") {
                    viewModel.promoteToTop(item.info.code)
                    actionSheetItemIndex = null
                }
                ConverterActionButton("📈 View ${uiState.selectedCodes.firstOrNull() ?: "USD"} / ${item.info.code} chart") {
                    viewModel.openChartForTarget(item.info.code)
                    actionSheetItemIndex = null
                }
                if (index > 0) {
                    ConverterActionButton("↑ Move up") {
                        viewModel.moveItem(index, index - 1)
                        actionSheetItemIndex = null
                    }
                }
                if (index < uiState.items.lastIndex) {
                    ConverterActionButton("↓ Move down") {
                        viewModel.moveItem(index, index + 1)
                        actionSheetItemIndex = null
                    }
                }
                if (uiState.items.size > 2) {
                    ConverterActionButton(
                        "🗑 Remove from converter list",
                        color = MaterialTheme.colorScheme.error
                    ) {
                        viewModel.removeFromList(item.info.code)
                        actionSheetItemIndex = null
                    }
                }
            }
        }
    }

    // Picker for Chart Base or Target currency/commodity
    chartPickerMode?.let { mode ->
        SingleCurrencyCommodityPickerDialog(
            title = if (mode == "BASE") "Select Chart Base" else "Select Chart Target",
            currentCode = if (mode == "BASE") uiState.chartBaseCode else uiState.chartTargetCode,
            onSelect = { chosen ->
                if (mode == "BASE") {
                    val target = if (chosen == uiState.chartTargetCode) uiState.chartBaseCode else uiState.chartTargetCode
                    viewModel.setChartPair(chosen, target)
                } else {
                    val base = if (chosen == uiState.chartBaseCode) uiState.chartTargetCode else uiState.chartBaseCode
                    viewModel.setChartPair(base, chosen)
                }
                chartPickerMode = null
            },
            onDismiss = { chartPickerMode = null }
        )
    }
}

@Composable
private fun ConverterModeBar(
    selected: ConverterSubTab,
    onSelect: (ConverterSubTab) -> Unit
) {
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenPadding, vertical = if (compact) 3.dp else 6.dp),
        shape = RoundedCornerShape(if (compact) 8.dp else 14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 2.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
        ) {
            val tabs = listOf(
                ConverterSubTab.CONVERTER to if (compact) "Converter" else "⇄  Converter",
                ConverterSubTab.CHARTS to if (compact) "Charts" else "📈  Charts"
            )
            tabs.forEach { (tab, label) ->
                val isSelected = selected == tab
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(if (compact) 6.dp else 10.dp))
                        .clickable { onSelect(tab) },
                    shape = RoundedCornerShape(if (compact) 6.dp else 10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSelected) 2.dp else 0.dp
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = if (compact) 5.dp else 10.dp),
                        textAlign = TextAlign.Center,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) ConverterBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConverterListContent(
    uiState: ConverterUiState,
    onLongPressTop: () -> Unit,
    onPromoteToTop: (String) -> Unit,
    onLongPressTarget: (Int) -> Unit,
    onOpenChart: (String) -> Unit,
    onOpenAddDialog: () -> Unit
) {
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current
    val topBaseCode = uiState.items.firstOrNull()?.info?.code ?: "USD"
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = dims.screenPadding,
            vertical = if (compact) 2.dp else 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp)
    ) {
        item(key = "hint_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (compact) "Hold top card to set amount · tap a row to promote"
                    else "Long-press top card for amount • Tap any row to make top",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                uiState.lastUpdatedMillis?.let { ts ->
                    Text(
                        text = timeFormatter.format(Date(ts)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        itemsIndexed(
            items = uiState.items,
            key = { _, item -> item.info.code }
        ) { index, item ->
            if (index == 0) {
                TopBaseCurrencyCard(item = item, onLongPressOrTap = onLongPressTop)
            } else {
                TargetCurrencyCard(
                    item = item,
                    baseCode = topBaseCode,
                    onTapPromoteToTop = { onPromoteToTop(item.info.code) },
                    onLongPress = { onLongPressTarget(index) },
                    onOpenChart = { onOpenChart(item.info.code) }
                )
            }
        }

        item(key = "add_more_footer") {
            OutlinedButton(
                onClick = onOpenAddDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 1.dp else 4.dp, bottom = if (compact) 6.dp else 16.dp),
                shape = RoundedCornerShape(if (compact) 8.dp else 14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = if (compact) 4.dp else 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(if (compact) 14.dp else 18.dp))
                Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
                Text(if (compact) "Add currencies" else "Add Currencies & Commodities")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBaseCurrencyCard(
    item: ConverterListItem,
    onLongPressOrTap: () -> Unit
) {
    val compact = LocalCompactUi.current
    val corner = if (compact) 10.dp else 18.dp
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .combinedClickable(onClick = onLongPressOrTap, onLongClick = onLongPressOrTap),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, ConverterBlue),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 8.dp else 16.dp,
                    vertical = if (compact) 7.dp else 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 28.dp else 46.dp)
                    .clip(CircleShape)
                    .background(ConverterBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.info.flag, fontSize = if (compact) 15.sp else 24.sp)
            }
            Spacer(Modifier.width(if (compact) 6.dp else 14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.info.code,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.info.isCommodity) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (compact) "◆" else "COMMODITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Text(
                    text = item.info.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = if (compact) 96.dp else 160.dp)) {
                Text(
                    text = formatConverterValue(item.convertedAmount),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ConverterBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (compact) "Amount" else "Amount (Hold)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TargetCurrencyCard(
    item: ConverterListItem,
    baseCode: String,
    onTapPromoteToTop: () -> Unit,
    onLongPress: () -> Unit,
    onOpenChart: () -> Unit
) {
    val compact = LocalCompactUi.current
    val isPositive = item.changePercent24h >= 0.0
    val badgeBg = if (isPositive) PositiveGreenBg else NegativeRedBg
    val badgeText = if (isPositive) PositiveGreenText else NegativeRedText
    val changeFormatted = String.format(
        Locale.US,
        "%s%.2f%%",
        if (isPositive) "+" else "",
        item.changePercent24h
    )
    val corner = if (compact) 8.dp else 16.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .combinedClickable(onClick = onTapPromoteToTop, onLongClick = onLongPress),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 7.dp else 16.dp,
                    vertical = if (compact) 6.dp else 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 24.dp else 44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.info.flag, fontSize = if (compact) 13.sp else 23.sp)
            }
            Spacer(Modifier.width(if (compact) 6.dp else 14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.info.code,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.info.isCommodity) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "◆",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    text = "1 $baseCode = ${formatConverterValue(item.unitRateFromBase)} ${item.info.code}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.info.isCommodity || item.unitRateFromBase < BigDecimal("0.01")) {
                    Text(
                        text = "1 ${item.info.code} = ${formatConverterValue(item.inverseRateToBase)} $baseCode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatConverterValue(item.convertedAmount),
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = if (compact) 82.dp else 160.dp)
                )
                Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeBg,
                    modifier = Modifier.clickable { onOpenChart() }
                ) {
                    Text(
                        text = changeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = badgeText,
                        modifier = Modifier.padding(horizontal = if (compact) 5.dp else 8.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountSelectionDialog(
    topItem: ConverterListItem,
    previewTargets: List<ConverterListItem>,
    initialAmountStr: String,
    onDismiss: () -> Unit,
    onConfirmAmount: (String) -> Unit
) {
    val compact = LocalCompactUi.current
    var amountText by remember { mutableStateOf(initialAmountStr) }
    val presetAmounts = remember {
        listOf("1", "5", "10", "25", "50", "100", "250", "500", "1000", "5000")
    }
    val parsedAmount = amountText.trim().replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
    val apply = {
        onConfirmAmount(amountText.ifBlank { "1" })
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Set ${topItem.info.code} amount",
        confirmLabel = "Apply",
        dismissLabel = "Cancel",
        confirmEnabled = parsedAmount > BigDecimal.ZERO,
        onConfirm = apply,
        contentMaxHeightFraction = if (compact) 0.62f else 0.58f
    ) {
        AppTextField(
            value = amountText,
            onValueChange = { value ->
                val cleaned = value.filter { it.isDigit() || it == '.' || it == '-' }
                if (cleaned.count { it == '.' } <= 1) amountText = cleaned
            },
            label = "Amount in ${topItem.info.code} (${topItem.info.symbol})",
            keyboardType = KeyboardType.Decimal,
            allowNegative = false,
            placeholder = "1"
        )

        // Preset chips; 4 per row keeps them tappable at 240dp wide.
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
            presetAmounts.chunked(if (compact) 5 else 5).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val isSelected = amountText.trim() == preset
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .clickable { amountText = preset },
                            shape = RoundedCornerShape(5.dp),
                            color = if (isSelected) ConverterBlue else MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 1.dp
                        ) {
                            Text(
                                text = preset,
                                modifier = Modifier.padding(vertical = if (compact) 4.dp else 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)
        ) {
            AppOutlinedButton(
                onClick = {
                    val v = parsedAmount.divide(BigDecimal.TEN, 4, RoundingMode.HALF_EVEN).stripTrailingZeros()
                    amountText = v.toPlainString()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) { Text("÷10", maxLines = 1) }
            AppOutlinedButton(
                onClick = {
                    amountText = parsedAmount.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) { Text("×10", maxLines = 1) }
            AppOutlinedButton(
                onClick = {
                    amountText = parsedAmount.add(BigDecimal.TEN).stripTrailingZeros().toPlainString()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) { Text("+10", maxLines = 1) }
            AppOutlinedButton(
                onClick = {
                    amountText = parsedAmount.add(BigDecimal(100)).stripTrailingZeros().toPlainString()
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) { Text("+100", maxLines = 1) }
        }

        if (previewTargets.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 6.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    previewTargets.take(if (compact) 2 else 3).forEach { target ->
                        val previewVal = parsedAmount.multiply(target.unitRateFromBase)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${target.info.flag} ${target.info.code}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatConverterValue(previewVal),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConverterChartsContent(
    uiState: ConverterUiState,
    onSwapPair: () -> Unit,
    onPickBase: () -> Unit,
    onPickTarget: () -> Unit,
    onSelectTargetQuick: (String) -> Unit,
    onSelectTimeframe: (ChartTimeframe) -> Unit
) {
    val baseInfo = remember(uiState.chartBaseCode) { CurrencyProvider.getInfo(uiState.chartBaseCode) }
    val targetInfo = remember(uiState.chartTargetCode) { CurrencyProvider.getInfo(uiState.chartTargetCode) }
    val series = uiState.chartSeries
    val points = series?.points ?: emptyList()

    val firstRate = points.firstOrNull()?.rate ?: 1.0
    val latestRate = points.lastOrNull()?.rate ?: 1.0
    val delta = latestRate - firstRate
    val pctChange = if (firstRate > 0.0) (delta / firstRate) * 100.0 else 0.0
    val isPositive = pctChange >= 0.0

    val minRate = points.minOfOrNull { it.rate } ?: latestRate
    val maxRate = points.maxOfOrNull { it.rate } ?: latestRate
    val avgRate = if (points.isNotEmpty()) points.sumOf { it.rate } / points.size else latestRate

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pair selector card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Base currency selector pill
                    OutlinedButton(
                        onClick = onPickBase,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${baseInfo.flag} ${baseInfo.code}", fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onSwapPair) {
                        Text("⇄", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ConverterBlue)
                    }

                    // Target currency selector pill
                    OutlinedButton(
                        onClick = onPickTarget,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${targetInfo.flag} ${targetInfo.code}", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Quick target switcher chips from user's converter list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    uiState.selectedCodes.filter { it != uiState.chartBaseCode }.forEach { code ->
                        val info = CurrencyProvider.getInfo(code)
                        val selected = code == uiState.chartTargetCode
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTargetQuick(code) },
                            label = { Text("${info.flag} ${info.code}") }
                        )
                    }
                }
            }
        }

        // Rate & interactive chart card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                var scrubbedIndex by remember(series) { mutableStateOf<Int?>(null) }
                val activePoint = scrubbedIndex?.let { points.getOrNull(it) } ?: points.lastOrNull()
                val displayRate = activePoint?.rate ?: latestRate

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "1 ${baseInfo.code} = ${formatConverterValue(BigDecimal.valueOf(displayRate))} ${targetInfo.code}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.baseAmount.compareTo(BigDecimal.ONE) != 0) {
                            val totalConverted = uiState.baseAmount.multiply(BigDecimal.valueOf(displayRate))
                            Text(
                                text = "${formatConverterValue(uiState.baseAmount)} ${baseInfo.code} = ${formatConverterValue(totalConverted)} ${targetInfo.code}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ConverterBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = activePoint?.dateLabel ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isPositive) PositiveGreenBg else NegativeRedBg
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "%s%.2f%% (%s)",
                                if (isPositive) "+" else "",
                                pctChange,
                                uiState.chartTimeframe.label
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) PositiveGreenText else NegativeRedText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Timeframe selector bar (1W, 1M, 3M, 6M, 1Y)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChartTimeframe.values().forEach { tf ->
                        val isSelected = uiState.chartTimeframe == tf
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectTimeframe(tf) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) ConverterBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = tf.label,
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (uiState.isChartLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (points.size >= 2) {
                    InteractiveRateLineChart(
                        points = points,
                        isPositive = isPositive,
                        scrubbedIndex = scrubbedIndex,
                        onScrubIndexChanged = { scrubbedIndex = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    )

                    Spacer(Modifier.height(6.dp))

                    // X-axis date labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = points.first().dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = points[points.size / 2].dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = points.last().dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Period Statistics (High / Low / Average)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${uiState.chartTimeframe.label} Statistics (${baseInfo.code}/${targetInfo.code})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatColumn("High", formatConverterValue(BigDecimal.valueOf(maxRate)))
                    StatColumn("Low", formatConverterValue(BigDecimal.valueOf(minRate)))
                    StatColumn("Average", formatConverterValue(BigDecimal.valueOf(avgRate)))
                }
            }
        }

        // Multi-Currency Relative Change Overview
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Watchlist Comparison (Base: ${uiState.selectedCodes.firstOrNull() ?: "USD"})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                uiState.items.drop(1).forEach { item ->
                    val pos = item.changePercent24h >= 0
                    val barFraction = (abs(item.changePercent24h).toFloat() / 2.0f).coerceIn(0.08f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectTargetQuick(item.info.code) }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.info.flag} ${item.info.code} (${formatConverterValue(item.convertedAmount)})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format(Locale.US, "%s%.2f%%", if (pos) "+" else "", item.changePercent24h),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (pos) PositiveGreenText else NegativeRedText
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(barFraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (pos) PositiveGreenText else NegativeRedText)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InteractiveRateLineChart(
    points: List<com.spendingtracker.app.data.repository.HistoricalRatePoint>,
    isPositive: Boolean,
    scrubbedIndex: Int?,
    onScrubIndexChanged: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) Color(0xFF1E8E3E) else Color(0xFF2563EB)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    val minVal = points.minOf { it.rate }
    val maxVal = points.maxOf { it.rate }
    val span = (maxVal - minVal).takeIf { it > 0.0 } ?: (maxVal * 0.01).takeIf { it > 0.0 } ?: 1.0

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures(
                    onPress = { offset ->
                        val idx = ((offset.x / size.width) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        onScrubIndexChanged(idx)
                        tryAwaitRelease()
                        onScrubIndexChanged(null)
                    }
                )
            }
            .pointerInput(points) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val idx = ((offset.x / size.width) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        onScrubIndexChanged(idx)
                    },
                    onDragEnd = { onScrubIndexChanged(null) },
                    onDragCancel = { onScrubIndexChanged(null) },
                    onDrag = { change, _ ->
                        val idx = ((change.position.x / size.width) * (points.size - 1))
                            .roundToInt()
                            .coerceIn(0, points.lastIndex)
                        onScrubIndexChanged(idx)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val padTop = 12.dp.toPx()
        val padBottom = 12.dp.toPx()
        val chartH = (h - padTop - padBottom).coerceAtLeast(1f)

        // Horizontal dashed grid lines
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        for (i in 0..3) {
            val y = padTop + (chartH * i / 3f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        fun pointOffset(index: Int): Offset {
            val x = (index.toFloat() / (points.size - 1).coerceAtLeast(1)) * w
            val normalizedY = ((points[index].rate - minVal) / span).toFloat().coerceIn(0f, 1f)
            val y = padTop + chartH * (1f - normalizedY)
            return Offset(x, y)
        }

        val linePath = Path()
        val fillPath = Path()

        points.indices.forEach { i ->
            val pt = pointOffset(i)
            if (i == 0) {
                linePath.moveTo(pt.x, pt.y)
                fillPath.moveTo(pt.x, h - padBottom)
                fillPath.lineTo(pt.x, pt.y)
            } else {
                linePath.lineTo(pt.x, pt.y)
                fillPath.lineTo(pt.x, pt.y)
            }
        }
        fillPath.lineTo(w, h - padBottom)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.0f)),
                startY = padTop,
                endY = h - padBottom
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Highlight scrubbed point or latest point
        val highlightIdx = scrubbedIndex ?: points.lastIndex
        val highlightOffset = pointOffset(highlightIdx)

        if (scrubbedIndex != null) {
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = Offset(highlightOffset.x, padTop),
                end = Offset(highlightOffset.x, h - padBottom),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = highlightOffset
        )
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = highlightOffset
        )
    }
}

@Composable
private fun AddCurrencyOrCommodityDialog(
    selectedCodes: List<String>,
    onToggleCode: (String) -> Unit,
    onMakeTop: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val compact = LocalCompactUi.current
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("ALL") } // ALL, CURRENCIES, COMMODITIES
    val filteredItems = remember(searchQuery, filterTab) {
        CurrencyProvider.searchAll(searchQuery, filterTab)
    }
    val maxList = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp *
        if (compact) 0.46f else 0.5f).dp

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Currencies & commodities",
        confirmLabel = "Done",
        dismissLabel = "",
        onConfirm = onDismiss,
        contentScrollable = false
    ) {
        AppTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search code or name…",
            label = "Search"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "ALL" to "All",
                "CURRENCIES" to "Currencies",
                "COMMODITIES" to "Commodities"
            ).forEach { (key, label) ->
                AppFilterChip(
                    selected = filterTab == key,
                    onClick = { filterTab = key },
                    label = label
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxList),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(filteredItems, key = { _, info -> info.code }) { _, info ->
                val isSelected = info.code in selectedCodes
                val isTop = selectedCodes.firstOrNull() == info.code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .clickable { onToggleCode(info.code) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(info.flag, fontSize = if (compact) 15.sp else 22.sp)
                    Spacer(Modifier.width(5.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = info.code,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (info.isCommodity) {
                                Spacer(Modifier.width(4.dp))
                                Text("◆", style = MaterialTheme.typography.labelSmall, color = ConverterBlue)
                            }
                            if (isTop) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "TOP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PositiveGreenText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = info.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Top",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ConverterBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onMakeTop(info.code) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    )
                    Text(
                        text = if (isSelected) "✓" else "＋",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) PositiveGreenText else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleCurrencyCommodityPickerDialog(
    title: String,
    currentCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val compact = LocalCompactUi.current
    var searchQuery by remember { mutableStateOf("") }
    val items = remember(searchQuery) { CurrencyProvider.searchAll(searchQuery, "ALL") }
    val maxList = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp *
        if (compact) 0.56f else 0.55f).dp

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmLabel = null,
        dismissLabel = "Close",
        onConfirm = null,
        contentScrollable = false
    ) {
        AppTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search currency or commodity…",
            label = "Search"
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxList),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(items, key = { _, info -> info.code }) { _, info ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (info.code == currentCode) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(info.code) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(info.flag, fontSize = if (compact) 14.sp else 20.sp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = info.code,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

internal fun formatConverterValue(value: BigDecimal): String {
    val absVal = value.abs()
    return when {
        absVal.compareTo(BigDecimal.ZERO) == 0 -> "0.0000"
        absVal < BigDecimal("0.001") -> DecimalFormat("0.000000").format(value)
        absVal < BigDecimal("100") -> DecimalFormat("#,##0.0000").format(value)
        else -> DecimalFormat("#,##0.00").format(value)
    }
}

@Composable
private fun ConverterActionButton(
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .clickable(
                interactionSource = source,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
