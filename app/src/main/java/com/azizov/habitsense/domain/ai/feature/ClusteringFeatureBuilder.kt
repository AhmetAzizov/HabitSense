package com.azizov.habitsense.domain.ai.feature

import com.azizov.habitsense.domain.model.ClusteringFeatureSchema
import com.azizov.habitsense.domain.model.DailyData

class ClusteringFeatureBuilder {
    fun build(day: DailyData): FloatArray {
        return floatArrayOf(
            (day.mood ?: 0).toFloat() / ClusteringFeatureSchema.MOOD_MAX,
            if (day.mood == null) 1f else 0f,
            (day.expenseTotal / ClusteringFeatureSchema.EXPENSE_TOTAL_NORMALIZER).toFloat().coerceAtMost(1f),
            (day.expenseCount / ClusteringFeatureSchema.EXPENSE_COUNT_NORMALIZER).coerceAtMost(1f),
            day.activeHabitCompletionRate(),
            (day.activeHabitIds.size / ClusteringFeatureSchema.ACTIVE_HABIT_COUNT_NORMALIZER).coerceAtMost(1f)
        )
    }
}
