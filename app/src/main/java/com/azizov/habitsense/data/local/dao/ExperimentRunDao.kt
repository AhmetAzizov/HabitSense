package com.azizov.habitsense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.azizov.habitsense.data.local.entity.ExperimentRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExperimentRunDao {
    @Insert
    suspend fun insert(run: ExperimentRunEntity): Long

    @Query("SELECT * FROM experiment_runs ORDER BY timestamp DESC")
    suspend fun getAll(): List<ExperimentRunEntity>

    @Query("SELECT * FROM experiment_runs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ExperimentRunEntity>>

    @Query("SELECT * FROM experiment_runs WHERE strategyName = :strategyName ORDER BY timestamp DESC")
    suspend fun getByStrategy(strategyName: String): List<ExperimentRunEntity>
}
