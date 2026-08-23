package com.azizov.habitsense.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.azizov.habitsense.data.local.entity.ExpenseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ExpenseLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<ExpenseLogEntity>): List<Long>

    @Query("SELECT * FROM expense_logs WHERE dateString = :dateString")
    suspend fun getByDate(dateString: String): List<ExpenseLogEntity>

    @Query(
        """
        SELECT * FROM expense_logs
        WHERE dateString BETWEEN :startDate AND :endDate
        ORDER BY dateString ASC
        """
    )
    suspend fun getBetweenDates(startDate: String, endDate: String): List<ExpenseLogEntity>

    @Query(
        """
        SELECT * FROM expense_logs
        WHERE dateString BETWEEN :startDate AND :endDate
        ORDER BY dateString ASC
        """
    )
    fun observeBetweenDates(startDate: String, endDate: String): Flow<List<ExpenseLogEntity>>

    @Query("DELETE FROM expense_logs")
    suspend fun deleteAll()
}
