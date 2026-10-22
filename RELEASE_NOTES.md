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
