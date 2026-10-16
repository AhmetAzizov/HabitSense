package com.azizov.habitsense.domain.ai.dataset

import com.azizov.habitsense.domain.ai.feature.HabitPredictionFeatureBuilder
import com.azizov.habitsense.domain.model.DailyData
import com.azizov.habitsense.domain.util.DateStringUtils

/**
 * Builds labeled training rows from ascending [DailyData], matching [HabitPredictor] semantics:
 * history excludes the target day; inactive habits on the target day are skipped.
 */
class HabitPredictionDatasetBuilder(
    private val featureBuilder: HabitPredictionFeatureBuilder = HabitPredictionFeatureBuilder()
) {
    fun build(
        dailyDataAscending: List<DailyData>,
        habitOrder: List<Long>
    ): List<HabitPredictionTrainingExample> {
        requireDailyDataSorted(dailyDataAscending)

        val examples = mutableListOf<HabitPredictionTrainingExample>()
        dailyDataAscending.forEachIndexed { targetIndex, targetDay ->
            val history = dailyDataAscending.subList(0, targetIndex)
            habitOrder.forEach { habitId ->
                if (!targetDay.isHabitActive(habitId)) return@forEach
                val completed = targetDay.completionFor(habitId) ?: return@forEach
                val features = featureBuilder.build(history, habitId)
                examples += HabitPredictionTrainingExample(
                    targetDateString = targetDay.dateString,
                    habitId = habitId,
                    label = completed,
                    features = features
                )
            }
        }
        return examples
    }

    private fun requireDailyDataSorted(dailyDataAscending: List<DailyData>) {
        dailyDataAscending.zipWithNext().forEach { (left, right) ->
            require(!DateStringUtils.parse(left.dateString).isAfter(DateStringUtils.parse(right.dateString))) {
                "dailyDataAscending must be sorted by dateString ascending"
            }
        }
    }
}
