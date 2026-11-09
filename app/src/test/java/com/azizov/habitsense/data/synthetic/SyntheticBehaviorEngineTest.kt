package com.azizov.habitsense.data.synthetic

import com.azizov.habitsense.data.local.entity.HabitEntity
import com.azizov.habitsense.domain.util.DateStringUtils
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun enriched_moodDistribution_usesFullScaleAcrossSeeds() {
        val yearConfig = config.copy(
            startDate = "2025-01-01",
            endDate = "2025-12-31",
            moodLogProbability = 1f,
            expenseDayProbability = 0f
        )
        val seeds = listOf(42L, 1L, 2L, 3L, 4L, 5L)

        seeds.forEach { seed ->
            val moods = simulateYearMoods(yearConfig, seed)
            val levelsPresent = (1..5).all { level -> moods.any { it == level } }
            assertTrue("seed=$seed should hit all mood levels", levelsPresent)

            val mean = moods.average()
            assertTrue("seed=$seed mean mood $mean", mean in 2.4..3.6)

            val highShare = moods.count { it >= 4 }.toFloat() / moods.size
            assertTrue("seed=$seed high mood share $highShare", highShare in 0.12f..0.50f)
        }
    }

    @Test
    fun enriched_moodPersistence_correlatesAdjacentLoggedDays() {
        val yearConfig = config.copy(
            startDate = "2025-01-01",
            endDate = "2025-12-31",
            moodLogProbability = 1f,
            expenseDayProbability = 0f
        )

        val persistent = SyntheticBehaviorEngine(
            yearConfig,
            SyntheticBehaviorEngine.buildHabitProfiles(habits, yearConfig, Random(1)),
            Random(42)
        )
        val independent = SyntheticBehaviorEngine(
            yearConfig.copy(moodPersistence = 0f),
            SyntheticBehaviorEngine.buildHabitProfiles(habits, yearConfig, Random(1)),
            Random(42)
        )

        val moodsPersistent = simulateMoodsWithEngine(persistent, yearConfig)
        val moodsIndependent = simulateMoodsWithEngine(independent, yearConfig)

        val rhoPersistent = lag1Correlation(moodsPersistent)
        val rhoIndependent = lag1Correlation(moodsIndependent)

        assertTrue("persistent rho=$rhoPersistent", rhoPersistent > 0.35f)
        assertTrue("independent |rho|=${abs(rhoIndependent)}", abs(rhoIndependent) < 0.25f)
    }

    @Test
    fun enriched_moodMissingness_respectsLogProbability() {
        val partialConfig = config.copy(
            startDate = "2025-01-01",
            endDate = "2025-12-31",
            moodLogProbability = 0.85f,
            expenseDayProbability = 0f
        )
        val engine = SyntheticBehaviorEngine(
            partialConfig,
            SyntheticBehaviorEngine.buildHabitProfiles(habits, partialConfig, Random(2)),
            Random(7)
        )

        val dayCount = DateStringUtils.datesBetweenInclusive(partialConfig.startDate, partialConfig.endDate).size
        val logged = mutableListOf<Int>()
        var missing = 0
        DateStringUtils.datesBetweenInclusive(partialConfig.startDate, partialConfig.endDate).forEach { date ->
            val mood = engine.simulateDay(date, habits).mood
            if (mood == null) {
                missing++
            } else {
                assertTrue(mood in 1..5)
                logged += mood
            }
        }

        val missingShare = missing.toFloat() / dayCount
        assertTrue("missing share $missingShare", missingShare in 0.08f..0.25f)
        assertEquals(dayCount, logged.size + missing)
    }

    @Test
    fun enriched_moodSequence_isDeterministicForSameSeed() {
        val yearConfig = config.copy(
            startDate = "2025-01-01",
            endDate = "2025-12-31",
            moodLogProbability = 1f,
            expenseDayProbability = 0f
        )

        val first = simulateYearMoods(yearConfig, seed = 99L)
        val second = simulateYearMoods(yearConfig, seed = 99L)
        assertEquals(first, second)
    }

    private fun simulateYearMoods(config: SyntheticDataConfig, seed: Long): List<Int> {
        val engine = SyntheticBehaviorEngine(
            config,
            SyntheticBehaviorEngine.buildHabitProfiles(habits, config, Random(0)),
            Random(seed)
        )
        return simulateMoodsWithEngine(engine, config)
    }

    private fun simulateMoodsWithEngine(
        engine: SyntheticBehaviorEngine,
        config: SyntheticDataConfig
    ): List<Int> {
        return DateStringUtils.datesBetweenInclusive(config.startDate, config.endDate).mapNotNull { date ->
            engine.simulateDay(date, habits).mood
        }
    }

    private fun lag1Correlation(values: List<Int>): Float {
        require(values.size >= 3)
        val x = values.dropLast(1).map { it.toFloat() }
        val y = values.drop(1).map { it.toFloat() }
        val meanX = x.average().toFloat()
        val meanY = y.average().toFloat()
        var num = 0f
        var denX = 0f
        var denY = 0f
        for (i in x.indices) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        if (denX == 0f || denY == 0f) return 0f
        return (num / sqrt(denX * denY)).coerceIn(-1f, 1f)
    }
}
