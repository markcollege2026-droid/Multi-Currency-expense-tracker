package com.spendingtracker.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exports: ExportImportManager
) {
    enum class Format { JSON, CSV, ENCRYPTED }
    companion object { const val MAX_BACKUP_BYTES = 20 * 1024 * 1024 }

    /** Caller runs on IO; only the chosen document URI is written, never a hidden plaintext cache. */
    suspend fun save(uri: Uri, format: Format, password: CharArray? = null): String {
        val plaintext = (if (format == Format.CSV) exports.exportTransactionsCsv() else exports.exportAll()).toByteArray(Charsets.UTF_8)
        try {
            require(plaintext.size <= MAX_BACKUP_BYTES - 49) { "Backup exceeds the 20 MiB limit." }
            val bytes = if (format == Format.ENCRYPTED) BackupEncryption.encrypt(plaintext, requireNotNull(password)) else plaintext
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open the selected location for writing.")
                stream.use { it.write(bytes); it.flush() }
            } finally { if (bytes !== plaintext) bytes.fill(0) }
            return displayName(uri)
        } finally { plaintext.fill(0) }
    }

    fun read(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open this backup.")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BACKUP_BYTES) { "Backup exceeds the 20 MiB limit." }
                output.write(buffer, 0, count)
            }
            require(total > 0) { "The selected backup is empty." }
            output.toByteArray()
        }
    }

    suspend fun restore(bytes: ByteArray, password: CharArray? = null) {
        val plaintext = if (BackupEncryption.isEncrypted(bytes)) BackupEncryption.decrypt(bytes, requireNotNull(password) { "Enter the backup password." }) else bytes
        try {
            val json = plaintext.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            exports.importAll(json).getOrThrow() // Existing replace-all transaction rolls back any invalid data.
        } finally { if (plaintext !== bytes) plaintext.fill(0) }
    }

    fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "Selected document"
    } catch (_: Exception) { uri.lastPathSegment ?: "Selected document" }
}
