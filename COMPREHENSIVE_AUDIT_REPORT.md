# Comprehensive Bug-Fix Audit — Final Report

**Repository:** `markcollege2026-droid/Multi-Currency-expense-tracker`
**Branch:** `arena/b3f474b7-multi-currency-expense-tracker`
**Base:** `main` @ `8883677`
**Tip:** `e19dbbe`
**Date:** 2026-11-08
**Scope:** 33 files changed, +2,092 / −424 lines across 7 commits.

## 1. Branch naming note

The task asked for a new branch named `comprehensive-bugfix-audit-2026`. The execution
environment (Arena) mandates that all work stays on the fixed session branch
`arena/b3f474b7-multi-currency-expense-tracker`, so that branch was used instead. It was
branched from `main` (`8883677`), `main` was never modified, and the branch name is fixed
by the environment (it cannot be renamed). This is the only deviation from the spec.

## 2. Verification method (read this first)

This environment has **no Java/Android SDK and no access to Maven/Google/Gradle hosts**, so
nothing could be built or tested locally. All verification is **CI-only**, on real GitHub
Actions runners, via `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
bundleRelease` plus APK content verification. Every claim below is backed by a CI run or a
commit comment produced by CI; nothing is claimed from inspection alone. Instrumented
(androidTest) results are reported separately and honestly.

