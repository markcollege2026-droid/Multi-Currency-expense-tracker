package com.spendingtracker.app.util

import org.junit.Assert.*
import org.junit.Test

class BackupEncryptionTest {
    private val password get() = "a long test passphrase".toCharArray()
    private val json = "{\"note\":\"שלום € ₪\",\"amount\":\"12.50\"}".toByteArray(Charsets.UTF_8)
    @Test fun encryptedRoundTripPreservesUtf8() {
        val encrypted = BackupEncryption.encrypt(json, password)
        assertTrue(BackupEncryption.isEncrypted(encrypted))
        assertArrayEquals(json, BackupEncryption.decrypt(encrypted, password))
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("12.50"))
    }
    @Test fun samePasswordAndDataUseDifferentRandomSaltAndNonce() {
        assertFalse(BackupEncryption.encrypt(json, password).contentEquals(BackupEncryption.encrypt(json, password)))
    }
    @Test fun wrongPasswordFailsAuthentication() {
        val encrypted = BackupEncryption.encrypt(json, password)
        rejected { BackupEncryption.decrypt(encrypted, "wrong password".toCharArray()) }
    }
    @Test fun changedCiphertextOrHeaderIsRejected() {
        val encrypted = BackupEncryption.encrypt(json, password)
        val altered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        rejected { BackupEncryption.decrypt(altered, password) }
        val saltChanged = encrypted.copyOf().also { it[5] = (it[5].toInt() xor 1).toByte() }
        rejected { BackupEncryption.decrypt(saltChanged, password) }
    }
    @Test fun truncatedAndUnknownVersionsAreRejected() {
        val encrypted = BackupEncryption.encrypt(json, password)
        rejected { BackupEncryption.decrypt(encrypted.copyOf(15), password) }
        rejected { BackupEncryption.decrypt(encrypted.copyOf().also { it[4] = 99 }, password) }
        assertFalse(BackupEncryption.isEncrypted(json))
    }
    @Test fun shortPasswordIsRejected() { rejected { BackupEncryption.encrypt(json, "123".toCharArray()) } }
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected validation error") } catch (_: IllegalArgumentException) { }
    }
}
