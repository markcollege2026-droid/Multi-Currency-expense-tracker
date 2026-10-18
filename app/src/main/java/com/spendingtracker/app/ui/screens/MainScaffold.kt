package com.spendingtracker.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.navigation.Screen
import com.spendingtracker.app.ui.screens.converter.ConverterScreen
import com.spendingtracker.app.ui.screens.home.HomeScreen
import com.spendingtracker.app.ui.screens.loans.LoansScreen
import com.spendingtracker.app.ui.screens.settings.SettingsScreen
import com.spendingtracker.app.ui.screens.transactions.TransactionsScreen

data class BottomNavItem(
    val label: String,
    val compactLabel: String,
    val route: String,
    val icon: ImageVector
)

/**
 * State-based bottom navigation (no nested NavHost back stacks).
 *
 * The bar is hand-built so it can be 42dp high on the F21 instead of Material's
 * 80dp NavigationBar. Each item is selectable (D-pad focusable) and receives the
 * app-wide amber focus ring through LocalIndication.
 */
@Composable
fun MainScaffold(
    onNavigateToAddExpense: () -> Unit,
    onNavigateToAddTopUp: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onEditExpense: (Long) -> Unit,
    onEditTopUp: (Long) -> Unit,
    onAddLoan: () -> Unit,
    onEditLoan: (Long) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(Screen.Home.route) }
    val stateHolder = rememberSaveableStateHolder()
    val compact = LocalCompactUi.current
    val dims = LocalAppDimens.current
    val items = listOf(
        BottomNavItem("Home", "Home", Screen.Home.route, Icons.Default.Home),
        BottomNavItem("Transactions", "History", Screen.Transactions.route, Icons.Default.List),
        BottomNavItem("Converter", "Rates", Screen.Converter.route, Icons.Default.Star),
        BottomNavItem("Loans", "Loans", Screen.Loans.route, Icons.Default.Person),
        BottomNavItem("Settings", "Settings", Screen.Settings.route, Icons.Default.Settings)
    )

    BackHandler(enabled = selectedTab != Screen.Home.route) {
        selectedTab = Screen.Home.route
    }

    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(dims.navBarHeight)
                ) {
                    items.forEach { item ->
                        val selected = selectedTab == item.route
                        val source = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = selected,
                                    interactionSource = source,
                                    indication = LocalIndication.current,
                                    role = Role.Tab,
                                    onClick = { selectedTab = item.route }
                                )
                                .padding(vertical = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (compact) item.compactLabel else item.label,
                                fontSize = if (compact) 9.sp else 10.sp,
                                lineHeight = if (compact) 11.sp else 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (selectedTab) {
                Screen.Home.route -> stateHolder.SaveableStateProvider(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToAddExpense = onNavigateToAddExpense,
                        onNavigateToAddTopUp = onNavigateToAddTopUp,
                        onNavigateToRecurring = onNavigateToRecurring,
                        onNavigateToTransactions = { selectedTab = Screen.Transactions.route },
                        onNavigateToSettings = { selectedTab = Screen.Settings.route },
                        onNavigateToLoans = { selectedTab = Screen.Loans.route },
                        onEditExpense = onEditExpense,
                        onEditTopUp = onEditTopUp
                    )
                }
                Screen.Transactions.route -> stateHolder.SaveableStateProvider(Screen.Transactions.route) {
                    TransactionsScreen(
                        onEditExpense = onEditExpense,
                        onEditTopUp = onEditTopUp,
                        onAddExpense = onNavigateToAddExpense,
                        onAddTopUp = onNavigateToAddTopUp
                    )
                }
                Screen.Converter.route -> stateHolder.SaveableStateProvider(Screen.Converter.route) {
                    ConverterScreen()
                }
                Screen.Loans.route -> stateHolder.SaveableStateProvider(Screen.Loans.route) {
                    LoansScreen(
                        onAddLoan = onAddLoan,
                        onEditLoan = onEditLoan
                    )
                }
                Screen.Settings.route -> stateHolder.SaveableStateProvider(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
