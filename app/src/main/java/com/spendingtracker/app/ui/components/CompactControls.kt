package com.spendingtracker.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spendingtracker.app.ui.compact.LocalAppDimens
import com.spendingtracker.app.ui.compact.LocalCompactUi
import com.spendingtracker.app.ui.compact.QinFocusColor
import com.spendingtracker.app.ui.compact.drawQinFocusRing
import com.spendingtracker.app.util.KeypadInput

/**
 * Top bar with status-bar padding. It is much shorter than Material's 64dp
 * TopAppBar on compact screens, which matters on a 320dp-high window.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val dims = LocalAppDimens.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = dims.topBarHeight)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                AppIconButton(
                    onClick = onBack,
                    icon = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            actions()
        }
    }
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current
) {
    val dims = LocalAppDimens.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(dims.iconButtonSize)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(dims.iconSize),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shape: androidx.compose.ui.graphics.Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit
) {
    val compact = LocalCompactUi.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (focused) drawQinFocusRing(color = QinFocusColor, corner = 10.dp)
        }
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = shape,
            colors = colors,
            contentPadding = contentPadding ?: if (compact) {
                PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            } else {
                ButtonDefaults.ContentPadding
            },
            interactionSource = source,
            content = content
        )
    }
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues? = null,
    content: @Composable RowScope.() -> Unit
) {
    val compact = LocalCompactUi.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (focused) drawQinFocusRing(color = QinFocusColor, corner = 10.dp)
        }
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            contentPadding = contentPadding ?: if (compact) {
                PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            } else {
                ButtonDefaults.ContentPadding
            },
            interactionSource = source,
            content = content
        )
    }
}

@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (focused) drawQinFocusRing(color = QinFocusColor, corner = 8.dp)
        }
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            interactionSource = source,
            content = content
        )
    }
}

@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Not a Material FilterChip: its 48dp interactive-size padding is too tall
    // for a 320dp-high window. The focus ring keeps it discoverable on D-pad.
    val compact = LocalCompactUi.current
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 30.dp else 40.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = if (compact) 7.dp else 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Small tappable pill used for the account switcher and compact filters. */
@Composable
fun AppPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentDescription: String? = null
) {
    val compact = LocalCompactUi.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = if (compact) 28.dp else 36.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Text field that collapses to a label + 32dp input on compact screens. It also
 * maps hardware number keys directly (the F21 keypad has no usable IME for a
 * numeric amount when the soft keyboard is hidden) and lets D-pad Up/Down leave
 * a single-line field instead of being swallowed as cursor movement.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    allowNegative: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Done,
    focusRequester: FocusRequester? = null,
    onImeAction: (() -> Unit)? = null
) {
    val compact = LocalCompactUi.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    if (!compact) {
        val actions = KeyboardActions(
            onDone = {
                onImeAction?.invoke()
                keyboard?.hide()
                focusManager.moveFocus(FocusDirection.Down)
            }
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            enabled = enabled,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = actions,
            modifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
        )
        return
    }

    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = {
                    onImeAction?.invoke()
                    keyboard?.hide()
                    focusManager.moveFocus(FocusDirection.Down)
                }
            ),
            visualTransformation = visualTransformation,
            interactionSource = source,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .heightIn(min = 32.dp)
                .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .pointerInput(Unit) {
                    // Do not consume the tap: BasicTextField still needs it to place the
                    // cursor. A tap always raises the IME, which keeps touchscreen entry
                    // working when the hardware keypad normally keeps it hidden.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                keyboard?.show()
                            }
                        }
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionDown -> {
                            keyboard?.hide()
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        event.key == Key.DirectionUp -> {
                            keyboard?.hide()
                            focusManager.moveFocus(FocusDirection.Up)
                            true
                        }
                        keyboardType == KeyboardType.Decimal || keyboardType == KeyboardType.Number -> {
                            val typed = hardwareChar(event.key)
                                ?: return@onPreviewKeyEvent false
                            val numeric = KeypadInput.appendChar(
                                current = value,
                                char = typed,
                                allowDecimal = keyboardType == KeyboardType.Decimal,
                                allowNegative = allowNegative
                            )
                            // Consume rejected keys too (e.g. a second decimal point)
                            // so the text field cannot insert a raw character behind us.
                            if (numeric != null) onValueChange(numeric)
                            true
                        }
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                            onImeAction?.invoke()
                            keyboard?.hide()
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        }
                        else -> false
                    }
                },
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(top = 1.dp, start = 2.dp)
            )
        }
    }
}

/** Compact row used instead of a read-only text field + separate "Pick" button. */
@Composable
fun AppSelectorRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingColor: Color? = null,
    placeholder: String = "Select"
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .semantics { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingColor != null) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(leadingColor)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value.ifBlank { placeholder },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Dialog that always fits the screen. Material's AlertDialog has a 280dp minimum
 * width and no height cap, which clips on 240x320dp. Buttons stay pinned below a
 * scrollable body, so the primary action is never hidden by a long form or IME.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String? = "OK",
    dismissLabel: String = "Cancel",
    confirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    contentScrollable: Boolean = true,
    contentMaxHeightFraction: Float = 0.58f,
    content: @Composable ColumnScope.() -> Unit
) {
    val compact = LocalCompactUi.current
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val maxScreenHeight = (screenHeight * 0.94f).dp
    val maxContentHeight = (screenHeight * contentMaxHeightFraction).dp
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 5.dp else 24.dp, vertical = 10.dp)
                .widthIn(max = 440.dp)
                .heightIn(max = maxScreenHeight)
                .imePadding(),
            shape = RoundedCornerShape(if (compact) 10.dp else 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (compact) 8.dp else 20.dp)
            ) {
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Column(
                    modifier = if (contentScrollable) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxContentHeight)
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp),
                    content = content
                )
                if (onConfirm != null || dismissLabel.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissLabel.isNotEmpty()) {
                            AppTextButton(onClick = onDismissRequest) {
                                Text(dismissLabel, maxLines = 1)
                            }
                        }
                        if (onConfirm != null && confirmLabel != null) {
                            Spacer(Modifier.width(4.dp))
                            AppButton(onClick = onConfirm, enabled = confirmEnabled) {
                                Text(confirmLabel, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Thin labelled divider used by the compact settings lists. */
@Composable
fun AppSectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun AppValueRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = LocalContentColor.current) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AppLoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
fun AppEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small colour dot used by category rows. */
@Composable
fun AppDot(color: Color, modifier: Modifier = Modifier, sizeDp: Int = 9) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** Cheap spring-less press feedback for rows that are not Material buttons. */
@Composable
fun rememberFocusSource(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
fun AppHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 3.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/** Keeps the focus ring from being clipped by a parent Surface. */
@Composable
fun FocusRingBox(focused: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (focused) drawQinFocusRing(corner = 8.dp)
        }
    ) {
        content()
    }
}

@Suppress("unused")
private val compactLabelSize = 9.sp

/** Maps the numeric keys of a phone-style hardware keypad to text characters. */
private fun hardwareChar(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    Key.Period, Key.NumPadDot, Key.Comma, Key.NumPadComma, Key.Multiply -> '.'
    Key.Minus, Key.NumPadSubtract -> '-'
    else -> null
}
