package com.azizov.habitsense.support

import com.azizov.habitsense.domain.ai.model.HabitPrediction
import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema
import com.azizov.habitsense.domain.model.DailyData

object PipelineTestSupport {
    fun expectedMovingAverage(
        history: List<DailyData>,
        habitId: Long,
        windowDays: Int = HabitPredictionFeatureSchema.WINDOW_DAYS
    ): Float {
        val trailing = history.takeLast(windowDays)
        val activeCompletions = trailing.mapNotNull { day -> day.completionFor(habitId) }
        if (activeCompletions.isEmpty()) return 0.5f
        return activeCompletions.count { it }.toFloat() / activeCompletions.size.toFloat()
    }

    fun predictionAccuracy(
        predictions: List<HabitPrediction>,
        targetDay: DailyData
    ): Pair<Int, Int> {
        var correct = 0
        var total = 0
        predictions.forEach { prediction ->
            val actual = targetDay.completionFor(prediction.habitId) ?: return@forEach
            total++
            if (prediction.predictedComplete == actual) correct++
        }
        return correct to total
    }
}
