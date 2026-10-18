package com.spendingtracker.app.ui.compact

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Ripple replacement that adds a high-contrast ring while a control has D-pad focus.
 * Used through LocalIndication so custom clickable rows, tabs and cards all get the
 * same visible focus treatment.
 */
class QinFocusIndication(
    private val focusColor: Color,
    private val pressedColor: Color
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingNode(interactionSource, focusColor, pressedColor)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QinFocusIndication) return false
        return focusColor == other.focusColor && pressedColor == other.pressedColor
    }

    override fun hashCode(): Int = 31 * focusColor.hashCode() + pressedColor.hashCode()
}

private class FocusRingNode(
    private val interactionSource: InteractionSource,
    private val focusColor: Color,
    private val pressedColor: Color
) : Modifier.Node(), DrawModifierNode {

    private var focused = false
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release -> pressed = false
                    is PressInteraction.Cancel -> pressed = false
                    else -> return@collect
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(pressedColor)
        if (focused) drawQinFocusRing(focusColor)
    }
}

fun DrawScope.drawQinFocusRing(
    color: Color = QinFocusColor,
    corner: Dp = 7.dp,
    stroke: Dp = 2.dp
) {
    val strokePx = stroke.toPx()
    val inset = strokePx / 2f
    val radius = CornerRadius(corner.toPx(), corner.toPx())
    val ringSize = Size(
        width = (size.width - strokePx).coerceAtLeast(0f),
        height = (size.height - strokePx).coerceAtLeast(0f)
    )
    // Dark backing line keeps the amber ring legible over light, dark and coloured surfaces.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.72f),
        topLeft = Offset(inset, inset),
        size = ringSize,
        cornerRadius = radius,
        style = Stroke(width = strokePx * 2f)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = ringSize,
        cornerRadius = radius,
        style = Stroke(width = strokePx)
    )
}
