package com.azizov.habitsense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_logs",
    indices = [Index(value = ["dateString"])]
)
data class ExpenseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String,
    val amount: Double,
    val note: String? = null
)
