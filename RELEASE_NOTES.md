# Spending Tracker 1.11.0 — Amount entry, currency order, live theme & Manage Accounts

Released 28 October 2026.

## Fixed: typing decimal amounts
Typing digits after a decimal point could make the cursor jump between the
left and right edges of the amount field (worst on RTL locales, where the
bidi algorithm parks a trailing "." on the far side of the digits), and
nothing stopped a second separator from making the amount unparseable and
silently disabling Save. Amount fields now own a real `TextFieldValue` (the
cursor is stable), are laid out LTR like numbers everywhere, and sanitise
input as typed - one separator, comma becomes ".", pasted groupings like
1,234.56 and 1.234,56 both understood - through a single `AmountInput`
model shared by every money field. The converter no longer treats "," as
grouping (1,5 meant 15 there, 1.5 everywhere else). Transient text such as
"." while typing ".5" no longer shows as an error. Hardware-keypad entry
also reads the field's live value (fast key repeats could append to stale
text) and accepts the '*' key by its character.

## Fixed: the currency-order setting now applies everywhere
The order configured in Settings (A-Z / Favorites / Recent / Custom)
reached the expense form and Settings, but the pickers in **Add Loan**,
**Recurring**, **Home's create-cash/card dialog** and the **Converter**
silently used a hard-coded list that is not even alphabetical. They all use
`FinanceRepository.orderedCurrencies()` now, and picking a currency records
it as recent from those screens too. The Customize dialog edits locally and
persists only on Save - previously every arrow tap wrote to the database,
Room re-emitted settings, and the list reseeded mid-edit (and Cancel did not
cancel). Reorder arrows are available whatever the current mode. The picker
no longer stops after 120 of ~155 currencies. Favorites behaviour is
unchanged and still floats to the top.

## Fixed: the theme setting applies immediately
Light/Dark/System was read once at cold start, so changing it in Settings
looked like it did nothing until the app was fully restarted. MainActivity
now observes settings live and re-themes instantly; the loading/error
surfaces are themed too.

## New: Settings -> Manage Accounts
Account management was only reachable from the account menu at the top of
Home. Settings now has a **Manage accounts** entry: a list of your accounts
with balances, entry counts and what each holds; tap one to open it - its
figures, Use/Rename/Delete, its **cash balances and credit cards** (each
deletable on its own), and "+ Add cash balance". "+ Add account" stays in
the list. All existing safeguards (last-account guard, duplicate-name
confirmation, pre-delete safety copy) apply in the new pages.

## Compatibility and migrations
Same application ID and signing key; installs over 1.10.x. Version code
**12**, version name **1.11.0**. No database or backup-schema change (still
DB v6 / backup schema 6).

## Testing
GitHub Actions runs the full suite, lint and signed builds. New coverage:
AmountInput sanitiser/parser (both separator conventions, grouping, pasted
text, non-ASCII digits), Compose tests typing real decimals into the amount
field (including in an RTL layout, cursor staying at the end), the
currency-order dialog's save-only persistence, Manage Accounts list/detail,
and theme resolution. A new **Emulator tests** workflow runs the
instrumented suite (decimal entry on the actual expense form through the
real input pipeline) on an API 33 emulator.

# Spending Tracker 1.10.0 — Cash transfers & loan settlements

Released 28 October 2026.

## Transfer between any two balances (cash → cash included)
The Add menu on Home and in History (Transactions) gains a fourth option,
**Transfer**. Pick any source and any destination — main or any cash balance,
including cash → cash — type the amount in either currency, and see both
balances after the move before you confirm.

- A transfer is stored as **one row with both sides** (each in its own
  currency). When one side is a cash balance that is **set aside** (not counted
  in total available), both balances still move by exactly the transferred
  amount, and the total changes by exactly that amount as money crosses into
  or out of the set-aside balance. The dialog says so up front. The transfer
  row itself is the adjustment — it is never recorded as an expense plus a
  deposit, so spending and deposit totals are never inflated.
- Transfers cannot overdraw their source, cannot involve credit cards or a
  different main account, and convert cross-currency with the typed side
  stored exactly (same rule as withdrawals).

## Loans settle through real accounts — or externally
**Settle** on any active loan now opens a settlement form instead of just
flipping a flag:

- Amount defaults to the whole remaining balance; part-payments are allowed
  and keep the loan active with the reduced remainder.
- A **dropdown** chooses where the money moves: **External revenue (default)**
  — recorded as a transaction but no balance changes — the main balance, or any
  cash balance, each listed with its current balance. Paying a debt cannot
  exceed what the chosen balance holds; receiving a loan repayment has no such
  limit. Cross-currency settlements store both sides and preview the converted
  amount and the balance afterwards.
- Every repayment/settlement is a **transaction**: it appears in History
  (filter: **Loans**), moves the linked balance, and deleting it reopens the
  loan and reverses the money.

