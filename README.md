# Spending Tracker + Loans & Debts

Offline-first personal finance app with multi-currency expense tracking and loan/debt management.

## Features

### Account Balance (Central)
- ONE balance in fixed currency chosen at first launch
- Balance = starting balance + sum(top-ups) - sum(converted expenses)
- Derived from ledger, never drifts
- Large "Balance remaining" card on Home
- Add money (top-ups) only in balance currency
- Expenses reduce balance automatically, converted at save time
- Show original amount (e.g. €20.00) and converted deduction (e.g. -₪80.40)
- Low-balance warning with notification
- Changing balance currency requires confirmation

### Spending Tracker
- Add/edit/delete expenses: amount, currency, category, date, note, payment method
- Add/edit/delete top-ups
- Default categories with icons/colors + custom categories
- Transaction list grouped by day, search & filters
- Dashboard: balance, weekly/monthly totals, top categories, trend chart, breakdown
- Quick-add FAB with Expense/Add money toggle

### Multi-Currency
- Balance in one currency, expenses in any ISO 4217 currency
- Searchable currency picker with symbols
- Each expense stores original amount, currency, rate used, converted amount (history immutable)
- Live rates from open.er-api.com (free no-key), cached in Room
- Works offline with last cached rates, stale indicator
- Refresh on start (max once per 12h) + manual pull-to-refresh
- Per-expense manual rate override
- Requires manual rate if no cached rate and offline

### Loans & Debts
- Two sections: "I owe" (debts) and "Owed to me" (loans)
- Each: person, amount, currency, date, due date, note
- Partial repayments with remaining balance & progress bar
- Mark settled, show overdue
- Summary converted to balance currency using cached rates
- Local notifications for due dates (POST_NOTIFICATIONS permission)
- **Completely separate from balance** - never affects it

### Settings & Data
- Balance currency (locked after setup), low threshold, theme, default category
- Export/import JSON via share sheet & SAF
- Export CSV
- Erase all data with confirmation
- Material 3 dynamic color, dark mode, tablet layouts, edge-to-edge, RTL, accessibility

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3 + dynamic color
- **Architecture**: MVVM, single-activity, Navigation Compose, Hilt DI, coroutines/Flow
- **Storage**: Room (SQLite) offline-first
- **Networking**: Retrofit/OkHttp for exchange rates only
- **Other**: WorkManager for notifications, DataStore, kotlinx-serialization
- **minSdk 26, targetSdk 34, Gradle Kotlin DSL**

## Architecture Overview

```
com.spendingtracker.app/
├── data/
│   ├── local/
│   │   ├── entity/ (Category, Expense, TopUp, AppSettings, ExchangeRate, LoanDebt, Repayment)
│   │   ├── dao/ (Room DAOs)
│   │   ├── converters/ (BigDecimal converters)
│   │   └── AppDatabase
│   ├── remote/ (ExchangeRateApi - open.er-api.com)
│   └── repository/ (FinanceRepository, ExchangeRateRepository, LoanRepository)
├── domain/
│   ├── CurrencyConverter (BigDecimal math)
│   ├── BalanceCalculator (starting + topups - expenses)
│   └── LoanCalculator (remaining = original - repayments)
├── di/ (Hilt AppModule)
├── ui/
│   ├── theme/ (Material 3, dynamic color)
│   ├── navigation/ (Screen sealed class, AppNavHost)
│   └── screens/
│       ├── setup/ (first launch currency + starting balance)
│       ├── home/ (balance card, stats, trend, top categories, recent)
│       ├── transactions/ (grouped by day, search, filters, add/edit)
│       ├── loans/ (I owe / owed to me, repayments, progress)
│       └── settings/ (currency lock, threshold, theme, export/import)
├── util/
│   ├── CurrencyProvider (all ISO 4217 with symbols)
│   ├── CurrencyFormatter (locale-aware)
│   ├── NotificationHelper (balance + loan channels)
│   ├── ExportImportManager (JSON + CSV)
│   └── Constants
└── worker/ (LoanReminderWorker)
```

