# Keystore Documentation

## Release Keystore

- **File**: `release.keystore` (project root)
- **Key alias**: configured at build time (never hard-coded in source control)
- **Algorithm**: RSA 2048
- **DN**: CN=Spending Tracker, OU=Finance, O=SpendingTracker, L=City, S=State, C=US

The keystore file is kept in the repository so existing installs can keep
upgrading (same signing identity). The store password, key alias and key
password are **not** stored in source control.

### Providing signing credentials

`app/build.gradle.kts` reads, in priority order:

1. Environment variables:
   - `SPENDINGTRACKER_STORE_FILE` (path; defaults to `release.keystore`)
   - `SPENDINGTRACKER_STORE_PASSWORD`
   - `SPENDINGTRACKER_KEY_ALIAS`
   - `SPENDINGTRACKER_KEY_PASSWORD`
2. A `signing.properties` file in the repository root (git-ignored):

   ```properties
   releaseStoreFile=release.keystore
   releaseStorePassword=…
   releaseKeyAlias=…
   releaseKeyPassword=…
   ```

3. Gradle properties (`-PreleaseStorePassword=…`, or `~/.gradle/gradle.properties`).

When all values are present, `assembleRelease` and `bundleRelease` are signed
with the historic key. When they are absent, the release build still compiles
but the artifact is **unsigned** — debug builds never need signing secrets.
CI maps the GitHub Actions secrets `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` to the environment variables.

**Standing policy** (until explicitly changed — see the README's "Release
signing policy" section): every CI run recovers the historic credentials from
git history and signs the release APK with the historic key, uploading it as
the `signed-release-apk-legacy-key` run artifact.

### Verify

```bash
keytool -list -v -keystore release.keystore
```

(The keystore password is one of the values you supply at build time; it is
deliberately not printed in this document or in any log.)

### Signing

Releases are signed by Gradle via the `release` signing config; the CI
`verify_upgrade.sh` step additionally compares the APK signer certificate
against v1.8.0 to prove the signing identity has not silently changed.

## Debug Keystore

- **File**: `debug.keystore` (project root)
- **Store password**: `android`
- **Alias**: `androiddebugkey`
- **Key password**: `android`

This uses the conventional, publicly known Android debug credentials. Debug
keystores carry no security value and are safe to commit.

## Security implications of the previously committed release credentials

The release keystore password, alias and key password used to be hard-coded in
`app/build.gradle.kts` and in this document, and the keystore file itself is
committed. Those values remain recoverable from Git **history** — deleting them
from the latest commit does NOT remove them. Anyone who ever had read access to
the repository can sign artifacts that Android will accept as updates to this
app.

Recommended remediation (requires a coordinated upgrade, so it is not done
silently here):

1. Generate a new release keystore and publish an app update signed with BOTH
   the old and new keys (APK Signature Scheme v3 key rotation, or Play App
   Signing), so existing installs upgrade to the new identity.
2. Store the new keystore and its passwords ONLY in GitHub Actions secrets (or
   a hardware/cloud KMS); never in the repository.
3. After the rotation release is widely adopted, stop shipping the old key.
4. Treat the exposed password as permanently public: never reuse it anywhere.
