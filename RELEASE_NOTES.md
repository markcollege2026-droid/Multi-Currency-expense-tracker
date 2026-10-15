# Spending Tracker 1.8.1 — File backups and lightweight encryption

## Save and restore
Open **Settings → Data → Backup files**:
- **Save JSON backup to file**: choose an actual filename/folder using Android's file picker (Downloads, device storage or a supported cloud document provider).
- **Save CSV report to file**: saves a transaction report. CSV is not a restorable backup.
- **Save encrypted backup**: enter and confirm a password, then choose the save location. Produces a portable `.stbk` file.
- **Restore from backup file**: select a JSON or encrypted `.stbk` backup. Review the replace-all confirmation; encrypted files additionally require their password.

There is no hidden export folder. Android's document provider controls the actual file location; the app confirms the document name only after writing and closing it successfully. Existing share-as-text options remain, but their messages now distinguish prepared text from a saved file.

## Encryption
Uses built-in Java/Android crypto only: **AES-128-GCM**, with a fresh random 16-byte salt and 12-byte nonce per backup, and **PBKDF2-HMAC-SHA256 (600,000 iterations)** deriving the key from the password. No extra crypto dependency, no custom cipher, no device-bound key, and only 49 bytes of format overhead. The deliberately slower password derivation resists guessing; encryption/file work runs off the UI thread.

AES-GCM authenticates the header and contents. Wrong passwords, changed contents, unsupported versions and truncated files are rejected before importing data. Passwords are not persisted or logged. Use a long passphrase; the UI requires at least 8 characters and confirmation. **Forgotten passwords cannot be recovered.** The encryption format is versioned independently of the existing JSON backup format.

Encrypted backup creation does not write a plaintext temporary file. Decrypted data is held in memory during restore. On a JVM, clearing byte/character arrays is best-effort memory hygiene, not a guarantee that all runtime copies are erased.

## Data safety and fixes
- Export reads from a consistent Room transaction snapshot.
- Restore prompts before replacing data and uses the existing atomic import transaction. Invalid data, wrong passwords and failed imports leave current data unchanged.
- Restore now replaces categories and the exchange-rate cache too, rather than leaving unrelated old entries behind; these changes participate in the same rollback transaction.
- Files are bounded to 20 MiB to prevent unbounded memory consumption.
- Save/read/restore failures are displayed instead of being silently ignored. Interrupted or failed writes may leave an incomplete document; delete that document and save again.
- Busy indicators prevent duplicate backup operations. Cancelling the file picker does not perform an export or restore.

## Compatibility / migrations
Same application ID and release signing key. Version code **7**, Android **8+** (minSdk 26), targetSdk 35. Install `app-release.apk` over an existing release-signed app without uninstalling. The database stays at **version 4** and JSON backups stay at **schema 4**: **no new database migration is needed**. Existing plain JSON backups remain importable. Encrypted `.stbk` backups require this version or later; older app versions cannot decrypt them.

## Testing
Unit tests cover encryption round-trip/Unicode, random salt/nonce, wrong passwords, tampering, truncation, unsupported versions and weak-password rejection. Room/Robolectric tests save actual JSON and encrypted files, restore account balances, verify wrong-password/no-data-loss behavior, and reject empty or invalid backups. GitHub Actions gates APK/AAB publication on the full test suite, lint and compatibility checks against v1.8.0.

Android's interactive file-picker/provider UI still requires physical-device or emulator smoke testing; local file tests do not simulate every cloud provider or interrupted device write.
