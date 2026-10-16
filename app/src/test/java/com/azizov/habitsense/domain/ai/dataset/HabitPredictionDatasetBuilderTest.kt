package com.azizov.habitsense.domain.ai.dataset

import com.azizov.habitsense.domain.ai.feature.HabitPredictionFeatureBuilder
import com.azizov.habitsense.domain.model.DailyData
import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitPredictionDatasetBuilderTest {
    private val builder = HabitPredictionDatasetBuilder()
    private val habitOrder = listOf(1L, 2L)

    private fun day(
        date: String,
        completed: Map<Long, Boolean>,
        mood: Int? = 3,
        expenseTotal: Double = 10.0,
        activeHabitIds: Set<Long> = completed.keys
    ) = DailyData(
        dateString = date,
        habitCompletionById = completed,
        activeHabitIds = activeHabitIds,
        mood = mood,
        expenseTotal = expenseTotal,
        expenseCount = 1
    )

    @Test
    fun build_skipsInactiveHabitsOnTargetDay() {
        val daily = listOf(
            day("2026-07-01", mapOf(1L to true), activeHabitIds = setOf(1L)),
            day("2026-07-02", mapOf(1L to true), activeHabitIds = setOf(1L))
        )

        val examples = builder.build(daily, habitOrder)
        assertEquals(2, examples.size)
        assertTrue(examples.all { it.habitId == 1L })
    }

    @Test
    fun build_usesHistoryStrictlyBeforeTargetDay() {
        val daily = listOf(
            day("2026-07-01", mapOf(1L to true, 2L to false)),
            day("2026-07-02", mapOf(1L to false, 2L to true))
        )
        val featureBuilder = HabitPredictionFeatureBuilder()

        val examples = builder.build(daily, habitOrder)
        val targetExample = examples.first { it.targetDateString == "2026-07-02" && it.habitId == 1L }

        val expected = featureBuilder.build(daily.subList(0, 1), 1L)
        assertArrayEquals(expected, targetExample.features, 0.0001f)
        assertEquals(false, targetExample.label)
    }

    @Test
    fun build_emitsFixedFeatureDimension() {
        val daily = listOf(
            day("2026-07-01", mapOf(1L to true)),
            day("2026-07-02", mapOf(1L to true))
        )

        val examples = builder.build(daily, habitOrder)
        assertEquals(HabitPredictionFeatureSchema.DIMENSION, examples.first().features.size)
    }
}
