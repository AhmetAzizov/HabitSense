package com.azizov.habitsense.domain.ai.baseline

import com.azizov.habitsense.domain.ai.complex.KMeansDayClusterer
import com.azizov.habitsense.domain.model.DailyData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineAndComplexStrategyTest {
    private val habitOrder = listOf(1L, 2L)

    private fun day(
        date: String,
        completed: Map<Long, Boolean>,
        mood: Int?,
        expenseTotal: Double
    ) = DailyData(
        dateString = date,
        habitCompletionById = completed,
        mood = mood,
        expenseTotal = expenseTotal,
        expenseCount = if (expenseTotal > 0) 1 else 0
    )

    @Test
    fun movingAverage_predictsPerHabitFromTrailingWindow() {
        val history = listOf(
            day("2026-07-01", mapOf(1L to true, 2L to false), 4, 20.0),
            day("2026-07-02", mapOf(1L to true, 2L to true), 3, 10.0),
            day("2026-07-03", mapOf(1L to false, 2L to true), 2, 30.0)
        )

        val insight = MovingAverageHabitPredictor(windowDays = 3).predict(
            history = history,
            targetDateString = "2026-07-04",
            habitOrder = habitOrder
        )

        val habit1 = insight.predictions.first { it.habitId == 1L }
        val habit2 = insight.predictions.first { it.habitId == 2L }
        assertEquals(2f / 3f, habit1.probabilityComplete, 0.0001f)
        assertEquals(2f / 3f, habit2.probabilityComplete, 0.0001f)
    }

    @Test
    fun heuristicClusterer_assignsReadableLabels() {
        val data = listOf(
            day("2026-07-01", mapOf(1L to true, 2L to true), 5, 20.0),
            day("2026-07-02", mapOf(1L to false, 2L to false), 2, 200.0)
        )

        val insight = HeuristicDayClusterer().cluster(data, habitOrder)
        assertEquals(2, insight.assignments.size)
        assertTrue(insight.assignments.any { it.label == "BalancedDay" })
        assertTrue(insight.assignments.any { it.label == "StressedSpendingDay" })
    }

    @Test
    fun spearman_detectsMonotonicRelationship() {
        val data = (1..10).map { index ->
            day(
                date = "2026-07-${index.toString().padStart(2, '0')}",
                completed = mapOf(1L to true, 2L to false),
                mood = index.coerceAtMost(5),
                expenseTotal = index * 10.0
            )
        }

        val insight = SpearmanCorrelationAnalyzer().analyze(data, habitOrder)
        val moodExpense = insight.pairwiseCorrelations.first {
            it.variableA == "mood" && it.variableB == "expense_total"
        }
        assertTrue(moodExpense.coefficient > 0.8f)
    }

    @Test
    fun kMeans_assignsEveryDayToCluster() {
        val data = listOf(
            day("2026-07-01", mapOf(1L to true, 2L to true), 5, 10.0),
            day("2026-07-02", mapOf(1L to false, 2L to false), 2, 150.0),
            day("2026-07-03", mapOf(1L to true, 2L to false), 3, 40.0)
        )

        val insight = KMeansDayClusterer(k = 2).cluster(data, habitOrder)
        assertEquals(3, insight.assignments.size)
        assertTrue(insight.assignments.all { it.clusterId in 0..1 })
    }
}
