package com.azizov.habitsense.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mood_logs",
    indices = [Index(value = ["dateString"], unique = true)]
)
data class MoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateString: String,
    val mood: Int
)
