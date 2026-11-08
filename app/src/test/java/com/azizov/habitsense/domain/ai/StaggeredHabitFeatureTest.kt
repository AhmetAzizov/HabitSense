package com.azizov.habitsense.domain.ai

import com.azizov.habitsense.domain.ai.baseline.HeuristicDayClusterer
import com.azizov.habitsense.domain.ai.baseline.MovingAverageHabitPredictor
import com.azizov.habitsense.domain.ai.baseline.SpearmanCorrelationAnalyzer
import com.azizov.habitsense.domain.ai.complex.AprioriCorrelationAnalyzer
import com.azizov.habitsense.domain.ai.complex.KMeansDayClusterer
import com.azizov.habitsense.domain.ai.dataset.HabitPredictionDatasetBuilder
import com.azizov.habitsense.domain.ai.feature.ClusteringFeatureBuilder
import com.azizov.habitsense.domain.ai.feature.HabitPredictionFeatureBuilder
import com.azizov.habitsense.domain.model.ClusteringFeatureSchema
import com.azizov.habitsense.domain.model.DailyData
import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One timeline where habit 2 does not exist on the first day.
 * Each analytics path must treat that day as "not applicable", not "missed".
 */
class StaggeredHabitFeatureTest {
    private val habitIds = listOf(1L, 2L)
    private val timeline = listOf(
        day(
            date = "2026-07-01",
            completed = mapOf(1L to true),
            activeHabitIds = setOf(1L),
            mood = 5,
            expenseTotal = 20.0
        ),
        day(
            date = "2026-07-02",
            completed = mapOf(1L to true, 2L to false),
            activeHabitIds = setOf(1L, 2L),
            mood = 4,
            expenseTotal = 30.0
        ),
        day(
            date = "2026-07-03",
            completed = mapOf(1L to false, 2L to true),
            activeHabitIds = setOf(1L, 2L),
            mood = 3,
            expenseTotal = 40.0
        )
    )

    @Test
    fun dailyData_distinguishesNotCreatedYetFromMissed() {
        val first = timeline[0]
        assertEquals(true, first.completionFor(1L))
        assertNull(first.completionFor(2L))
        assertEquals(1f, first.activeHabitCompletionRate(), 0.0001f)

        val second = timeline[1]
        assertEquals(false, second.completionFor(2L))
        assertEquals(0.5f, second.activeHabitCompletionRate(), 0.0001f)
    }

    @Test
    fun predictionFeatures_markPreCreationDaysInactiveWithoutCountingThemComplete() {
        val features = HabitPredictionFeatureBuilder(windowDays = 2).build(
            history = timeline.take(2),
            targetHabitId = 2L
        )

        assertEquals(HabitPredictionFeatureSchema.FEATURES_PER_DAY * 2, features.size)
        assertEquals(0f, features[feature(day = 0, name = "target_habit_completed")], 0.0001f)
        assertEquals(1f, features[feature(day = 0, name = "target_habit_inactive_or_missing")], 0.0001f)
        assertEquals(0f, features[feature(day = 1, name = "target_habit_completed")], 0.0001f)
        assertEquals(0f, features[feature(day = 1, name = "target_habit_inactive_or_missing")], 0.0001f)
        assertEquals(1f, features[feature(day = 0, name = "active_habit_completion_rate")], 0.0001f)
        assertEquals(0.5f, features[feature(day = 1, name = "active_habit_completion_rate")], 0.0001f)
    }

    @Test
    fun clusteringFeatures_ignoreHabitsThatDidNotExistYet() {
        val first = ClusteringFeatureBuilder().build(timeline[0])
        val second = ClusteringFeatureBuilder().build(timeline[1])

        assertEquals(1f, first[cluster("active_habit_completion_rate")], 0.0001f)
        assertEquals(1f / ClusteringFeatureSchema.ACTIVE_HABIT_COUNT_NORMALIZER, first[cluster("active_habit_count_norm")], 0.0001f)
        assertEquals(0.5f, second[cluster("active_habit_completion_rate")], 0.0001f)
        assertEquals(2f / ClusteringFeatureSchema.ACTIVE_HABIT_COUNT_NORMALIZER, second[cluster("active_habit_count_norm")], 0.0001f)

        val insight = KMeansDayClusterer(k = 2).cluster(timeline, habitIds)
        assertEquals(timeline.size, insight.assignments.size)
    }

