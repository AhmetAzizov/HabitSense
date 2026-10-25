package com.azizov.habitsense.domain.model

object HabitPredictionFeatureSchema {
    const val VERSION = 1
    const val WINDOW_DAYS = 7
    const val FEATURES_PER_DAY = 6
    const val MOOD_MAX = 5f
    const val EXPENSE_TOTAL_NORMALIZER = 500.0

    val FEATURE_NAMES_PER_DAY = listOf(
        "target_habit_completed",
        "target_habit_inactive_or_missing",
        "mood_normalized",
        "mood_missing",
        "expense_total_norm",
        "active_habit_completion_rate"
    )

    const val DIMENSION = WINDOW_DAYS * FEATURES_PER_DAY

    fun featureNames(): List<String> {
        return (0 until WINDOW_DAYS).flatMap { dayIndex ->
            FEATURE_NAMES_PER_DAY.map { name -> "d${dayIndex}_$name" }
        }
    }
}

object ClusteringFeatureSchema {
    const val VERSION = 1
    const val DIMENSION = 6
    const val MOOD_MAX = 5f
    const val EXPENSE_TOTAL_NORMALIZER = 500.0
    const val EXPENSE_COUNT_NORMALIZER = 10f
    const val ACTIVE_HABIT_COUNT_NORMALIZER = 20f

    val FEATURE_NAMES = listOf(
        "mood_normalized",
        "mood_missing",
        "expense_total_norm",
        "expense_count_norm",
        "active_habit_completion_rate",
        "active_habit_count_norm"
    )
}
