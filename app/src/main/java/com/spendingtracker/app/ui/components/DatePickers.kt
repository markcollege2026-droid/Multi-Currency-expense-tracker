package com.spendingtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.LocalHardwareKeypad
import com.spendingtracker.app.util.DateInput
import java.util.Calendar
import java.util.TimeZone

/**
 * Material date picker for normal screens. On the F21's 240x320dp window the
 * Material picker is wider/taller than the display, so a compact numeric date
 * dialog is used instead. Both return local end-of-day millis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerDialog(
    initialDateMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    if (LocalCompactUi.current) {
        CompactDatePickerDialog(initialDateMillis, onDismiss, onConfirm)
        return
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDateMillis ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { picked ->
                        onConfirm(endOfDayLocal(picked))
                    }
                    onDismiss()
                }
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun CompactDatePickerDialog(
    initialDateMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val initial = remember(initialDateMillis) {
        DateInput.partsFrom(initialDateMillis ?: System.currentTimeMillis())
    }
    var day by remember(initial) { mutableStateOf(initial.day) }
    var month by remember(initial) { mutableStateOf(initial.month) }
    var year by remember(initial) { mutableStateOf(initial.year) }
    var error by remember { mutableStateOf<String?>(null) }
    val dayFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hardwareKeypad = LocalHardwareKeypad.current

    LaunchedEffect(Unit) {
        runCatching { dayFocus.requestFocus() }
        if (hardwareKeypad) keyboard?.hide()
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = "Choose date",
        confirmLabel = "Set date",
        onConfirm = {
            val millis = DateInput.endOfDayMillis(day, month, year)
            if (millis == null) {
                error = "Enter a valid day, month and year"
            } else {
                onConfirm(millis)
                onDismiss()
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppTextField(
                value = day,
                onValueChange = { day = it.take(2) },
                label = "Day",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                focusRequester = dayFocus
            )
            AppTextField(
                value = month,
                onValueChange = { month = it.take(2) },
                label = "Month",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            AppTextField(
                value = year,
                onValueChange = { year = it.take(4) },
                label = "Year",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1.35f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AppTextButton(onClick = {
                val today = DateInput.todayParts()
                day = today.day; month = today.month; year = today.year; error = null
            }, modifier = Modifier.weight(1f)) { Text("Today", maxLines = 1) }
            AppTextButton(onClick = {
                val parts = DateInput.partsFrom(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
                day = parts.day; month = parts.month; year = parts.year; error = null
            }, modifier = Modifier.weight(1f)) { Text("+7 days", maxLines = 1) }
            AppTextButton(onClick = {
                val calendar = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }
                val parts = DateInput.partsFrom(calendar.timeInMillis)
                day = parts.day; month = parts.month; year = parts.year; error = null
            }, modifier = Modifier.weight(1f)) { Text("+1 month", maxLines = 1) }
        }
        if (error != null) {
            Text(
                text = error!!,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * The Material picker returns UTC-midnight millis for the chosen calendar day.
 * Convert to the end of that same calendar day in the user's local timezone so
 * "due on the 20th" means due until 23:59 on the 20th locally.
 */
fun endOfDayLocal(pickedUtcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMillis }
    return Calendar.getInstance().apply {
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
