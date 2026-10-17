package com.spendingtracker.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.util.BackupFileManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupControls(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showPassword by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    DisposableEffect(Unit) { onDispose { pendingPassword?.fill('\u0000') } }
    val jsonSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.saveBackup(it, BackupFileManager.Format.JSON) }
    }
    val csvSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.saveBackup(it, BackupFileManager.Format.CSV) }
    }
    val encryptedSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri != null && password != null) viewModel.saveBackup(uri, BackupFileManager.Format.ENCRYPTED, password)
        else {
            password?.fill('\u0000')
            if (uri != null) viewModel.reportBackupError("Password was cleared when the screen was recreated. Try saving again; remove the incomplete file if one was created.")
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::openBackup) }
    fun filename(extension: String) = "spending-tracker-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.$extension"

    Text("Backup files", style = MaterialTheme.typography.titleMedium)
    Text("Choose a folder in Android's file picker (for example Downloads). The app does not save to a hidden export folder.")
    Button(enabled = !state.backupBusy, onClick = { jsonSave.launch(filename("json")) }, modifier = Modifier.fillMaxWidth()) { Text("Save JSON backup to file") }
    OutlinedButton(enabled = !state.backupBusy, onClick = { csvSave.launch(filename("csv")) }, modifier = Modifier.fillMaxWidth()) { Text("Save CSV report to file") }
    Button(enabled = !state.backupBusy, onClick = { showPassword = true }, modifier = Modifier.fillMaxWidth()) { Text("Save encrypted backup") }
    OutlinedButton(enabled = !state.backupBusy, onClick = { restore.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup file") }
    Text("Restore accepts JSON or encrypted .stbk backups. CSV is a report, not a restorable backup. Restoring replaces all current finance data.")
    if (state.backupBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Working… Keep the app open.") }

    if (showPassword) BackupPasswordDialog(
        title = "Encrypt backup", confirmation = true, busy = false, error = null,
        description = "Use a long password you can remember. The password is not saved, and a forgotten password cannot be recovered. Encryption uses Android's built-in AES-GCM; no extra libraries.",
        onDismiss = { showPassword = false },
        onConfirm = { password ->
            pendingPassword?.fill('\u0000'); pendingPassword = password
            showPassword = false
            encryptedSave.launch(filename("stbk"))
        }
    )
    state.restoreEncrypted?.let { encrypted ->
        BackupPasswordDialog(
            title = "Restore ${state.backupDocumentName ?: "backup"}", confirmation = false,
            requirePassword = encrypted, busy = state.backupBusy, error = state.error,
            description = "This replaces all current accounts, transactions, loans and settings. Save a backup of current data first. ${if (encrypted) "Enter the password used to encrypt this file." else "This is an unencrypted JSON backup."}",
            onDismiss = viewModel::cancelRestore,
            onConfirm = { password -> if (encrypted) viewModel.restoreBackup(password) else { password.fill('\u0000'); viewModel.restoreBackup() } }
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String, description: String, confirmation: Boolean, busy: Boolean, error: String?,
    requirePassword: Boolean = true, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit
) {
    // Deliberately not rememberSaveable: passwords never enter saved instance state.
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = !requirePassword || (password.isNotEmpty() && (!confirmation || (password.length >= 8 && password == repeat)))
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(description)
            if (requirePassword) {
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                if (confirmation) {
                    OutlinedTextField(repeat, { repeat = it }, label = { Text("Confirm password") }, enabled = !busy,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                    Text("At least 8 characters; longer is safer.")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) CircularProgressIndicator()
        }
    }, confirmButton = {
        TextButton(enabled = valid && !busy, onClick = {
            val chars = password.toCharArray(); password = ""; repeat = ""; onConfirm(chars)
        }) { Text(if (confirmation) "Choose save location" else "Replace data and restore") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = { password = ""; repeat = ""; onDismiss() }) { Text("Cancel") } })
}
