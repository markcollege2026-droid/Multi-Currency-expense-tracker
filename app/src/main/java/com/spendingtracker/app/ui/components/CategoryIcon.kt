package com.spendingtracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryIcon(iconName: String, color: Int, modifier: Modifier = Modifier) {
    val bgColor = try { Color(color) } catch (e: Exception) { Color.Gray }
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (iconName) {
                "restaurant" -> "🍔"
                "directions_bus" -> "🚌"
                "home" -> "🏠"
                "receipt" -> "🧾"
                "local_hospital" -> "🏥"
                "shopping_bag" -> "🛍️"
                "movie" -> "🎬"
                else -> "📦"
            },
            fontSize = 20.sp
        )
    }
}
