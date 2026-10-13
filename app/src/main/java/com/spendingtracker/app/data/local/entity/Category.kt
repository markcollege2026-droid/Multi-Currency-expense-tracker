package com.spendingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val color: Int,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun defaultCategories(): List<Category> = listOf(
            Category(name = "Food", iconName = "restaurant", color = 0xFF4CAF50.toInt(), isDefault = true),
            Category(name = "Transport", iconName = "directions_bus", color = 0xFF2196F3.toInt(), isDefault = true),
            Category(name = "Housing", iconName = "home", color = 0xFFFF9800.toInt(), isDefault = true),
            Category(name = "Bills", iconName = "receipt", color = 0xFFF44336.toInt(), isDefault = true),
            Category(name = "Health", iconName = "local_hospital", color = 0xFFE91E63.toInt(), isDefault = true),
            Category(name = "Shopping", iconName = "shopping_bag", color = 0xFF9C27B0.toInt(), isDefault = true),
            Category(name = "Entertainment", iconName = "movie", color = 0xFF00BCD4.toInt(), isDefault = true),
            Category(name = "Other", iconName = "category", color = 0xFF607D8B.toInt(), isDefault = true)
        )
    }
}
