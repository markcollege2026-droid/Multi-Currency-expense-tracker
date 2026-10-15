# Codebase review and implementation map

## Review scope
The repository inventory covers the Android application, all Kotlin source and tests, Room entities/DAOs and historical schema, Compose navigation/screens/components/themes, exchange-rate client and converter, loan/debt handling, workers/notifications, backup/restore, resources/manifest, Gradle configuration/wrappers, and both GitHub workflows. There is no separate backend or web app.

Changes are deliberately concentrated on financial correctness and upgrade safety rather than unrelated visual redesign or renaming persisted fields. Existing `Expense` / `TopUp` table and JSON names remain compatible: top-ups represent income; `CreditCardPayment` represents `CARD_PAYMENT`. `accountId` retains the original independent wallet context and optional `balanceAccountId` identifies the balance actually charged. A missing balance target retains historical main-account behavior.

## Main findings and changes
| Area | Finding | Resolution / files |
|---|---|---|
| Accounts | Independent wallets existed, but no typed balances | Extend `Account` with MAIN/CASH/CREDIT_CARD, parent, optional credit limit and autopay fields. One cash balance per wallet; cards inherit wallet currency. |
| Expenses | Free-text payment labels did not affect routing | Explicit account picker in `AddExpenseScreen` / ViewModel. Saved conversion and category remain unchanged. |
| Money logic | Home recomputed all expenses against main; unused SQL summed money as floating point | `domain/AccountBalances.kt` centralizes derived balances. Remove unused floating-point DAO aggregate. |
| Validation | Cash could overspend and nonpositive manual rates could be saved | Transactional checks in `FinanceRepository`; cash overdraft is explicit; positive amount/rate and wallet/currency ownership required. Income accepts only main/cash. |
| Card payments | Missing | New entity/DAO plus atomic `payCard`: successful payments affect two balances through one record; failures affect neither. Main funds and overpayment checked inside the same Room transaction. |
| Autopay | Missing | Per-card configuration; due-day clamping; full/fixed debt calculation; unique card/month key including failures; hourly worker plus Home check; in-app and permission-aware notifications. No bank integration. |
| Home | Only a single balance shown | `BalanceAccountsPanel` adds cash, per-card balances, total available, upcoming autopay and warnings, create/fund/pay/configure/retry/partial/ignore controls. |
| History | Income/expense only | Payment entries with status and a dedicated filter; cash income included. Payments excluded from expense/category totals. |
| Backup | New data would be lost on export; failed replace-all imports could erase data | Schema-4 JSON includes routing, typed accounts and all payment attempts. Entire replace-all import is one Room transaction, including shared loan tables. CSV fixes incorrect income currency and quotes all fields. |
| Settings | Rename used conflicting insert; threshold edited legacy global field | Use account update; thresholds apply to active account. |
| Currency changes | Old function rewrote every wallet's history but did not update account currencies | Refuse unsafe historical currency rewriting; offer new independent account instead. |
| Database | Only 2→3 migration; destructive fallback erased unsupported databases | Add 1→2 and 3→4, remove destructive fallback; startup displays a data-open error rather than presenting setup after a failed migration. |
| Build wrapper | Could replace a failed real build with a stub APK or simulated test result | Always invoke the genuine checked-in Gradle wrapper and propagate failure. |
| Release workflows | Tests not gated; signing keystore published as an asset | Tests + lint + signed builds; signature/version/minSdk verification against v1.7; no keystore asset; explicit release publication. |

## Test coverage added
- Pure BigDecimal calculations: routing, card debt, multiple cards, legacy labels, cash income, payment conservation, failure no-op, edits/deletes, full/fixed autopay and February/leap-year clamping.
- Real Room/Robolectric repository tests: cash overdraft opt-in, account ownership, manual overpayment, insufficient funds, monthly deduplication, partial retry, backup round-trip/rollback and CSV escaping/currencies.
- Migration 3→4 with preserved expense note/amount and real Room schema validation.
- Earliest checked-in schema fixture upgraded 1→2→3→4, preserving income/expenses/settings and validating Room's final schema.
- Existing currency conversion, balance, loan and recurring-date tests remain in the suite.

The release build passed **53 tests with zero failures, errors or skips**, lint and APK/AAB compilation. The actual APK application ID, signing certificate, increasing versionCode and minSdk were checked against v1.7. Build/test reports are GitHub Actions artifacts. See the verified build links on the pull request and release. These tests do not replace physical-device upgrade testing of every historical APK; the earliest schema file is a simplified historical fixture.

## Deliberate limitations / follow-ups
- WorkManager is best-effort, not an exact-time scheduler; force-stopped apps cannot run until Android permits execution. Enabling autopay does not retroactively pay dates before configuration; current-month due payments catch up when checked.
- These are local accounting entries, not real bank payments. Credit limits are optional metadata, not an enforced bank authorization limit.
- No new cross-currency sub-account transfers or bank integrations. Existing independent multi-currency wallets remain independent.
- Existing converter reference rates, synthetic fallback chart series and synthetic percentage-change badges are pre-existing converter behavior, not new ledger inputs. They deserve a separate market-data accuracy pass; this change does not claim to make them live market data.
- The historical release signing key is committed publicly in the repository. It is retained for install-over compatibility; removing its current file cannot revoke exposure. Key rotation/distribution requires a separate upgrade plan.

## File-backup follow-up (1.8.1)
`BackupControls` uses Android CreateDocument/OpenDocument to save JSON/CSV/encrypted files and confirm restore. `BackupFileManager` implements bounded document reads/writes. `BackupEncryption` uses platform AES-GCM and PBKDF2 with a random salt/nonce, without an added dependency. Settings operations run on IO and expose progress/errors; no passwords enter saved instance state. Exports are consistent snapshots; restores also replace categories/rates atomically. Database/schema versions stay unchanged.