## Settled loans move to the Archive
Once a loan reaches zero it leaves the active Loans page (and its totals) and
moves to the **Archive**, opened with the Archive button next to **+** at the
top of the Loans page. From the archive a loan can be reopened — reopening
deletes exactly the transaction that settled it — or deleted. Loans settled
before this release appear in the archive automatically.

## "Owed to me" creation fixed
The **+ Add** above each Loans section now creates that section's loan type
("I owe" above debts, "Owed to me" above loans), and the top **+** asks which
type you mean before the form opens. Previously every path opened a blank
"I owe" form.

## Delete one account, keep everything else
Settings → Accounts now lists every cash and card balance under its main
account, each with its own balance, entry count and **Delete**. Deleting one
removes only that account and its own entries: transferred money returns to
the balances it came from, every other account is untouched, and shared loans
survive (settlements paid from the deleted balance become external records
instead of dangling or reopening their loans). A whole-ledger JSON safety copy
is written to app storage before any single-account delete. The existing
guards stay: the last main account cannot be deleted, and duplicate account
names must be typed to confirm.

## Compatibility and migrations
Same application ID and release signing key; installs over 1.9.x without
uninstalling. Version code **11**, version name **1.10.0**, minSdk 26,
targetSdk 35. Database **version 6** (explicit 5→6 migration: settlement
bookkeeping columns; existing repayments stay external so no balance changes
on upgrade). JSON backups are **schema 6** and still import schemas 1–5;
cash→cash transfers and account-linked settlements round-trip through export,
restore and CSV.

## Testing
GitHub Actions runs the full unit suite, lint, debug + signed release APKs,
the AAB and upgrade checks against v1.8.0. New coverage: cash→cash transfers
(same/cross currency, set-aside totals, cross-ledger/card rejection,
overdraw), loan settlements (cash/main/external, part-payments, overpay and
insufficient-funds rejection, cross-currency, reopen and delete reversal,
already-repaid archiving, backup round-trip, 5→6 migration keeping legacy
repayments external), per-account deletion isolation across two ledgers, and
transfer/settlement form + dialog tests including the loan-type chooser.

## Limits
Settlements pick balances from the active main account (loans themselves stay
shared, as before). Account-funded settlements are started from the Loans
tab's Settle action; repayments typed on the loan edit screen remain external.
These are local accounting entries, not bank transfers.

# Spending Tracker 1.9.1 — Account safety

Released 28 October 2026.

## The bug that made history look deleted
A ledger could open completely empty after a restart while **nothing had been deleted**.
Sequence that triggered it:

1. Erase all data (Settings) — this deletes the settings row and writes a fresh one with
   `isSetupComplete = false`.
2. Create a new account from Settings → Accounts, and add expenses/deposits to it. That
   path set the active account but never set `isSetupComplete`.
3. Close the app. On the next cold start the app read the stale flag, opened the **Setup
   wizard**, and the wizard inserted a *second*, empty "Main" account and made it active.
   Home and History filter by the active account, so the real ledger disappeared from both
   screens while Loans — shared across accounts — looked untouched.

The rows were never lost. This release removes every path that could lead there, and makes
the symptom impossible to misread.

## Fixes
- **Setup only when there is no ledger.** The start destination is derived from the
  accounts table, never from a flag that can go stale. `completeSetup` is one transaction
  (account + active pointer + flag + default categories), is idempotent, and reuses an
  existing main account instead of inserting a duplicate.
- **Account creation is atomic and complete.** Settings → Accounts → Add account inserts,
  activates, and marks setup complete together; the setup button also refuses re-entry, so
  a double tap cannot create two accounts.
- **Startup repair.** A dangling `activeAccountId` (deleted account, restore, stale write)
  is resolved to a real main account in a deterministic `createdAt, id` order and persisted.
  A "not set up" flag that contradicts an existing ledger is cleared.
- **Deletion cannot destroy a ledger by accident.** The last main account cannot be deleted.
  Deleting the active account moves the pointer to a survivor in the same transaction. A
  main account is snapshotted to JSON in app storage before the cascade. The dialog states
  the entry count and current balance, and when two accounts share a name you must type it
  to confirm. Cash/card deletes state how many entries go with them.
- **Erase all data is one transaction** across accounts, entries, withdrawals, loans and
  settings; an interruption rolls the whole thing back instead of leaving loans only. After
  a successful erase the app restarts into setup rather than showing empty screens.
- **Settings are written per column** (`activeAccountId`, theme, rate timestamp, currency
  order, favourites, recents). A whole-row write from a stale snapshot — for example the
  automatic rate refresh — could previously put an old `activeAccountId` back.
- **Empty states are honest.** "No entries in *Main* yet" plus, when rows exist elsewhere,
  "12 entries are in *Old ledger* · Switch", so a different account can never read as data
  loss. Loans stay visible in both cases, as before.
- **Cash-balance cascade repair.** Deleting a cash balance now also deletes the expenses
  paid from it. They used to survive with a dangling `balanceAccountId`: still listed in
  History, no longer reducing any balance, so money silently reappeared.

