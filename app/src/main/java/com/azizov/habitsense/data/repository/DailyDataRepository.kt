package com.azizov.habitsense.data.repository

import com.azizov.habitsense.data.local.dao.ExpenseLogDao
import com.azizov.habitsense.data.local.dao.HabitDao
import com.azizov.habitsense.data.local.dao.HabitLogDao
import com.azizov.habitsense.data.local.dao.MoodLogDao
import com.azizov.habitsense.data.local.entity.HabitEntity
import com.azizov.habitsense.data.local.entity.HabitLogEntity
import com.azizov.habitsense.domain.model.DailyData
import com.azizov.habitsense.domain.util.DateStringUtils

class DailyDataRepository(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val moodLogDao: MoodLogDao,
    private val expenseLogDao: ExpenseLogDao
) {
    suspend fun getHabitIds(): List<Long> = habitDao.getAll().map { it.id }

    suspend fun buildDailyData(dateString: String): DailyData {
        val habits = habitDao.getAll()
        return buildDailyDataForDate(dateString, habits)
    }

    suspend fun buildDailyDataRange(startDate: String, endDate: String): List<DailyData> {
        val habits = habitDao.getAll()
        val dates = DateStringUtils.datesBetweenInclusive(startDate, endDate)

        val habitLogs = habitLogDao.getBetweenDates(startDate, endDate)
            .groupBy { it.dateString }
        val moodLogs = moodLogDao.getBetweenDates(startDate, endDate)
            .associateBy { it.dateString }
        val expenseLogs = expenseLogDao.getBetweenDates(startDate, endDate)
            .groupBy { it.dateString }

        return dates.map { date ->
            buildDailyDataFromRaw(
                dateString = date,
                habits = habits,
                habitLogsForDay = habitLogs[date].orEmpty(),
                moodForDay = moodLogs[date]?.mood,
                expensesForDay = expenseLogs[date].orEmpty().map { it.amount }
            )
        }
    }

    suspend fun buildTrailingWindow(endDate: String, windowDays: Int): List<DailyData> {
        val startDate = DateStringUtils.minusDays(endDate, windowDays.toLong() - 1)
        return buildDailyDataRange(startDate, endDate)
    }

    private suspend fun buildDailyDataForDate(dateString: String, habits: List<HabitEntity>): DailyData {
        val habitLogs = habitLogDao.getByDate(dateString)
        val mood = moodLogDao.getByDate(dateString)?.mood
        val expenses = expenseLogDao.getByDate(dateString).map { it.amount }

        return buildDailyDataFromRaw(
            dateString = dateString,
            habits = habits,
            habitLogsForDay = habitLogs,
            moodForDay = mood,
            expensesForDay = expenses
        )
    }

    private fun buildDailyDataFromRaw(
        dateString: String,
        habits: List<HabitEntity>,
        habitLogsForDay: List<HabitLogEntity>,
        moodForDay: Int?,
        expensesForDay: List<Double>
    ): DailyData {
        val activeHabitIds = habits
            .filter { habit -> habit.createdAtDateString <= dateString }
            .map { habit -> habit.id }
            .toSet()
        val completionMap = activeHabitIds.associateWith { habitId ->
            habitLogsForDay.firstOrNull { it.habitId == habitId }?.completed ?: false
        }

        return DailyData(
            dateString = dateString,
            habitCompletionById = completionMap,
            activeHabitIds = activeHabitIds,
            mood = moodForDay,
            expenseTotal = expensesForDay.sum(),
            expenseCount = expensesForDay.size
        )
    }
}
