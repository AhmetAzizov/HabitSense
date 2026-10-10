package com.azizov.habitsense.domain.ai.feature

import com.azizov.habitsense.domain.model.DailyData
import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema

class HabitPredictionFeatureBuilder(
    private val windowDays: Int = HabitPredictionFeatureSchema.WINDOW_DAYS
) {
    fun build(
        history: List<DailyData>,
        targetHabitId: Long
    ): FloatArray {
        val paddedWindow = padHistory(history.takeLast(windowDays))
        val features = FloatArray(windowDays * HabitPredictionFeatureSchema.FEATURES_PER_DAY)
        var index = 0

        paddedWindow.forEach { day ->
            val completion = day?.completionFor(targetHabitId)
            features[index++] = if (completion == true) 1f else 0f
            features[index++] = if (completion == null) 1f else 0f
            features[index++] = (day?.mood ?: 0).toFloat() / HabitPredictionFeatureSchema.MOOD_MAX
            features[index++] = if (day?.mood == null) 1f else 0f
            features[index++] = ((day?.expenseTotal ?: 0.0) / HabitPredictionFeatureSchema.EXPENSE_TOTAL_NORMALIZER)
                .toFloat()
                .coerceAtMost(1f)
            features[index++] = day?.activeHabitCompletionRate() ?: 0f
        }

        return features
    }

    private fun padHistory(history: List<DailyData>): List<DailyData?> {
        val missingCount = (windowDays - history.size).coerceAtLeast(0)
        return List(missingCount) { null } + history
    }
}
