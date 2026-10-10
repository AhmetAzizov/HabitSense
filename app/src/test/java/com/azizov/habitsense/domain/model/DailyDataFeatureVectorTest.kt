package com.azizov.habitsense.domain.model

import com.azizov.habitsense.domain.ai.feature.ClusteringFeatureBuilder
import com.azizov.habitsense.domain.ai.feature.HabitPredictionFeatureBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyDataFeatureVectorTest {
    private val habitOrder = listOf(1L, 2L, 3L)

    @Test
    fun toFloatArray_usesFixedOrderingAndMissingDefaults() {
        val day = DailyData(
            dateString = "2026-07-13",
            habitCompletionById = mapOf(1L to true, 2L to false, 3L to true),
            mood = null,
            expenseTotal = 100.0,
            expenseCount = 2
        )

        val vector = day.toFloatArray(habitOrder)
        assertEquals(FeatureSchema.dimension(habitOrder.size), vector.size)
        assertArrayEquals(
            floatArrayOf(1f, 0f, 1f, 0f, 1f, 0.2f, 0.2f),
            vector,
            0.0001f
        )
    }

    @Test
    fun featureNames_matchVectorDimension() {
        val names = FeatureSchema.featureNames(habitOrder)
        assertEquals(FeatureSchema.dimension(habitOrder.size), names.size)
    }

    @Test
    fun completionFor_returnsNullWhenHabitWasNotActive() {
        val day = DailyData(
            dateString = "2026-07-13",
            habitCompletionById = mapOf(1L to true),
            activeHabitIds = setOf(1L),
            mood = 4,
            expenseTotal = 10.0,
            expenseCount = 1
        )

        assertEquals(true, day.completionFor(1L))
        assertNull(day.completionFor(2L))
        assertEquals(1f, day.activeHabitCompletionRate(), 0.0001f)
    }

    @Test
    fun predictionBuilder_keepsFixedSizeWhenHabitCountChanges() {
        val history = listOf(
            DailyData(
                dateString = "2026-07-01",
                habitCompletionById = mapOf(1L to true),
                activeHabitIds = setOf(1L),
                mood = 4,
                expenseTotal = 20.0,
                expenseCount = 1
            ),
            DailyData(
                dateString = "2026-07-02",
                habitCompletionById = mapOf(1L to true, 2L to false),
                activeHabitIds = setOf(1L, 2L),
                mood = 3,
                expenseTotal = 30.0,
                expenseCount = 1
            )
        )

        val builder = HabitPredictionFeatureBuilder()
        assertEquals(HabitPredictionFeatureSchema.DIMENSION, builder.build(history, 1L).size)
        assertEquals(HabitPredictionFeatureSchema.DIMENSION, builder.build(history, 2L).size)
    }

    @Test
    fun clusteringBuilder_keepsFixedSizeWhenHabitCountChanges() {
        val day = DailyData(
            dateString = "2026-07-13",
            habitCompletionById = mapOf(1L to true, 2L to false, 3L to true),
            activeHabitIds = setOf(1L, 2L, 3L),
            mood = 5,
            expenseTotal = 75.0,
            expenseCount = 2
        )

        val vector = ClusteringFeatureBuilder().build(day)
        assertEquals(ClusteringFeatureSchema.DIMENSION, vector.size)
    }
}
