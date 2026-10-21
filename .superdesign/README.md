# Superdesign pass — "Ledger"

This folder holds the design work for the Spending Tracker UI, produced with the
**Superdesign** design skill (`superdesigndev/superdesign` → `superdesign-skill`,
`.claude/skills/superdesign`). It is design context plus mockups; the product code
stays in `app/src/main`.

```
.superdesign/
  design-system.md              ← source of truth: product context, tokens, components
  init/                         ← repository analysis the skill requires before designing
    components.md               shared Compose primitives + signatures
    layouts.md                  app shell, bottom nav, screen skeletons
    routes.md                   navigation destinations
    theme.md                    token summary + raw theme sources
    pages.md                    per-screen dependency trees
    extractable-components.md   components worth reusing
  design_iterations/            high-fidelity, single-file HTML mockups (390×844)
    home_1.html · history_1.html · add-expense_1.html
    converter_1.html · loans_1.html · settings_1.html
    index.html                  gallery of all six screens
    _build_mockups.py           regenerates every mockup from one token set
```

## How this was run

1. **Init** — the repository was analysed into the six files in `init/`
   (`components.md`, `layouts.md`, `routes.md`, `theme.md`, `pages.md`,
   `extractable-components.md`) instead of the web-oriented defaults, because this is
   a Jetpack Compose app: components are `@Composable` functions, layouts are the
   nav shell, and theme tokens are Compose `ColorScheme`/`Typography`/`Shapes` values.
2. **Design system** — `design-system.md` sets the visual language (_Ledger_: warm
   paper, forest accent, amber focus, hairline cards, tabular money) with light and
   dark tokens, a compact (Qin F21) variant, motion and accessibility rules.
3. **Design iterations** — six screens at the real phone size, each a self-contained
   HTML file (no external assets, no CDN), plus a gallery. Regenerate with:
   ```bash
   cd .superdesign/design_iterations && python3 _build_mockups.py
   ```
4. **Implementation** — the tokens and component patterns were applied to the Compose
   sources (see below), not left as a separate mockup.

> The Superdesign **canvas import** (`npx @superdesign/cli import-design-draft`) was
> not used: this environment has no Superdesign account or network access to
> superdesign.dev. The drafts therefore follow the skill's
> `references/design-with-your-model.md` contract — complete, self-contained HTML
> authored directly against the gathered context — and are previewed locally.

## Design → code map

| Design concept (`design-system.md`) | Where it lives in the app |
|---|---|
| Colour roles (light + dark) | `ui/theme/Color.kt` (`LightColorScheme`, `DarkColorScheme`) |
| Extra semantic colours (income, warning, hero gradient, card border) | `ui/theme/Color.kt` → `AppPalette`, `LocalAppPalette` |
| Brand no longer overridden by Material You | `ui/theme/Theme.kt` (`dynamicColor = false` default) |
| Shape scale 6/10/14/20/28 | `ui/theme/Shapes.kt` → `MaterialTheme.shapes` |
| Type scale, eyebrow, tabular money | `ui/theme/Type.kt` (`Typography`, `AppType`) |
| Card (flat fill, 1dp hairline, 20dp radius) | `ui/components/AppCards.kt` → `AppCard`, `AppSectionCard` |
| Stat tiles, avatars, list rows | `ui/components/AppCards.kt` → `AppStatTile`, `AppAvatar`, `AppListRow` |
| Top bar, buttons, chips, pills, fields, dialogs, empty states | `ui/components/CompactControls.kt` |
| Balance hero, recent rows, category bars, 7-day trend, tools | `ui/screens/home/HomeScreen.kt` |
| Day-grouped history rows | `ui/screens/transactions/TransactionsScreen.kt` |
| Loan summary + progress rows | `ui/screens/loans/LoansScreen.kt` |
| Tab bar with selected pill + hairline | `ui/screens/MainScaffold.kt` |
| Form structure (Amount / Details / Payment cards) | `AddExpenseScreen.kt`, `AddTopUpScreen.kt` |
| Section cards and theme picker | `ui/screens/settings/SettingsScreen.kt` |
| Onboarding | `ui/screens/setup/SetupScreen.kt` |

## Functionality pass (follow-up)

The first pass shipped styling; this pass closed functional gaps found by walking every
screen. Two categories:

**Broken**: the shared `AppDialog` rendered its confirm button inside an `End`-aligned
`Row` where both buttons were `fillMaxWidth`, so the confirm was measured at 0dp and
disappeared. Every confirmation dialog — "Add cash balance", "Delete account?", "Erase
all data?" — showed only *Cancel*. Actions are now a `FlowRow` (intrinsic widths, wraps
on 240dp), buttons gained `fillWidth`, and `AppDialogActionsTest` (Robolectric, regular +
240×320dp windows) locks the behaviour in.

**Missing options** added:

| Where | Added |
|---|---|
| Balance panel | Main/cash/card rows are tappable → *Edit balance* dialog (rename, low-balance warning, card credit limit, delete with confirmation) |
| Cash / card rows | *+ Cash income*, *Edit cash*, *Pay card*, *Autopay*, *+ Add credit card* are always reachable; card autopay status is shown as meta |
| Expense / income edit | *Date* picker (was display-only) and a delete action with confirmation |
| History | Category filter, currency filters, "Last 30 days", "Clear filters" and a filtered total — the view model supported these but the UI never exposed them |
| History / Recurring / Loans / Loan edit | Confirmation dialogs before delete; settle/reopen confirmation |
| Recurring empty state | Real call to action instead of a paragraph |

## Constraints the design respects

- **Two densities.** Regular phones/tablets and the Qin F21 Pro (240×320dp) share the
  tokens; compact steps sizes down but never below 9sp labels or 30dp targets.
- **Hardware-first.** Every interactive element is focusable and draws the amber D-pad
  ring (`QinFocusIndication`); the ring colour is untouched by the redesign because it
  is functional, not decorative.
- **Core icons only.** `material-icons-extended` is intentionally absent; the design
  uses text glyphs (↻, ⇄, ›, −, +) and the core icon set.
- **Offline.** No web fonts, no remote images, no CDN — mockups and app both render
  from local resources.
