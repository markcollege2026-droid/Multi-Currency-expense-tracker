package com.spendingtracker.app.util

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable password backup: STBK/version + salt + nonce + AES-GCM ciphertext/tag. No dependencies. */
object BackupEncryption {
    private val magic = byteArrayOf(83, 84, 66, 75) // STBK
    private const val VERSION: Byte = 1
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val HEADER_SIZE = 5 + SALT_SIZE + NONCE_SIZE
    private const val ITERATIONS = 600_000

    fun isEncrypted(bytes: ByteArray): Boolean = bytes.size >= magic.size && magic.indices.all { bytes[it] == magic[it] }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 8) { "Use a password of at least 8 characters (a long passphrase is better)." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val header = magic + byteArrayOf(VERSION) + salt + nonce
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plaintext)
        } finally { key.fill(0) }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        require(isEncrypted(bytes)) { "Not an encrypted Spending Tracker backup." }
        require(bytes.size >= HEADER_SIZE + 16) { "Encrypted backup is truncated or damaged." }
        require(bytes[4] == VERSION) { "Unsupported encrypted backup version." }
        val salt = bytes.copyOfRange(5, 5 + SALT_SIZE)
        val nonce = bytes.copyOfRange(5 + SALT_SIZE, HEADER_SIZE)
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(bytes, 0, HEADER_SIZE)
            return cipher.doFinal(bytes, HEADER_SIZE, bytes.size - HEADER_SIZE)
        } catch (_: GeneralSecurityException) {
            throw IllegalArgumentException("Incorrect password or damaged encrypted backup. Existing data was not changed.")
        } finally { key.fill(0) }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 128)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
