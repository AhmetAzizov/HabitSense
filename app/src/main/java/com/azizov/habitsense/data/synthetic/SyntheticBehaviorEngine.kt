package com.azizov.habitsense.data.synthetic

import com.azizov.habitsense.data.local.entity.HabitEntity
import com.azizov.habitsense.domain.util.DateStringUtils
import kotlin.math.exp
import kotlin.random.Random
import java.time.DayOfWeek

internal data class HabitBehaviorProfile(
    val baseLogit: Float,
    val moodSensitivity: Float,
    /** Index 0 = Monday … 6 = Sunday (ISO [DayOfWeek]). */
    val weekdayLogitOffsets: FloatArray
)

internal data class SimulatedDay(
    val mood: Int?,
    val expenseAmounts: List<Double>,
    val habitCompletions: Map<Long, Boolean>
)

/**
 * Stateful simulator for one user timeline. Pure Kotlin (no Room) for unit tests and thesis docs.
 */
internal class SyntheticBehaviorEngine(
    private val config: SyntheticDataConfig,
    private val habitProfiles: Map<Long, HabitBehaviorProfile>,
    private val random: Random
) {
    private var previousMood: Int? = null
    private val completionStreakByHabit = mutableMapOf<Long, Int>()

    fun simulateDay(dateString: String, activeHabits: List<HabitEntity>): SimulatedDay {
        val mood = sampleMood()
        val expenses = sampleExpenses(mood)
        val completions = activeHabits.associate { habit ->
            val completed = sampleHabitCompletion(habit, dateString, mood)
            updateStreak(habit.id, completed)
            habit.id to completed
        }
        return SimulatedDay(mood, expenses, completions)
    }

    fun completionProbability(
        habitId: Long,
        dateString: String,
        mood: Int?
    ): Float {
        val profile = habitProfiles[habitId] ?: return config.habitCompletionProbability
        val streak = completionStreakByHabit[habitId] ?: 0
        val logit = enrichedLogit(profile, dateString, mood, streak)
        return sigmoid(logit)
    }

    private fun sampleMood(): Int? {
        if (random.nextFloat() >= config.moodLogProbability) return null

        val drawn = random.nextInt(1, 6)
        val blended = if (previousMood == null) {
            drawn
        } else {
            val persistence = config.moodPersistence.coerceIn(0f, 1f)
            val value = persistence * previousMood!! + (1f - persistence) * drawn
            value.toInt().coerceIn(1, 5)
        }
        previousMood = blended
        return blended
    }

    private fun sampleExpenses(mood: Int?): List<Double> {
        if (random.nextFloat() >= config.expenseDayProbability) return emptyList()

        val expenseCount = random.nextInt(1, config.maxExpensesPerDay + 1)
        val moodStress = when (mood) {
            null -> 1f
            else -> 1f + config.expenseLowMoodBoost * ((3f - mood) / 2f).coerceAtLeast(0f)
        }

        return List(expenseCount) {
            val raw = config.minExpenseAmount +
                random.nextDouble() * (config.maxExpenseAmount - config.minExpenseAmount)
            (raw * moodStress).coerceAtMost(config.maxExpenseAmount * config.expenseLowMoodBoost)
        }
    }

    private fun sampleHabitCompletion(
        habit: HabitEntity,
        dateString: String,
        mood: Int?
    ): Boolean {
        val profile = habitProfiles[habit.id]
            ?: return random.nextFloat() < config.habitCompletionProbability
        val streak = completionStreakByHabit[habit.id] ?: 0
        val logit = enrichedLogit(profile, dateString, mood, streak)
        return random.nextFloat() < sigmoid(logit)
    }

    private fun enrichedLogit(
        profile: HabitBehaviorProfile,
        dateString: String,
        mood: Int?,
        streak: Int
    ): Float {
        val dayIndex = DateStringUtils.parse(dateString).dayOfWeek.isoIndex()
        val weekdayOffset = profile.weekdayLogitOffsets[dayIndex]
        val moodCentered = ((mood ?: 3) - 3f) / 2f
        val cappedStreak = streak.coerceAtMost(config.maxStreakBonusDays)
        val streakBonus = cappedStreak * config.streakBonusPerDay
        return profile.baseLogit + weekdayOffset + profile.moodSensitivity * moodCentered + streakBonus
    }

    private fun updateStreak(habitId: Long, completed: Boolean) {
        if (completed) {
            completionStreakByHabit[habitId] = (completionStreakByHabit[habitId] ?: 0) + 1
        } else {
            completionStreakByHabit[habitId] = 0
        }
    }

    companion object {
        fun buildHabitProfiles(
            habits: List<HabitEntity>,
            config: SyntheticDataConfig,
            random: Random
        ): Map<Long, HabitBehaviorProfile> {
            val baseLogit = probabilityToLogit(config.habitCompletionProbability)
            return habits.associate { habit ->
                val jitter = random.nextFloat() * 0.5f - 0.25f
                val moodSensitivity = config.moodLinkedCompletionStrength *
                    (0.75f + random.nextFloat() * 0.5f)
                habit.id to HabitBehaviorProfile(
                    baseLogit = baseLogit + jitter,
                    moodSensitivity = moodSensitivity,
                    weekdayLogitOffsets = weekdayOffsetsForHabit(habit.name, random)
                )
            }
        }

        private fun weekdayOffsetsForHabit(name: String, random: Random): FloatArray {
            val offsets = FloatArray(7)
            when {
                name.contains("exercise", ignoreCase = true) -> {
                    offsets[0] = 0.55f
                    offsets[2] = 0.45f
                    offsets[4] = 0.35f
                    offsets[6] = -0.4f
                }
                name.contains("read", ignoreCase = true) -> {
                    offsets[5] = 0.25f
                    offsets[6] = 0.35f
                    offsets[0] = -0.15f
                }
                name.contains("meditat", ignoreCase = true) -> {
                    offsets[1] = 0.3f
                    offsets[3] = 0.3f
                    offsets[5] = -0.25f
                }
                name.contains("sleep", ignoreCase = true) -> {
                    offsets[5] = -0.55f
                    offsets[6] = -0.45f
                    offsets[0] = 0.2f
                    offsets[1] = 0.15f
                }
                else -> {
                    repeat(7) { index ->
                        offsets[index] = random.nextFloat() * 0.4f - 0.2f
                    }
                }
            }
            return offsets
        }

        fun probabilityToLogit(probability: Float): Float {
            val p = probability.coerceIn(0.01f, 0.99f)
            return kotlin.math.ln(p / (1f - p))
        }

        fun sigmoid(logit: Float): Float = (1f / (1f + exp(-logit.toDouble()))).toFloat()
    }
}

private fun DayOfWeek.isoIndex(): Int = value - 1
