# Spending Tracker + Loans & Debts

Offline-first personal finance app with multi-currency expense tracking and loan/debt management.

## UI and design system (Ledger)

The interface is built on a single design system, **Ledger**: warm bone paper,
one forest accent, amber reserved for warnings and the hardware focus ring, hairline
cards instead of shadows, and money always in tabular figures. Light and dark themes
are both defined in code (Material You dynamic colour is available but off by
default), and the compact Qin F21 layout shares the same tokens at a smaller scale.

- Design source of truth: [`.superdesign/design-system.md`](.superdesign/design-system.md)
- Screen mockups (390×844, self-contained HTML): [`.superdesign/design_iterations/`](.superdesign/design_iterations/)
- Component library: `ui/components/AppCards.kt` (`AppCard`, `AppSectionCard`,
  `AppStatTile`, `AppAvatar`, `AppListRow`) and `ui/components/CompactControls.kt`
- Tokens: `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Shapes.kt`, `ui/theme/Theme.kt`
- How the design was produced and how each screen maps to code:
  [`.superdesign/README.md`](.superdesign/README.md)

## Qin F21 Pro build

This branch adds a compact layout for the 480×640 Qin F21 Pro and full physical-key
navigation: D-pad focus with an amber ring, centre/OK activation, hardware number
entry in amount fields, and IME handling that keeps Save/Cancel visible. Touch and
all existing features (accounts, cards, autopay, recurring, loans, converter,
backups) are unchanged. See [QIN_F21.md](QIN_F21.md) for dimensions, key mappings,
build/test results and known limitations.

## Current release: 1.10.0 — cash-to-cash transfers and loan settlements

**Transfers between any two balances, loans that settle through real accounts.**
Version code **11**, database **version 6** (explicit 5→6 migration), JSON backups
**schema 6** (schemas 1–5 still import). Same application ID and signing key - installs
over 1.9.x without uninstalling.

| Change | Where |
|---|---|
| **Transfer** joins Expense / Deposit / Withdraw in the Add menu (Home and History): pick any source and destination - main, or any cash balance, **cash → cash included** - type the amount in either currency, and see both balances after the move before confirming | `TransferDialog`, `FinanceRepository.transfer` |
| A transfer is **one row with both sides** in their own currencies. If one side is *set aside* (`includedInTotal = false`), both balances still move exactly and *total available* changes by exactly the transferred value - the transfer row itself is the adjustment, never an expense + deposit pair | `AccountBalances`, `TransferMath.totalEffect` |
| **Settle** on a loan now records a real transaction: amount (whole remainder by default, part-payments allowed) and a **dropdown** source/destination - **External revenue (default)**, main, or any cash balance, each shown with its balance. Debts check the paying balance; cross-currency settlements store both sides | `SettleLoanDialog`, `FinanceRepository.recordRepayment` |
| Settled loans leave the active page for the **Archive** (button next to **+** on Loans), appear in **History** under the Loans filter, and can be **reopened** - which reverses the settlement transaction through its account | `LoansScreen`, `TransactionsScreen` |
| The **+ Add** under each Loans section now creates that section's type, and the top **+** asks **I owe / Owed to me** before the form opens (previously always "I owe") | `LoanTypeChooserDialog`, `Screen.AddLoan(loanType)` |
| Settings → Accounts lists every **cash/card balance** under its main account, each **deletable on its own**; a whole-ledger safety copy is saved before any single-account delete, and settlements paid from a deleted balance become external instead of reopening their loans | `SettingsScreen`, `deleteAccountCascade`, `RepaymentDao.unlinkAccount` |

Balances now also include account-linked loan settlements (`AccountBalances` gains
repayments/loans; external repayments - including every repayment from before 1.10 -
never move a balance). Totals on the Loans page count active loans only.

## Previous release: 1.9.1 — account safety (no more "history wiped" after a restart)

**Fixed: a ledger could look completely empty after a reboot even though nothing was
deleted.** An "erase all data" left `isSetupComplete = false` behind; creating an
account from Settings afterwards set the active account but not that flag. On the next
cold start the app opened the **setup wizard**, which created a second empty "Main"
and made it active. Every screen filters by the active account, so expenses and
deposits vanished from Home and History while loans (shared across accounts) stayed.

