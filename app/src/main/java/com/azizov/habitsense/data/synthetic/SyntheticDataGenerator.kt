package com.azizov.habitsense.data.synthetic

import com.azizov.habitsense.data.local.dao.ExpenseLogDao
import com.azizov.habitsense.data.local.dao.HabitDao
import com.azizov.habitsense.data.local.dao.HabitLogDao
import com.azizov.habitsense.data.local.dao.MoodLogDao
import com.azizov.habitsense.data.local.entity.ExpenseLogEntity
import com.azizov.habitsense.data.local.entity.HabitEntity
import com.azizov.habitsense.data.local.entity.HabitLogEntity
import com.azizov.habitsense.data.local.entity.MoodLogEntity
import com.azizov.habitsense.domain.util.DateStringUtils
import kotlin.random.Random

data class SyntheticGenerationResult(
    val habitCount: Int,
    val habitLogCount: Int,
    val moodLogCount: Int,
    val expenseLogCount: Int,
    val dayCount: Int,
    val seed: Long
)

class SyntheticDataGenerator(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val moodLogDao: MoodLogDao,
    private val expenseLogDao: ExpenseLogDao
) {
    suspend fun generate(config: SyntheticDataConfig): SyntheticGenerationResult {
        require(DateStringUtils.isValid(config.startDate))
        require(DateStringUtils.isValid(config.endDate))

        val random = Random(config.seed)
        val dates = DateStringUtils.datesBetweenInclusive(config.startDate, config.endDate)

        if (config.clearExistingData) {
            expenseLogDao.deleteAll()
            moodLogDao.deleteAll()
            habitLogDao.deleteAll()
            habitDao.deleteAll()
        }

        val habits = ensureHabits(config)
        val habitLogs = mutableListOf<HabitLogEntity>()
        val moodLogs = mutableListOf<MoodLogEntity>()
        val expenseLogs = mutableListOf<ExpenseLogEntity>()

        dates.forEach { dateString ->
            habits.forEach { habit ->
                val completed = random.nextFloat() < config.habitCompletionProbability
                habitLogs += HabitLogEntity(
                    habitId = habit.id,
                    dateString = dateString,
                    completed = completed
                )
            }

            if (random.nextFloat() < config.moodLogProbability) {
                moodLogs += MoodLogEntity(
                    dateString = dateString,
                    mood = random.nextInt(1, 6)
                )
            }

            if (random.nextFloat() < config.expenseDayProbability) {
                val expenseCount = random.nextInt(1, config.maxExpensesPerDay + 1)
                repeat(expenseCount) {
                    val amount = config.minExpenseAmount +
                        random.nextDouble() * (config.maxExpenseAmount - config.minExpenseAmount)
                    expenseLogs += ExpenseLogEntity(
                        dateString = dateString,
                        amount = amount,
                        note = null
                    )
                }
            }
        }

        habitLogDao.insertAll(habitLogs)
        moodLogDao.insertAll(moodLogs)
        expenseLogDao.insertAll(expenseLogs)

        return SyntheticGenerationResult(
            habitCount = habits.size,
            habitLogCount = habitLogs.size,
            moodLogCount = moodLogs.size,
            expenseLogCount = expenseLogs.size,
            dayCount = dates.size,
            seed = config.seed
        )
    }

    private suspend fun ensureHabits(
        config: SyntheticDataConfig
    ): List<HabitEntity> {
        val existing = habitDao.getAll()
        if (existing.isNotEmpty()) return existing

        val habits = config.defaultHabitNames.map { name ->
            HabitEntity(
                name = name,
                createdAtDateString = config.startDate
            )
        }
        val ids = habitDao.insertAll(habits)
        return habits.mapIndexed { index, habit -> habit.copy(id = ids[index]) }
    }
}
