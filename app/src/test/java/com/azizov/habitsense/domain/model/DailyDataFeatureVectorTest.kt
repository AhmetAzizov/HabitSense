package com.azizov.habitsense.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}
