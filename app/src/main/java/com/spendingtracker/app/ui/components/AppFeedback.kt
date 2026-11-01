package com.spendingtracker.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.ui.compact.LocalCompactUi

/**
 * Shows [message] as a toast exactly once, then reports back so the screen can
 * clear it.
 *
 * The screen owns the flag (a `toast: String?` in its UI state), which keeps the
 * one-shot semantics out of the toast itself: a recomposition cannot show it
 * twice, and a configuration change cannot repeat it. Toasts are used for results
 * that arrive too fast to deserve a spinner, and for actions that are refused
 * before any work starts.
 *
 * Cost: one `LaunchedEffect` and one platform toast per message. No timers, no
 * polling, nothing on the work path - so it cannot slow an operation down.
 */
@Composable
fun AppToast(message: String?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            onShown()
        }
    }
}

/**
 * Inline "working…" row for pages whose content must stay on screen.
 *
 * Deliberately not a blocking overlay: the state that flips this row on is set
 * before the work is launched and cleared in a `finally`, so the row costs one
 * recomposition and never delays the operation. Screens use it where a long task
 * (a whole-ledger export, a deletion cascade, a network rate refresh) would
 * otherwise look like a frozen page.
 */
@Composable
fun AppBusyRow(label: String, modifier: Modifier = Modifier) {
    val compact = LocalCompactUi.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
