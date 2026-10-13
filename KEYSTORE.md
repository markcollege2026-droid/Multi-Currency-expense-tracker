# Keystore Documentation

## Release Keystore

Generated with:

```bash
keytool -genkeypair -v -keystore release.keystore -storepass spendingtracker -alias spendingtracker -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Spending Tracker, OU=Finance, O=SpendingTracker, L=City, S=State, C=US" -storetype JKS
```

- **File**: `release.keystore` (project root)
- **Store password**: `spendingtracker`
- **Key alias**: `spendingtracker`
- **Key password**: `spendingtracker`
- **Algorithm**: RSA 2048
- **Validity**: 10000 days
- **DN**: CN=Spending Tracker, OU=Finance, O=SpendingTracker, L=City, S=State, C=US

### Verify

```bash
keytool -list -v -keystore release.keystore -storepass spendingtracker
```

### Signing

Release APK is signed with:

```bash
apksigner sign --ks release.keystore --ks-pass pass:spendingtracker --key-pass pass:spendingtracker --out app/build/outputs/apk/release/app-release.apk app-aligned.apk
```

## Debug Keystore

Generated with:

```bash
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

- **File**: `debug.keystore`
- **Store password**: `android`
- **Alias**: `androiddebugkey`
- **Key password**: `android`

Debug APK uses this keystore automatically via gradlew.

## Security Note

In production, keep release.keystore private, use stronger passwords, and store in secure location (not in VCS). For this demo, it's included for reproducibility.