## Durability
Ledger writes already commit per Room transaction and nothing is cached in memory, so a
reboot or a "close from Recents" cannot drop committed rows — now covered by
`PersistenceFileTest`, which closes a real database file and reopens it. Lifecycle writes
(setup, account create/delete, erase) run under `NonCancellable` so closing the app
mid-action finishes the commit instead of truncating it.

## Compatibility and migrations
Same application ID and release signing key; installs over 1.8.x without uninstalling.
Version code **10**, version name **1.9.1**, minSdk 26, targetSdk 35. Database stays at
**version 5** and JSON backups at **schema 5** — no migration is needed for this release.
Backups restored from any schema 1–5 now always mark the ledger as set up.

## Testing
GitHub Actions runs the full unit suite, lint, debug + signed release APKs, the AAB and the
upgrade checks against v1.8.0. New this release: `AccountLifecycleTest` (setup idempotency,
existing-ledger reuse, pointer repair and ordering, last-account guard, active-account
re-point, cash cascade, atomic erase rollback, per-account summaries),
`PersistenceFileTest` (file database survives close/reopen), `AccountVisibilityMessageTest`.

## Limits
The pre-delete safety copy lives in app-private storage (`files/pre-delete-backups/`) and is
not surfaced as a restore button; it is a last-resort recovery file, not a backup feature.
Restoring it requires the normal import flow once copied out of app storage.

# Spending Tracker 1.9.0 — One Add menu, deposits and cash transfers

## One way to add anything
Adding is now a single **Add** control that pops out the three ways money moves — **Expense**,
**Deposit**, **Withdraw** — with a 60 ms staggered scale/fade. It is the same control, the same
words and the same behaviour on Home and in History (Home draws it as buttons, History as a pill
next to the search field). Back closes the menu before it leaves the tab. On the 240×320dp Qin
window the options wrap instead of squeezing their labels.

The old History `− / +` pills only covered two of the three transaction types and gave the same
actions different names on different screens; that is gone.

## Deposits
Money arriving in the main account is now a **Deposit** (it was called "Income"). The form is
unchanged apart from the wording and one addition: an optional **Source**.

- **External** is the default: a salary, a refund, money from a bank — nothing else in the app moves.
- Picking a **cash balance** funds the deposit from it. That is recorded as the reverse of a
  withdrawal (cash ↓, main ↑, converted at the cached rate), and the form previews exactly what
  leaves the balance — "≈ −€304.00 from Euro wallet" — before you save.

Zero, negative and over-balance amounts cannot be saved; the button stays disabled instead of
failing after the fact.

## Withdrawals in either currency
A withdrawal between main and a cash balance of another currency can now be typed in **either**
currency: enter ₪400 and €100 arrives, or enter €100 and ₪400 leaves. The side you typed is stored
exactly and only the derived side is rounded once, so a round trip never loses a cent (the old
behaviour could turn €100 into ₪399.96). The dialog shows the other side live, and the main balance
is still checked against the amount that will actually leave it.

## History: details first, then edit
- Tapping a row no longer jumps into the editor. It opens **details** — amount and saved rate,
  category, account, method, note and date — with **Edit** offered as an explicit choice.
  Transfers have no editor: their saved rate is a fact, so they are deleted and re-created.
- A row shows the **time only while it is today**. After that it shows `21 Oct`, and older rows
  show `31 Dec 2025`. The list is still grouped by day, so this reads as detail within the day
  rather than a missing timestamp.
- Delete still asks for confirmation with a one-line consequence before touching balances.

## Carried in from 1.9.0's first pass
Multiple cash balances per main account, cash balances in their own currency, set-aside balances
excluded from the total, withdrawals listed as transfers (never spending), category/currency/
"last 30 days" filters that the view model already supported but the UI never exposed, and
confirmation dialogs on every destructive action.

## Compatibility and migrations
Same application ID and release signing key; install `app-release.apk` over 1.8.x without
uninstalling. Version code **9**, version name **1.9.0**, minSdk 26, targetSdk 35. Database
version **5** adds the `withdrawals` table and `accounts.includedInTotal` through an explicit
migration; JSON backups are format **5** and still import schema 1–4. The withdrawals table stores
both directions (`fromAccountId` / `toAccountId` may be the main or the cash side), so deposits
funded from cash restore correctly.

## Testing
GitHub Actions runs the full unit suite (25 suites, **149 tests**, real Room/Robolectric repository
and migration tests), lint, debug + signed release APKs, the AAB, and upgrade checks against
v1.8.0 (application id, version code, minSdk, signing certificate).

New coverage for this release: `TransferMathTest` (both typed sides, rounding, parity fallback),
`DepositFormStateTest` (external default, over-balance, conversion by rate, invalid input),
`HistoryTimestampTest` (today / same year / earlier), `HistoryAddMenuTest` (collapsed and expanded
pill menu), plus reverse-transfer and invalid-pair cases in `FinanceRepositoryTest` and updated
withdraw/Add-menu UI tests on regular and 240dp windows.