What changed, so it cannot happen again:

| Fix | Where |
|---|---|
| Setup is needed only when the accounts table is empty; the wizard can never run over an existing ledger, is idempotent, and reuses the existing account | `MainActivity`, `FinanceRepository.completeSetup`, `SetupViewModel` |
| Creating an account also marks setup complete and activates it, in one transaction | `SettingsViewModel.addAccount`, `setActiveAccount` |
| A dangling `activeAccountId` is repaired at startup (deterministic `createdAt, id` order) and persisted | `resolveActiveAccountId` |
| The last main account cannot be deleted; deleting the active one re-points the pointer inside the same transaction | `deleteAccountCascade` |
| Deleting a main account writes a JSON safety copy to app storage first, and the dialog states the entry count and balance; duplicate account names must be typed to confirm | `SettingsViewModel`, `SettingsScreen` |
| "Erase all data" is **one** transaction (accounts, entries, withdrawals, loans, settings), so an interruption cannot leave loans only | `SettingsViewModel.confirmErase` |
| Settings are written per column (`activeAccountId`, theme, rate timestamp, currency order), so a stale whole-row write cannot revert the active account | `AppSettingsDao` |
| Empty screens name the account and point at the account that actually holds the entries ("12 entries are in \"Old ledger\" · Switch"); cash/card deletes state how many rows go with them | `TransactionsScreen`, `HomeScreen` |
| Deleting a cash balance also removes the expenses paid from it (they used to survive as orphans and stop reducing any balance) | `ExpenseDao.deleteForBalanceAccount` |

Tests: `AccountLifecycleTest` (setup idempotency, pointer repair, last-account guard,
cascade, atomic erase, summaries), `PersistenceFileTest` (file database closed and
reopened - committed rows and the active pointer survive a restart), and
`AccountVisibilityMessageTest` (empty-state wording). Version code **10**, database
still **version 5**, JSON backups still **schema 5**.

## Earlier release: 1.9.0 — one Add menu, deposits and cash transfers