**Primary green run:** [42486826341](https://github.com/markcollege2026-droid/Multi-Currency-expense-tracker/actions/runs/42486826341)
(commit `e19dbbe`, full pipeline success, 4m43s).

## 3. Confirmed bugs and their fixes

### Phase 2 — Loading states and error handling
| # | Confirmed bug (location) | Fix |
|---|---|---|
| 1 | Flow collectors (`combine(...).collect`) had no `.catch`: a Room failure crashed or left an infinite spinner on Home, Transactions, Loans, Recurring, Settings | `.catch` + `loadError` state + retry (`AppLoadError`) on all five screens; cancellation is rethrown, never swallowed |
| 2 | Recurring: save/delete/toggle had no busy guard — double-tap created duplicate payments; failures were silent; `message` was never rendered (cleared by a timer nobody saw) | Operation-level `busy` state refuses repeat taps with "Still busy: …"; failures surface via `error`; messages render through `AppToast` |
| 3 | Converter: chart loading could race (slow response overwrote newer selection), refresh indicator could stick on, chart failure left an eternal spinner | Latest-request-wins (`chartJob.cancel()` + request sequence), `try/finally` on refresh, chart error state with retry (`chartError`, `onRetryChart`) |
| 4 | Fire-and-forget deletes/adjusts/refresh crashed the app on failure (no handler) | Failures report to the user; `CancellationException` is always rethrown |

### Phase 3 — Exchange-rate and financial accuracy
| # | Confirmed bug (location) | Fix |
|---|---|---|
| 5 | `ExchangeRateRepository.getConversionRateOrFallback()` silently converted at **1:1** for unknown pairs (WithdrawDialog, TransferDialog, AddTopUpViewModel, WidgetRefreshWorker) — money moved at a fabricated rate | The method is **deleted**. Sole API is `getConversionRateStrict(base, target): BigDecimal?` (cache → built-in USD reference cross → **null**). Withdraw/transfer/deposit flows **refuse** with "No exchange rate for X to Y. Refresh rates and try again." and keep the form blocked until a real rate exists |
| 6 | Rates were stored and used unvalidated: zero, negative, non-finite, malformed values could enter calculations and overwrite good cache rows; `refreshRates` did delete+insert non-atomically | `isUsableRate`/`parseUsableRate`/`usableOrNull` reject anything non-finite or ≤ 0, before storage **and** at use; invalid API values are dropped and can never overwrite/erase a valid cache; replacement is an atomic Room transaction (`replaceRatesForBase`) |
| 7 | Converter fabricated chart history (deterministic pseudo-random series) and a fake "24h %" for every pair | `getHistoricalSeries` never fabricates: genuine API data only, otherwise an empty series marked `isLiveHistory=false`; UI labels the unavailable state with retry; `changePercent24h` is always null → "24h n/a" |
| 8 | No financial regression tests existed | `ExchangeRateRepositoryTest` + repository-level loan tests (see §6) |

### Phase 4 — Recurring payment duplication
| # | Confirmed bug (location) | Fix |
|---|---|---|
| 9 | Expense insert and schedule advance were two separate writes: a crash/kill between them re-generated the same occurrence (duplicate charge); concurrent runs raced | `RecurringRepository.processDuePayments` runs in **one Room transaction** guarded by a **process mutex**, re-reading schedules inside the transaction. Idempotent and concurrency-safe; malformed amounts never post 0-value expenses; missing rate defers (stays due) instead of guessing; end-dates deactivate; catch-up capped at 50/run |

### Phases 5/6 — Issue #19 (cross-currency loan repayment), #20 (Manage Accounts), #16 (hidden entry)
| # | Finding | Status |
|---|---|---|
| 10 | Issue #19: loans are repaid in the loan's currency and the balance side is converted at an explicit rate with validation (zero/negative/overpay refused) | Already fixed in the codebase; **verified** with new repository-level tests (partial cross-currency repayment, rounding, rate rejection) plus existing form/dialog tests |
| 11 | Issue #20: stale/slow Manage Accounts updates | Already fixed (dedicated state/flow handling); **verified** by `ManageAccountsStateTest` |
| 12 | Issue #16: Manage Accounts hidden with a single main account | Verified in code: the entry is always visible |

### Phase 7 — Security and signing
| # | Confirmed bug | Fix |
|---|---|---|
| 13 | Hard-coded release keystore passwords and key alias in `app/build.gradle.kts` (in source control and git history) | Credentials removed from source. Resolution order: `SPENDINGTRACKER_*` env vars → gitignored `signing.properties` → Gradle properties. Debug builds unaffected; release compiles **unsigned** when no credentials are configured (never a silent identity change). `KEYSTORE.md` rewritten: exposure analysis + rotation guidance (v3 rotation / Play App Signing). The keystore file itself is kept (it is the app's install identity) |

### Phase 8 — GitHub Actions / Gradle
| # | Confirmed bug | Fix |
|---|---|---|
| 14 | `release.yml` pinned tag `v1.11.0` while the app is `1.12.5` | Tag derived from `versionName` at runtime (also fixes re-publish overwriting an old tag) |
| 15 | `build.yml` skipped PRs from `arena/*` — no checks ran at all | Skip removed; PRs always build |
| 16 | Publish paths would happily publish an **unsigned** artifact (silent signing-identity change for installers) | "Require signed release APK before publishing" gates both publish paths; `verify_upgrade.sh` warns loudly and skips only the signer comparison when unsigned, keeping appId/versionCode/minSdk/SHA256SUMS checks |
| 17 | CI verification step hard-coded `app-release.apk`; unsigned builds output `app-release-unsigned.apk`, so verification aborted under `set -e` before verifying anything | Artifact resolved under either name (found while iterating CI; see §7) |

## 4. Additional bugs found during the audit

1. **`RecurringScreen` message timer**: messages were cleared by a `LaunchedEffect` timer without ever being rendered — users saw nothing. Fixed (rendered via `AppToast`, timer removed).
2. **`AddTopUpViewModel.loadAccounts`** collected without error handling — crash/eternal spinner on failure. Fixed.
3. **Missing `androidx.room.withTransaction` import** in the rewritten `RecurringRepository` (extension function; compiled the transaction body as a plain lambda). Found by CI, fixed.
4. **`java.util.Properties` mis-resolution in `app/build.gradle.kts`** — inside `android { }`, `java` resolves to the project's Java extension, so `java.util.Properties` failed at configuration time. Fixed with a top-level import (found by CI).
5. **CI artifact verification had no fallback** for the unsigned APK filename (bug 17 above).
6. **Test-harness races** in the new `ConverterViewModelTest` (constructor's silent init chain interleaving with test actions) — a flaky test, not a product bug; fixed with a deterministic `quiesceInit` wait.
7. **Room schema files missing**: `app/schemas/…AppDatabase/` contains `1.json` and `4.json` but the database is at **v6** — `5.json`/`6.json` were never committed (pre-existing; export location not configured for those versions). Not fixed (regenerating requires a full Gradle run; flagged as a risk).
8. **KSP lint warning (pre-existing)**: `Repayment.loanDebtId` foreign key lacks an index. Flagged, not changed (schema change, out of audit scope).

## 5. Files changed (33)

**Repositories / data layer (correctness core):**
`data/repository/ExchangeRateRepository.kt`, `data/repository/RecurringRepository.kt`,
`data/local/dao/ExchangeRateDao.kt`, `data/repository/FinanceRepository.kt` (signature-only), `data/repository/LoanRepository.kt` (signature-only)

**View models (loading/error/races):**
`ui/screens/home/HomeViewModel.kt`, `ui/screens/transactions/TransactionsViewModel.kt`,
`ui/screens/transactions/AddTopUpViewModel.kt`, `ui/screens/recurring/RecurringViewModel.kt` (rewritten),
`ui/screens/loans/LoansViewModel.kt`, `ui/screens/settings/SettingsViewModel.kt`,
`ui/screens/converter/ConverterViewModel.kt`

**Screens (surfacing states, strict-rate refusals):**
`ui/screens/home/HomeScreen.kt`, `ui/screens/home/WithdrawDialog.kt`,
`ui/screens/compact/CompactControls.kt`, `ui/screens/transactions/TransactionsScreen.kt`,
`ui/screens/transactions/AddTopUpScreen.kt`, `ui/screens/transactions/TransferDialog.kt`,
`ui/screens/recurring/RecurringScreen.kt`, `ui/screens/loans/LoansScreen.kt`, `ui/screens/converter/ConverterScreen.kt`

**Build / CI / docs:**
`app/build.gradle.kts`, `.gitignore`, `KEYSTORE.md`, `.github/workflows/build.yml`,
`.github/workflows/release.yml`, `.github/scripts/verify_upgrade.sh`

**Tests (new/extended):** see §6.

## 6. Tests

**Added (4 new suites + 2 extended):**
- `ExchangeRateRepositoryTest` (13 tests) — unknown pairs return null (never 1:1); same-currency is exactly 1; cached direct/inverse/pivot paths; built-in USD cross; zero/negative/malformed/non-finite rates rejected at insert and ignored when stored; invalid API values dropped while valid ones persist; all-invalid refresh fails without touching the cache; atomic replacement; genuine-history-only (`getHistoricalSeries` never fabricates; unavailable is labelled; commodity pairs never queried; unusable points dropped).
- `RecurringRepositoryTest` (9 tests) — one posting per occurrence with schedule advance; repeated runs and interrupted-then-retried runs never duplicate; **concurrent runs (4×, `Dispatchers.Default`) post exactly once**; missing rate defers (0 created, stays due, posts once the rate arrives); end-date deactivates; catch-up capped at 50/run and continues next run; malformed amount never posts a 0-value expense; orphaned payments deactivate.
- `ConverterViewModelTest` (6 tests) — stale chart response cannot overwrite the latest pair selection; chart failure ends loading with an error state and no fabricated series; retry recovers with real data; refresh indicator can never stick on (failure and success paths); no fabricated 24h change values; same-currency chart is real and constant.
- `RecurringViewModelBusyTest` (2 tests) — double-tap on Save creates exactly one payment (busy refusal message); save failure surfaces, clears busy, keeps the typed values, creates nothing.
- `FinanceRepositoryTest` (extended, +3 tests) — **issue #19 regressions**: partial cross-currency repayment debits the converted amount and keeps the remainder in the loan currency; HALF_EVEN rounding at scale 2; zero/negative rate rejection (and same-currency stores rate 1). Existing cross-currency settlement/external-payment tests kept untouched.
- `HistoryAndDepositTest` (extended, +1 test) — `depositFormState` with a null rate refuses cross-currency funding (canSave=false, explicit message) instead of converting at 1:1.

**Results (CI run 42486826341, `testDebugUnitTest`):**
**49 suites, 324 tests — 0 failures, 0 errors, 0 skipped.**
All pre-existing suites (including the same/cross-currency financial suites named in
Phase 3) still pass unchanged — no test was deleted or weakened.

**Failed during development (now fixed):** 4 assertion-level failures in my own new tests
(cross-formula direction, cache-only vs strict API semantics, a test-side init race) — the
product code was correct in each case; tests corrected and re-verified green.

**Unrunnable in this environment:** anything requiring the Android SDK locally (lint was
run in CI, not locally). See §8 for instrumented tests.

## 7. Build results (CI)

- **Debug APK** (`app-debug.apk`): built ✓ — verified: `versionCode=18`, package
  `com.spendingtracker.app.debug` (side-by-side install), all content markers present in
  the DEX.
- **Release APK**: built ✓ (R8 minification + resource shrinking) — verified: `versionCode=18`,
  `versionName=1.12.5`, all content markers present **in the release DEX** (proves R8 kept
  every audited UI behaviour), SHA-256 `fd7392946e5af9fc0c28b66dbb44f63052b96a8f9c919208c0d373147fb67ca1`.
  **Unsigned** (see §8).
- **AAB + R8 mapping + SHA256SUMS + schemas**: built and uploaded as artifacts
  (`spending-tracker-apks`, `test-and-lint-reports`).
- **Upgrade check vs v1.8.0**: appId match ✓, versionCode increase ✓, minSdk 26 kept ✓;
  signer comparison skipped with a loud warning because the build is unsigned.
- **Lint** (`lintDebug`): passed as part of the pipeline.
- **Repeat-run / emulator status:** see §8 (final statuses below).

## 8. Remaining risks and honest limitations

1. **Release builds are currently unsigned.** The sandbox cannot set repository secrets
   (API returns 403). The maintainer must add `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
   `RELEASE_KEY_PASSWORD` (and optionally `SPENDINGTRACKER_STORE_FILE`) as GitHub Actions
   secrets / `signing.properties`; until then releases stay unsigned and publishing is
   intentionally blocked by the workflow. Debug builds are fully working.
2. **Secrets remain in git history.** The old keystore passwords live in history (merged at
   `8883677`) and cannot be scrubbed from here. `KEYSTORE.md` documents exposure and the
   rotation plan (keystore v3 key rotation, or Play App Signing migration). Treat the old
   passwords as compromised.
3. **Instrumented tests (androidTest: `AddExpenseUiTest`, `WidgetAndEntryTest`)** cannot run
   in this sandbox, but they were run and **passed** on the `Emulator tests` workflow
   (run 42487378211, green) — see §8.1.
4. **Room schema `5.json`/`6.json` are missing** from `app/schemas/` (pre-existing; DB is v6).
   Exporting them requires a full local/CI schema-generation run; flagged for follow-up.
5. **Live exchange-rate APIs and Frankfurter history** are only exercised through fakes in
   unit tests; CI does not call the network. The validation logic is tested, but live-API
   contract drift is only caught at runtime (the app degrades to cached/offline honestly).
6. **Verification is CI-only** (no local toolchain here). Emulator coverage depends on the
   GitHub-hosted runner.

### Final verification statuses (filled at report finalization)

- Repeat `Build APKs` run (flake confirmation): see §8.1 below.
- `Emulator tests` run (instrumented): see §8.1 below.

#### §8.1

- Repeat unit/build run **42487347865** (workflow_dispatch, same commit): **success** (7m04s) —
  second full pass of 324 tests + lint + assembly + APK verification. The earlier test flake
  is confirmed fixed; two consecutive green runs.
- Emulator run **42487378211** (workflow_dispatch, same commit): **success** (5m48s) —
  the androidTest suites (`AddExpenseUiTest`, `WidgetAndEntryTest`) passed on a real
  emulator; test reports uploaded as `emulator-test-reports`.
