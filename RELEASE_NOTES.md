# Spending Tracker 1.8.0 — Multiple balances

## Major changes
- Each existing account remains a main/debit ledger and can have one cash balance and multiple credit cards, all in its currency.
- Home displays main/debit, cash, each card's signed balance, total available funds, upcoming autopay amounts, and insufficient-funds warnings.
- New expenses use an explicit payment-account picker. Cash reduces only cash; credit-card expenses increase only card debt. Categories and spending reports still include both.
- Manual card payments deduct main/debit and reduce debt atomically. Payments are separate history records, never duplicate expenses. Overpayment requires explicit consent; insufficient main funds do not change either balance.
- Per-card autopay supports enable/disable, due day 1–31, full debt or a fixed amount capped at debt. Short months use the last day. Persistent monthly keys prevent repeat automatic attempts, including failed attempts. Retry, partial payment and ignore are available on Home.
- Background autopay checks hourly and when Home opens. Android can defer background execution (especially after force-stop); no exact-time guarantee. These are local ledger entries, not actual bank transfers.
- Centralized BigDecimal ledger calculation keeps all balances consistent after edits/deletes/payments. Cash overdraft must be explicitly enabled when saving an expense.
- JSON backup schema 4 includes sub-accounts, expense routing, autopay settings and payment attempts. Replace-all imports are transactional; older JSON exports remain supported.
- Existing global currency rewriting was unsafe across independent currencies and is now blocked. Create another account in the desired currency instead.
- GitHub Actions now gates builds/releases on unit tests and lint, retains build reports, and no longer publishes the signing keystore as a release asset.

## Database migrations
Room database version **3 → 4** adds account type/parent, optional credit limit, autopay settings/start date, nullable `expenses.balanceAccountId`, and `credit_card_payments` with a unique `(cardId, autopayPeriod)` index. Balances remain derived, not duplicated mutable fields.

Existing accounts become `MAIN`. Historical expenses keep their original main-account effect even if their free-text method says Cash/Credit card. No historical amounts, dates, categories or notes are changed. Select a new payment account when editing if you intentionally want to reassign an old transaction.

The existing **2 → 3** migration remains. A **1 → 2** migration handles early `top_ups` naming, nullable expense categories and currency-order fields. Unknown database versions fail safely rather than destructively erasing data. Back up before upgrading; the repository's early schema-1 export was incomplete, so test very old installations on a backup first.

## Upgrade compatibility
Same application ID (`com.spendingtracker.app`), existing release signing key, versionCode **6** (previously 5), minSdk **26** (Android 8+), targetSdk **35**. Install **app-release.apk over a previous release-signed APK** without uninstalling. Debug-signed installations need the matching debug build; release/debug signatures are not interchangeable. Android will reject upgrades signed by a different key.

The existing release key is already committed in the historical repository. It is retained for upgrade compatibility, not a security recommendation; removing it from this change cannot revoke historical exposure. Plan a signing-key migration separately if needed.

Additional cleanup: fixed account renaming (update instead of conflicting insert), made low-balance thresholds apply to the active account rather than obsolete global settings, removed an unused floating-point SQL balance aggregate, and replaced the old wrapper's stub-APK/test-success fallback with the genuine Gradle wrapper. Cash income appears alongside other income in transaction history.
