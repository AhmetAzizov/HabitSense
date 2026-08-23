package com.azizov.habitsense.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var instance: HabitSenseDatabase? = null

    fun get(context: Context): HabitSenseDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HabitSenseDatabase::class.java,
                "habitsense.db"
            ).build().also { instance = it }
        }
    }
}