    @Test
    fun movingAverage_usesOnlyDaysAfterTheHabitExisted() {
        val insight = MovingAverageHabitPredictor(windowDays = 3).predict(
            history = timeline,
            targetDateString = "2026-07-04",
            habitIds = habitIds
        )

        val habit1 = insight.predictions.first { it.habitId == 1L }
        val habit2 = insight.predictions.first { it.habitId == 2L }
        assertEquals(2f / 3f, habit1.probabilityComplete, 0.0001f)
        assertEquals(0.5f, habit2.probabilityComplete, 0.0001f)
    }

    @Test
    fun heuristicClusterer_doesNotPenalizeADayForFutureHabits() {
        val label = HeuristicDayClusterer().cluster(listOf(timeline[0]), habitIds)
            .assignments
            .single()
            .label
        assertEquals("BalancedDay", label)
    }

    @Test
    fun spearman_omitsAHabitUntilItHasEnoughActiveDays() {
        val insight = SpearmanCorrelationAnalyzer().analyze(timeline, habitIds)
        val mentioned = insight.pairwiseCorrelations.flatMap { listOf(it.variableA, it.variableB) }
        assertTrue(mentioned.contains("habit_1"))
        assertFalse(mentioned.contains("habit_2"))
    }

    @Test
    fun apriori_doesNotEmitAMissItemBeforeTheHabitExists() {
        val days = listOf(
            timeline[0],
            timeline[2].copy(
                dateString = "2026-07-02",
                habitCompletionById = mapOf(1L to true, 2L to true)
            )
        )
        val insight = AprioriCorrelationAnalyzer(minSupport = 0f, minConfidence = 0f)
            .analyze(days, habitIds)
        val items = insight.associationRules.flatMap { it.antecedent + it.consequent }

        assertFalse(items.contains("habit_2_miss"))
        assertTrue(items.contains("habit_2_done"))
    }

    @Test
    fun datasetRows_skipTargetDaysBeforeTheHabitExisted_andKeepInactiveHistory() {
        val examples = HabitPredictionDatasetBuilder().build(timeline, habitIds)
        val lastDay = HabitPredictionFeatureSchema.WINDOW_DAYS - 1

        assertTrue(examples.none { it.habitId == 2L && it.targetDateString == "2026-07-01" })
        val lateTarget = examples.single { it.habitId == 2L && it.targetDateString == "2026-07-02" }
        assertEquals(HabitPredictionFeatureSchema.DIMENSION, lateTarget.features.size)
        assertEquals(false, lateTarget.label)
        assertEquals(1f, lateTarget.features[feature(day = lastDay, name = "target_habit_inactive_or_missing")], 0.0001f)
        assertEquals(0f, lateTarget.features[feature(day = lastDay, name = "target_habit_completed")], 0.0001f)

        val bothActive = examples.single { it.habitId == 2L && it.targetDateString == "2026-07-03" }
        assertEquals(true, bothActive.label)
        assertEquals(1f, bothActive.features[feature(day = lastDay - 1, name = "target_habit_inactive_or_missing")], 0.0001f)
        assertEquals(0f, bothActive.features[feature(day = lastDay, name = "target_habit_inactive_or_missing")], 0.0001f)
        assertEquals(0f, bothActive.features[feature(day = lastDay, name = "target_habit_completed")], 0.0001f)
    }

    private fun feature(day: Int, name: String): Int {
        val offset = HabitPredictionFeatureSchema.FEATURE_NAMES_PER_DAY.indexOf(name)
        require(offset >= 0)
        return day * HabitPredictionFeatureSchema.FEATURES_PER_DAY + offset
    }

    private fun cluster(name: String): Int {
        val offset = ClusteringFeatureSchema.FEATURE_NAMES.indexOf(name)
        require(offset >= 0)
        return offset
    }

    private fun day(
        date: String,
        completed: Map<Long, Boolean>,
        activeHabitIds: Set<Long>,
        mood: Int,
        expenseTotal: Double
    ) = DailyData(
        dateString = date,
        habitCompletionById = completed,
        activeHabitIds = activeHabitIds,
        mood = mood,
        expenseTotal = expenseTotal,
        expenseCount = 1
    )
}