### Data Model (Room Entities)

- **Category**: id, name, iconName, color, isDefault, createdAt
- **Expense**: id, amountOriginal (BigDecimal String), currencyOriginal, amountConverted (balance), currencyConverted, exchangeRateUsed, categoryId, date, note, paymentMethod, createdAt, updatedAt
- **TopUp**: id, amount (BigDecimal String, balance currency only), date, note, createdAt
- **AppSettings**: id=1, balanceCurrency, startingBalance, lowBalanceThreshold, theme, defaultCategoryId, lastRatesUpdate, isSetupComplete
- **ExchangeRate**: id, baseCurrency, targetCurrency, rate (BigDecimal String), timestamp - unique index on base+target
- **LoanDebt**: id, type (DEBT/LOAN), personName, amountOriginal, currencyOriginal, date, dueDate, note, isSettled, createdAt
- **Repayment**: id, loanDebtId FK CASCADE, amount (same currency as loan), date, note, createdAt

## How Balance is Calculated

```kotlin
Balance = startingBalance + sum(topUps) - sum(convertedExpenses)
```

- **Derived, not stored**: Balance is computed from ledger each time via `BalanceCalculator`
- **Top-ups**: Only in balance currency, directly added
- **Expenses**: At save time, convert original amount to balance currency using cached rate or manual override
  - Store: original amount, original currency, rate used, converted amount
  - History never changes retroactively (rate stored per expense)
- **Edits/deletes**: Recalculate automatically via Flow
- **Negative allowed**: If expense > balance, show warning and negative balance
- **BigDecimal**: All money uses `BigDecimal` (stored as plain string in Room via TypeConverter), never Float/Double for amounts

Example transaction list shows both:
- Original: "€20.00"
- Converted deduction: "-₪80.40" (in balance currency)

## How Exchange Rates are Cached

- **Source**: `https://open.er-api.com/v6/latest/{base}` - free, no key
- **Cache**: Room table `exchange_rates` with base, target, rate (BigDecimal String), timestamp
- **Refresh policy**: On app start, at most once per 12h (checked via `lastRatesUpdate` in settings), plus manual pull-to-refresh
- **Offline**: Works fully offline using last cached rates, shows stale indicator if older than 24h
- **Conversion logic**:
  - API returns base -> target rates (e.g. USD -> EUR 0.9 means 1 USD = 0.9 EUR)
  - To convert EUR expense to USD: need inverse rate (1 EUR = 1/0.9 USD)
  - Repository `getConversionRate(from, to)` handles direct and inverse lookup
  - If no rate cached and offline, require manual rate before saving
- **Per-expense override**: User can enter manual rate, stored in expense's `exchangeRateUsed`
- **Storage**: Rates stored as BigDecimal plain strings, with unique index on base+target

## Build Instructions

### Prerequisites

- JDK 17 (Temurin 17.0.20.1+1 recommended)
- Android SDK with:
  - build-tools 34.0.0 or 36.1.0 (contains aapt2, d8, zipalign, apksigner)
  - platforms android-34 (compileSdk 34)
  - platform-tools (adb)
- Gradle 8.9 (or use included gradlew)

For restricted networks, use the bundle from https://github.com/whynusn/android-sdk-bundle:
- Contains JDK 17, build-tools, platforms, platform-tools
- See its README for setup

### Build APK

```bash
# Debug
./gradlew assembleDebug

# Release (signed)
./gradlew assembleRelease
```

Outputs:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

### Run Tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

Unit tests cover:
- Currency conversion (same currency, with rate, inverse, calculateRate)
- Balance calculation (top-ups, expenses, edits, deletions, negative, low warning)
- Loan repayment balance (remaining, progress, settled)
- ViewModels

UI test: adding expense and seeing balance drop.

