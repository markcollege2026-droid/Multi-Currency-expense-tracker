package com.spendingtracker.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendingtracker.app.ui.theme.LocalAppPalette

/**
 * One entry of a pop-out Add menu. [emphasized] paints the label in the income
 * colour; the primary entry is rendered as a filled button.
 */
data class AddMenuOption(
    val label: String,
    val onClick: () -> Unit,
    val emphasized: Boolean = false,
    val primary: Boolean = false
)

/** How the options are drawn when the menu opens. */
enum class AddMenuStyle {
    /** Filled / outlined buttons that fill the row (Home). */
    Buttons,

    /** Compact pills, for a toolbar next to a search field (History). */
    Pills
}

/**
 * Animates one menu entry into place. Each index is delayed by [STAGGER_MS], so
 * the entries arrive one after another instead of as a block.
 */
@Composable
fun PopOutItem(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val delay = index * STAGGER_MS
    val exitDelay = index * (STAGGER_MS / 2)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.72f,
            animationSpec = tween(220, delayMillis = delay, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(180, delayMillis = delay)),
        exit = scaleOut(
            targetScale = 0.82f,
            animationSpec = tween(140, delayMillis = exitDelay, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(120, delayMillis = exitDelay))
    ) {
        content()
    }
}

/**
 * The row of options that pops out of an Add control.
 *
 * It is a [FlowRow] so three entries stay on one line on a phone and wrap on the
 * 240dp Qin window instead of being squeezed or clipped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMenuOptions(
    expanded: Boolean,
    options: List<AddMenuOption>,
    style: AddMenuStyle = AddMenuStyle.Buttons,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    AnimatedVisibility(
        visible = expanded,
        modifier = modifier,
        enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 3
        ) {
            options.forEachIndexed { index, option ->
                PopOutItem(index = index, visible = expanded) {
                    when (style) {
                        AddMenuStyle.Pills -> AppPill(
                            text = option.label,
                            onClick = option.onClick,
                            selected = option.primary,
                            contentDescription = option.label
                        )
                        AddMenuStyle.Buttons -> {
                            // Only the income-coloured entry overrides the button
                            // content colour; the filled one keeps onPrimary.
                            val label: @Composable () -> Unit = {
                                if (option.emphasized) {
                                    Text(text = option.label, maxLines = 1, color = palette.income)
                                } else {
                                    Text(text = option.label, maxLines = 1)
                                }
                            }
                            if (option.primary) {
                                AppButton(onClick = option.onClick, fillWidth = false) { label() }
                            } else {
                                AppOutlinedButton(onClick = option.onClick, fillWidth = false) { label() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Delay between two consecutive pop-out entries. */
const val STAGGER_MS = 60
