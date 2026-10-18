package com.spendingtracker.app.ui.compact

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ripple replacement that adds a high-contrast ring while a control has D-pad focus.
 * Used through LocalIndication so custom clickable rows, tabs and cards all get the
 * same visible focus treatment.
 */
class QinFocusIndication(
    private val focusColor: Color,
    private val pressedColor: Color
) : Indication {

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        val focused by interactionSource.collectIsFocusedAsState()
        val pressed by interactionSource.collectIsPressedAsState()
        return remember(focused, pressed) {
            object : IndicationInstance {
                override fun ContentDrawScope.drawIndication() {
                    drawContent()
                    if (pressed) {
                        drawRect(color = pressedColor)
                    }
                    if (focused) {
                        drawQinFocusRing(focusColor)
                    }
                }
            }
        }
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