**[Download v1.9.0](https://github.com/markcollege2026-droid/Multi-Currency-expense-tracker/releases/tag/v1.9.0)**
— `app-release.apk` (signed, ~2.2 MB), `app-release.aab` for Play, R8 `mapping.txt` and
`SHA256SUMS.txt`.

Version code **9**, version name **1.9.0**, database **version 5**, JSON backups **schema 5**.
Same application ID and signing key as 1.8.x, so it installs over an existing release without
uninstalling. Full details: [release notes and migrations](RELEASE_NOTES.md); design decisions:
[`.superdesign/design-system.md`](.superdesign/design-system.md).

What this release changes, in one line each:

| Change | Where |
|---|---|
| One **Add** control with a 60 ms pop-out — Expense / Deposit / Withdraw, identical on Home and History | `ui/components/AddMenu.kt` |
| **Deposit** replaces "Income", with an **optional cash source** (external is the default, previewed before save) | `AddTopUpViewModel`, `AddTopUpScreen` |
| Withdrawals typed in **either** currency; the typed side is stored exactly, only the derived side is rounded | `domain/TransferMath.kt`, `WithdrawDialog` |
| History rows show the time only today, then `21 Oct` / `31 Dec 2025`; tapping opens **details**, editing is a choice | `TransactionsScreen` |

**File backups:** Settings → Data → Backup files lets you choose a save folder for JSON backups, CSV reports, or password-encrypted `.stbk` backups. Restore accepts JSON or `.stbk` and prompts before replacing existing data. Encryption uses built-in AES-128-GCM with PBKDF2-HMAC-SHA256; no extra library. Remember your password—there is no recovery. CSV reports cannot be restored.

Each existing account is a main/debit ledger with **any number of cash balances** and any number of credit cards. Home and History both have one **+ Add** control that pops out **Expense**, **Deposit** and **Withdraw** — same words, same options, same animation on both screens; Home → **Add cash balance / Add credit card** sets opening amounts, which do not transfer funds from main. **Add cash income** records external cash income. The expense form selects the exact payment account. Main/debit remains the default for deposits and expenses.

Money arriving in main is a **Deposit**. It is external by default (salary, refund, a transfer in from a bank); the form's optional *Source* lets you fund it from a cash balance instead, which records the reverse of a withdrawal and previews what leaves that balance before you save. A deposit that would overdraw the chosen cash balance, or a zero/negative amount, cannot be saved.

A cash balance may use a **different currency** from main. **Withdraw** moves money from main into any cash balance and converts it at the cached rate (1 ILS = 0.25 EUR, say), storing both sides — the main side in its currency, the cash side in the cash currency. The amount can be typed in **either** currency: type ₪400 and €100 arrives, or type €100 and ₪400 leaves. The side you typed is stored exactly and only the derived side is rounded, so a round-trip never loses a cent. Withdrawals are transfers: they never count as spending or deposits, they appear in History under **Transfers**, and deleting one returns the money.

History rows show the **time only while it is today**; older rows show `21 Oct`, and rows from previous years show `31 Dec 2025`. Tapping a row opens its **details** — amount and rate, category, account, method, note and date — and editing is a separate choice inside that dialog (transfers cannot be edited: their saved rate is a fact).

Each cash balance can be **set aside**, so its money is excluded from *total available* without deleting it (for savings you do not want counted as spendable). Foreign-currency cash and cards are converted into the main currency when totals are summed, and Home's week/month/category stats convert expenses paid from foreign cash back into the main currency.

All balances are derived by `AccountBalances` from immutable amounts and payment records:

- Main = opening + main income − main expenses − completed card payments.
- Cash (per balance) = opening + cash income − cash expenses + withdrawals in − withdrawals out, in that balance's own currency.
- Card signed balance = opening signed balance − card expenses + completed payments (debt is negative).
- Total available = main + cash balances not set aside (converted to main currency) + signed card balances.

Card payments are separate history entries and do not inflate category spending. Autopay is a **local ledger operation, not a bank transfer**, checked hourly by WorkManager and when Home opens; Android may defer execution. Failed attempts leave balances unchanged and provide retry/partial-payment/ignore controls. Fixed autopay is capped at debt; manual overpayment and cash overdraft require explicit consent.

Historical payment labels are not reinterpreted during migration, so existing balances stay unchanged. JSON backup format 5 includes withdrawals and the set-aside flag and still accepts older backups (schema 1–4). Existing ledger currencies are fixed: create another balance instead of rewriting mixed-currency history. Database version 5 adds the `withdrawals` table and `accounts.includedInTotal` through an explicit migration.

GitHub Actions runs unit tests (including real Room/Robolectric repository and migration tests), lint, debug/release APK and AAB builds. It verifies upgrade identifiers and the release signing certificate against v1.8.0. An explicitly marked `[release]` push publishes the release only after successful verification; the manual Release APKs workflow is also available to users with workflow-dispatch permission. The Gradle wrapper never falls back to stub APKs or simulated test success.

## Features (earlier releases)

### New in v1.2
- **Multiple accounts** on one device (e.g. Personal / Work / Family), each with its own currency, starting balance and low-balance threshold. Expenses and top-ups are tracked per account; switch the active account from the Home screen or Settings. **Loans & debts stay shared across all accounts.**
- **Recurring payments**: schedule repeating payments (daily / weekly / monthly / yearly) with a start date, optional end date and a chosen due date. Each due payment is posted automatically as an expense in its account, converted at execution time. Missed occurrences are caught up (capped per run); if no exchange rate is available the payment stays due and retries - it is never posted with a wrong amount. Manage them from Home → "Recurring payments" (pause / resume / edit / delete).
- **Loan due-date picker**: choose any due date for a loan or debt (previously only "in 7 days").
- Loans & debts now show the **date they were made** as well as the due date.
- **Custom categories**: create your own categories (with colors) when adding an expense, and delete them from the category picker.
- Export/import JSON now covers accounts, per-account data and recurring payments; old export files still import fine (data goes into a single "Main" account).

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
- Default categories with icons/colors + custom categories (create/delete in the category picker)
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
- Each: person, amount, currency, date made, due date (pick any date), note
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
- **Other**: WorkManager for notifications, kotlinx-serialization
- **minSdk 26, targetSdk 35, Gradle Kotlin DSL**

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
  - platforms android-34 (compileSdk 35)
  - platform-tools (adb)
- Gradle 8.9 (or use included gradlew)

For restricted networks, use the bundle from https://github.com/whynusn/android-sdk-bundle:
- Contains JDK 17, build-tools, platforms, platform-tools
- See its README for setup

### Build APK / App Bundle

```bash
# Debug
./gradlew assembleDebug

# Release APK (signed, R8-minified)
./gradlew assembleRelease

# Play Store App Bundle (signed, R8-minified) - upload this to Google Play Console
./gradlew bundleRelease
```

Outputs:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`
- `app/build/outputs/bundle/release/app-release.aab` (Google Play)
- `app/build/outputs/mapping/release/mapping.txt` (R8 crash deobfuscation)

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
- **Balance currency change**: Re-expresses all history (starting balance, top-ups, expenses) using stored rates, including cross-rates via pivot currencies. If no rate path exists at all, the change is refused instead of corrupting balances.
- **Loans/Debts**: Repayments assumed same currency as original loan; multi-currency repayments would require conversion but simplified for v1
- **No backend**: Fully offline, no sync, no accounts, no analytics as required
- **Permissions**: Only INTERNET and POST_NOTIFICATIONS
- **ProGuard/R8**: Enabled for release (`isMinifyEnabled=true`, `isShrinkResources=true` in app/build.gradle.kts) for smaller APK/AAB and faster startup. R8 mapping is uploaded by CI for crash deobfuscation
- **Room migrations**: Configured with `fallbackToDestructiveMigration()` for v1; schema exported to `app/schemas` for future migrations. Replace with real migrations before shipping schema changes
- **Compose**: Uses Material 3 with dynamic color on Android 12+, fallback palette otherwise
- **Accessibility**: 48dp touch targets, content descriptions, contrast, font scaling considered in UI
- **Localization**: All strings in `res/values/strings.xml`, ready for translation; currency formatting uses `NumberFormat` with locale
- **Manual APK build**: The included `gradlew` wrapper script prefers the real Gradle build; a manual aapt2/d8 fallback exists only for restricted-network environments and produces a minimal demo APK
- **Testing**: Unit tests use JUnit; UI test is placeholder that verifies composable loads. Real UI test would need Hilt test setup and in-memory Room.
- **Notifications**: Low-balance notified on the transition into low balance; loan due reminders via WorkManager every 12h (requires POST_NOTIFICATIONS permission on Android 13+, requested at first launch)

## Download

### Option 1: Google Play
The app ships to Google Play as an **App Bundle (AAB)**, built with R8 code + resource
shrinking. On the device, install it from the Play Store — Play delivers the smallest
APK split for the device and can install startup/baseline profiles for faster launches.

### Option 2: GitHub Actions artifacts (latest build)
1. Go to **Actions** tab -> **Build APKs** workflow
2. Click the latest successful run
3. Scroll to **Artifacts** section
4. Download:
   - `spending-tracker-apks` -> `app-debug.apk` + `app-release.apk` + `app-release.aab`, plus the
     R8 `mapping.txt` and the Room schemas
   - `test-and-lint-reports` -> JUnit and lint reports

Artifacts are generated on every push to `main` or `arena/**` branches. The same run posts its
test/lint summary as a commit comment.

### Option 3: GitHub Releases
1. Go to the [**Releases**](https://github.com/markcollege2026-droid/Multi-Currency-expense-tracker/releases) tab
2. Take the latest release — [v1.9.0](https://github.com/markcollege2026-droid/Multi-Currency-expense-tracker/releases/tag/v1.9.0)
3. Each release ships `app-release.apk`, `app-debug.apk`, `app-release.aab`, the R8 `mapping.txt`
   and `SHA256SUMS.txt`

A push whose commit message contains `[release]` publishes the tag derived from `versionName`
after tests, lint, APK/AAB builds and the upgrade check against v1.8.0 pass. The
**Release APKs** workflow dispatches the same job manually with an explicit tag. Signing
keystores are never attached to a release.

### Option 4: Build locally
```bash
./gradlew assembleDebug
./gradlew assembleRelease   # signed, R8-minified APK
./gradlew bundleRelease     # signed AAB for Google Play
```
Outputs are in `app/build/outputs/`.

## APK Paths

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk` (signed with `release.keystore`)
- Play bundle: `app/build/outputs/bundle/release/app-release.aab`

## License

MIT - for demonstration purposes.
