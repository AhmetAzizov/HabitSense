package com.azizov.habitsense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.azizov.habitsense.data.local.entity.MoodLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MoodLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<MoodLogEntity>): List<Long>

    @Query("SELECT * FROM mood_logs WHERE dateString = :dateString LIMIT 1")
    suspend fun getByDate(dateString: String): MoodLogEntity?

    @Query(
        """
        SELECT * FROM mood_logs
        WHERE dateString BETWEEN :startDate AND :endDate
        ORDER BY dateString ASC
        """
    )
    suspend fun getBetweenDates(startDate: String, endDate: String): List<MoodLogEntity>

    @Query(
        """
        SELECT * FROM mood_logs
        WHERE dateString BETWEEN :startDate AND :endDate
        ORDER BY dateString ASC
        """
    )
    fun observeBetweenDates(startDate: String, endDate: String): Flow<List<MoodLogEntity>>

    @Query("DELETE FROM mood_logs")
    suspend fun deleteAll()
}
