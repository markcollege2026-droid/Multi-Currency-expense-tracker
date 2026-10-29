# Setting up the home-screen widget

The widget shows **total available** for the active ledger, one live conversion
rate and a **+ Expense** button that opens the expense form directly.

## Fastest path (1.12.3 and later)

1. Install the release APK and open the app once. The widget draws a cached
   snapshot, so the app has to run at least once to write one; before that it
   shows "Open the app".
2. **Settings → Home-screen widget → Add widget to home screen.**
3. The launcher asks you to confirm (or asks you to pick a home screen). Confirm.
   The widget appears immediately, already populated.

If that button instead prints instructions, this launcher cannot place a widget
on request (`requestPinAppWidget` is unsupported) — use the manual path below.

## Manual path (any launcher)

1. Open the app once, so a snapshot exists.
2. Go to the home screen and **long-press an empty spot**.
3. Tap **Widgets** (some launchers: a "Widgets" pill at the bottom, or the app
   drawer's "Widgets" tab).
4. Find **Spending Tracker** — in 1.12.2 and earlier it was listed as
   **Spending**; the debug build is **Spending Tracker (debug)**.
   Once expanded, the preview shows the balance line, the rate line and the
   **+ Expense** button.
5. Drag it onto a home screen. It prefers a 3x1 cell and can be resized; the
   minimum is one small cell (110x40dp).

### If it is not in the list at all

- **You are not on 1.12.3.** Before this version the label was "Spending" and the
  minimum size was 180x70dp, which cannot fit the grid on a ~320dp-wide screen;
  a provider whose minimum does not fit is not offered, without any error. Check
  **Settings → Data → Version** — it must read `1.12.3 (16) · <sha>`.
- **The launcher's widget list is cached.** Many launchers only rebuild it on
  restart: reboot the phone, or force-stop the launcher
  (Settings → Apps → *your launcher* → Force stop), then re-open it.
- **The debug build is installed instead of the release one.** Since 1.12.2 they
  have different package names (`com.spendingtracker.app` vs
  `com.spendingtracker.app.debug`) and both can be installed at once, each with
  its own widget entry.

## After it is placed

- **Tap the balance area** opens the app; **+ Expense** opens the expense form.
- It refreshes when the app pushes a new snapshot (app start, and after edits),
  and on a 12-hour, network-gated background refresh that only exists while a
  widget is placed.
- The third line says how old the figures are, and the rate line says
  "No … rate" rather than pretending a 1:1 conversion.
- Removing the last widget cancels the refresh work and clears the snapshot.

## How the widget is verified

- `app/src/androidTest/.../WidgetAndEntryTest.kt` (runs on an API 33 emulator)
  asserts that the **system** lists the provider
  (`AppWidgetManager.installedProviders`) — the same list the launcher's picker
  is built from — that the `appwidget-provider` metadata parses to a positive
  minimum size that fits the screen, that the layout only uses `RemoteViews`
  compatible views, that the snapshot survives a process-free reload, and that
  `MainActivity` is `singleTop` so the widget's button reuses the running task.
- `app/src/test/.../WidgetPinTest.kt` covers the in-app placement path and the
  fallback wording.
- `WidgetTextTest.kt` covers the wording of every line.
