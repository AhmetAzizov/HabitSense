package com.azizov.habitsense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.azizov.habitsense.data.local.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: HabitLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<HabitLogEntity>): List<Long>

    @Query("SELECT * FROM habit_logs WHERE dateString = :dateString")
    suspend fun getByDate(dateString: String): List<HabitLogEntity>

    @Query(
        """
        SELECT * FROM habit_logs
        WHERE dateString BETWEEN :startDate AND :endDate
        ORDER BY dateString ASC
        """
    )
    suspend fun getBetweenDates(startDate: String, endDate: String): List<HabitLogEntity>

    @Query(
        """
        SELECT * FROM habit_logs
        WHERE habitId = :habitId AND dateString BETWEEN :startDate AND :endDate
        ORDER BY dateString ASC
        """
    )
    suspend fun getHabitSequence(
        habitId: Long,
        startDate: String,
        endDate: String
    ): List<HabitLogEntity>

    @Query(
        """
        SELECT * FROM habit_logs
        WHERE dateString BETWEEN :startDate AND :endDate
        ORDER BY dateString ASC
        """
    )
    fun observeBetweenDates(startDate: String, endDate: String): Flow<List<HabitLogEntity>>

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAll()
}
