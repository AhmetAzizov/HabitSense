package com.azizov.habitsense.data.synthetic

import com.azizov.habitsense.data.local.entity.HabitEntity
import com.azizov.habitsense.domain.util.DateStringUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SyntheticBehaviorEngineTest {
    private val config = SyntheticDataConfig(
        startDate = "2026-01-01",
        endDate = "2026-12-31",
        profile = SyntheticDataProfile.ENRICHED,
        moodLogProbability = 1f,
        expenseDayProbability = 0f,
        habitCreationStaggerDays = 0,
        moodLinkedCompletionStrength = 0.75f
    )

    private val habits = listOf(
        HabitEntity(id = 1L, name = "Exercise", createdAtDateString = "2026-01-01"),
        HabitEntity(id = 2L, name = "Reading", createdAtDateString = "2026-01-01")
    )

    @Test
    fun enriched_higherMood_increasesCompletionProbability() {
        val profiles = SyntheticBehaviorEngine.buildHabitProfiles(habits, config, Random(7))
        val engine = SyntheticBehaviorEngine(config, profiles, Random(8))
        val date = "2026-03-03"

        val lowMoodProbability = engine.completionProbability(1L, date, mood = 1)
        val highMoodProbability = engine.completionProbability(1L, date, mood = 5)

        assertTrue(highMoodProbability > lowMoodProbability + 0.08f)
    }

    @Test
    fun enriched_streakIncreasesNextDayCompletionProbability() {
        val profiles = SyntheticBehaviorEngine.buildHabitProfiles(
            habits,
            config,
            Random(1)
        )
        val engine = SyntheticBehaviorEngine(config, profiles, Random(2026))
        val habitId = 1L
        val startDate = "2026-03-01"

        val coldStart = engine.completionProbability(habitId, startDate, mood = 3)
        repeat(45) { offset ->
            engine.simulateDay(
                dateString = DateStringUtils.plusDays(startDate, offset.toLong()),
                activeHabits = habits
            )
        }
        val warmStreak = engine.completionProbability(
            habitId,
            DateStringUtils.plusDays(startDate, 45),
            mood = 3
        )

        assertTrue(warmStreak > coldStart + 0.03f)
    }

    @Test
    fun enriched_weekdayEffect_differsForExerciseOnMondayVsSunday() {
        val profiles = SyntheticBehaviorEngine.buildHabitProfiles(
            listOf(habits.first()),
            config,
            Random(3)
        )
        val profile = profiles.getValue(1L)
        val monday = profile.weekdayLogitOffsets[0]
        val sunday = profile.weekdayLogitOffsets[6]
        assertTrue(monday > sunday + 0.3f)
    }
}
