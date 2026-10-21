# Components — shared UI primitives

All primitives live in `app/src/main/java/com/spendingtracker/app/ui/components/`.
Every one of them is compact-aware through `LocalCompactUi` / `LocalTightUi` /
`LocalAppDimens`, so a redesign must keep the same call signatures.

## `CompactControls.kt`

### AppTopBar
```kotlin
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
)
```
Surface + `statusBarsPadding()`, `heightIn(min = dims.topBarHeight)`, optional back
`AppIconButton`, bold `titleMedium` title, actions row on the right.

### AppIconButton
```kotlin
@Composable
fun AppIconButton(
    onClick: () -> Unit, icon: ImageVector, contentDescription: String,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    tint: Color = LocalContentColor.current
)
```
37dp (compact) / 44dp box, 8dp corner, 19/22dp icon, semantics contentDescription.

### AppButton / AppOutlinedButton / AppTextButton
```kotlin
@Composable fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(), shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues? = null, content: @Composable RowScope.() -> Unit)
@Composable fun AppOutlinedButton(...)   // same minus colors
@Composable fun AppTextButton(onClick, modifier, enabled, content)
```
All wrap `Box(Modifier.drawWithContent { … drawQinFocusRing(…) })` so the amber D-pad
ring is drawn outside the button. Buttons are `fillMaxWidth()`; compact content
padding is 8–12dp horizontal, 2–5dp vertical.

### AppFilterChip
```kotlin
@Composable fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true)
```
Hand-built pill (`heightIn(min = 30/36/40dp)`, `RoundedCornerShape(50)`), selected =
`secondaryContainer` + bold label, unselected = `surfaceVariant.copy(alpha = .5f)`.

### AppPill
```kotlin
@Composable fun AppPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    selected: Boolean = false, contentDescription: String? = null)
```
Account switcher / “See all” / “+ −” controls. Filled pill, `labelMedium` semibold.

### AppTextField
```kotlin
@Composable fun AppTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true, isError: Boolean = false, supportingText: String? = null,
    placeholder: String? = null, allowNegative: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Done, focusRequester: FocusRequester? = null,
    onImeAction: (() -> Unit)? = null)
```
Non-compact = `OutlinedTextField`. Compact = own label + `BasicTextField` in a bordered
box (1dp outline / 2dp primary when focused, 6dp corner, 32–38dp min height) with
hardware numeric key mapping (`hardwareChar`) and D-pad up/down focus moves.

### AppSelectorRow
```kotlin
@Composable fun AppSelectorRow(label: String, value: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingColor: Color? = null, placeholder: String = "Select")
```
Read-only “label ‹value› ›” row used instead of text field + picker button.

### AppDialog
```kotlin
@Composable fun AppDialog(onDismissRequest: () -> Unit, title: String,
    confirmLabel: String? = "OK", dismissLabel: String = "Cancel",
    confirmEnabled: Boolean = true, onConfirm: (() -> Unit)? = null,
    contentScrollable: Boolean = true, contentMaxHeightFraction: Float = 0.58f,
    content: @Composable ColumnScope.() -> Unit)
```
Platform-width-free dialog capped at 94% screen height; body scrolls, actions pinned.

### AppSectionHeader / AppValueRow / AppLoadingScreen / AppEmptyState / AppDot / AppHairline / FocusRingBox
```kotlin
@Composable fun AppSectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null)
@Composable fun AppValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = LocalContentColor.current)
@Composable fun AppLoadingScreen(modifier: Modifier = Modifier)
@Composable fun AppEmptyState(text: String, modifier: Modifier = Modifier)
@Composable fun AppDot(color: Color, modifier: Modifier = Modifier, sizeDp: Int = 9)
@Composable fun AppHairline(modifier: Modifier = Modifier)
@Composable fun FocusRingBox(focused: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit)
```

## `CategoryIcon.kt`
Colour dot + first letter of the category name for expense rows.

## `DatePickers.kt`
`AppDatePickerField` — compact date entry with an `AppDialog` calendar/text fallback.

## Icons
Only `androidx.compose.material.icons.Icons.Default` **core** icons are on the
classpath: `Add, ArrowBack, Delete, Edit, Home, List, Person, Settings, Star, Refresh,
Check, Close, Search, MoreVert, KeyboardArrowDown/Up/Left/Right`. Do **not** design
with `material-icons-extended` glyphs — the dependency is deliberately omitted for
APK size. Anything richer must be drawn with `Canvas` or set as text.