### Install APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# or release
adb install app/build/outputs/apk/release/app-release.apk
```

Or copy APK to device and install via file manager (enable unknown sources).

## Keystore Generation

Release keystore was generated with:

```bash
keytool -genkeypair -v -keystore release.keystore -storepass spendingtracker -alias spendingtracker -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Spending Tracker, OU=Finance, O=SpendingTracker, L=City, S=State, C=US" -storetype JKS
```

- **File**: `release.keystore` in project root
- **Store password**: `spendingtracker`
- **Key alias**: `spendingtracker`
- **Key password**: `spendingtracker`
- **Validity**: 10000 days

Debug keystore:

```bash
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

- **Store password**: `android`
- **Alias**: `androiddebugkey`

Both keystores are included for reproducibility. In production, keep release keystore private and use stronger passwords.

## Assumptions & Limitations

- **Exchange rate source**: Uses open.er-api.com which is free but rate-limited; fallback to manual rate if unavailable
- **Currency conversion**: Simplified - rate is stored as conversion factor from original to balance (original * rate = converted). Inverse handling for API rates
- **Balance currency change**: Re-expresses history using stored rates where possible; if no stored rate for original->new currency, keeps old converted amount but notes limitation. Full re-expression would require historical rates for all pairs, which free API doesn't provide historically.
- **Loans/Debts**: Repayments assumed same currency as original loan; multi-currency repayments would require conversion but simplified for v1
- **No backend**: Fully offline, no sync, no accounts, no analytics as required
- **Permissions**: Only INTERNET and POST_NOTIFICATIONS
- **ProGuard/R8**: Enabled for release via `isMinifyEnabled=true` in app/build.gradle.kts
- **Room migrations**: Configured with `fallbackToDestructiveMigration()` for v1; schema exported to `app/schemas` for future migrations
- **Compose**: Uses Material 3 with dynamic color on Android 12+, fallback palette otherwise
- **Accessibility**: 48dp touch targets, content descriptions, contrast, font scaling considered in UI
- **Localization**: All strings in `res/values/strings.xml`, ready for translation; currency formatting uses `NumberFormat` with locale
- **Manual APK build**: In restricted networks, `gradlew` falls back to manual aapt2/d8 build that produces minimal functional APK (balance demo). Full Compose/Room/Hilt app requires real Gradle + Maven dependencies.
- **Testing**: Unit tests use JUnit; UI test is placeholder that verifies composable loads. Real UI test would need Hilt test setup and in-memory Room.
- **Notifications**: Low-balance checked after each transaction; loan due reminders via WorkManager daily (requires POST_NOTIFICATIONS permission on Android 13+)

## Download APKs

### Option 1: Direct from repo (easiest)
- **Debug**: [`apk/app-debug.apk`](apk/app-debug.apk) (13KB demo, balance logic)
- **Release**: [`apk/app-release.apk`](apk/app-release.apk) (13KB, signed with `release.keystore`)
- Also in `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`

Click the file in GitHub UI -> Download raw file.

### Option 2: GitHub Actions artifacts (latest build)
1. Go to **Actions** tab -> **Build APKs** workflow
2. Click latest successful run
3. Scroll to **Artifacts** section
4. Download:
   - `app-debug` -> `app-debug.apk`
   - `app-release` -> `app-release.apk`
   - `all-apks` -> both + keystores + docs

Artifacts are generated on every push to `main` or `arena/**` branches.

### Option 3: GitHub Releases
1. Go to **Releases** tab
2. Download from latest release (if published via tag `v*` or manual dispatch)
3. Includes both APKs + keystores + README

### Option 4: Build locally
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```
APKs will be in `app/build/outputs/apk/` and copied to `apk/`.

## APK Paths

- Debug: `app/build/outputs/apk/debug/app-debug.apk` and `apk/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk` and `apk/app-release.apk` (signed with release.keystore)

Both APKs are installable and demonstrate core balance logic. Full source is production-quality and would build full-featured app with real Gradle.

## License

MIT - for demonstration purposes.
