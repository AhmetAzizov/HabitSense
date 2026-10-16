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
    val seed: Long,
    val profile: SyntheticDataProfile
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

        val habits = ensureHabits(config, random)
        val habitLogs = mutableListOf<HabitLogEntity>()
        val moodLogs = mutableListOf<MoodLogEntity>()
        val expenseLogs = mutableListOf<ExpenseLogEntity>()

        when (config.profile) {
            SyntheticDataProfile.INDEPENDENT -> generateIndependent(
                config = config,
                dates = dates,
                habits = habits,
                random = random,
                habitLogs = habitLogs,
                moodLogs = moodLogs,
                expenseLogs = expenseLogs
            )
            SyntheticDataProfile.ENRICHED -> generateEnriched(
                config = config,
                dates = dates,
                habits = habits,
                random = random,
                habitLogs = habitLogs,
                moodLogs = moodLogs,
                expenseLogs = expenseLogs
            )
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
            seed = config.seed,
            profile = config.profile
        )
    }

    private fun generateIndependent(
        config: SyntheticDataConfig,
        dates: List<String>,
        habits: List<HabitEntity>,
        random: Random,
        habitLogs: MutableList<HabitLogEntity>,
        moodLogs: MutableList<MoodLogEntity>,
        expenseLogs: MutableList<ExpenseLogEntity>
    ) {
        dates.forEach { dateString ->
            val activeHabits = habits.activeOn(dateString)
            activeHabits.forEach { habit ->
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
    }

    private fun generateEnriched(
        config: SyntheticDataConfig,
        dates: List<String>,
        habits: List<HabitEntity>,
        random: Random,
        habitLogs: MutableList<HabitLogEntity>,
        moodLogs: MutableList<MoodLogEntity>,
        expenseLogs: MutableList<ExpenseLogEntity>
    ) {
        val profiles = SyntheticBehaviorEngine.buildHabitProfiles(habits, config, random)
        val engine = SyntheticBehaviorEngine(config, profiles, random)

        dates.forEach { dateString ->
            val activeHabits = habits.activeOn(dateString)
            val day = engine.simulateDay(dateString, activeHabits)

            day.habitCompletions.forEach { (habitId, completed) ->
                habitLogs += HabitLogEntity(
                    habitId = habitId,
                    dateString = dateString,
                    completed = completed
                )
            }

            day.mood?.let { mood ->
                moodLogs += MoodLogEntity(dateString = dateString, mood = mood)
            }

            day.expenseAmounts.forEach { amount ->
                expenseLogs += ExpenseLogEntity(
                    dateString = dateString,
                    amount = amount,
                    note = null
                )
            }
        }
    }

    private suspend fun ensureHabits(
        config: SyntheticDataConfig,
        random: Random
    ): List<HabitEntity> {
        val existing = habitDao.getAll()
        if (existing.isNotEmpty()) return existing

        val habits = config.defaultHabitNames.mapIndexed { index, name ->
            val createdAt = if (config.habitCreationStaggerDays <= 0) {
                config.startDate
            } else {
                DateStringUtils.plusDays(
                    config.startDate,
                    index * config.habitCreationStaggerDays.toLong()
                )
            }
            HabitEntity(
                name = name,
                createdAtDateString = createdAt
            )
        }
        val ids = habitDao.insertAll(habits)
        return habits.mapIndexed { index, habit -> habit.copy(id = ids[index]) }
    }

    private fun List<HabitEntity>.activeOn(dateString: String): List<HabitEntity> =
        filter { habit -> habit.createdAtDateString <= dateString }
}
