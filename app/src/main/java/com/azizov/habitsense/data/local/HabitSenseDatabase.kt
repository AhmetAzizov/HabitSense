package com.azizov.habitsense.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.azizov.habitsense.data.local.dao.ExpenseLogDao
import com.azizov.habitsense.data.local.dao.ExperimentRunDao
import com.azizov.habitsense.data.local.dao.HabitDao
import com.azizov.habitsense.data.local.dao.HabitLogDao
import com.azizov.habitsense.data.local.dao.MoodLogDao
import com.azizov.habitsense.data.local.entity.ExpenseLogEntity
import com.azizov.habitsense.data.local.entity.ExperimentRunEntity
import com.azizov.habitsense.data.local.entity.HabitEntity
import com.azizov.habitsense.data.local.entity.HabitLogEntity
import com.azizov.habitsense.data.local.entity.MoodLogEntity

@Database(
    entities = [
        HabitEntity::class,
        HabitLogEntity::class,
        MoodLogEntity::class,
        ExpenseLogEntity::class,
        ExperimentRunEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HabitSenseDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun moodLogDao(): MoodLogDao
    abstract fun expenseLogDao(): ExpenseLogDao
    abstract fun experimentRunDao(): ExperimentRunDao
}
