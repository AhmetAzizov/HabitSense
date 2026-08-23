package com.azizov.habitsense.domain.model

/**
 * Fixed feature contract for ML input vectors.
 * Increment [VERSION] when feature order or semantics change.
 */
object FeatureSchema {
    const val VERSION = 1
    const val MOOD_MIN = 1
    const val MOOD_MAX = 5
    const val EXPENSE_TOTAL_NORMALIZER = 500.0

    /** Per-habit completion slots + mood + moodMissing + expenseTotal + expenseCount */
    fun dimension(habitCount: Int): Int = habitCount + 4

    fun featureNames(habitIds: List<Long>): List<String> {
        val names = habitIds.map { "habit_$it" }.toMutableList()
        names += listOf("mood_normalized", "mood_missing", "expense_total_norm", "expense_count_norm")
        return names
    }

    fun validateDimension(habitIds: List<Long>, vectorSize: Int) {
        require(vectorSize == dimension(habitIds.size)) {
            "Feature vector size mismatch. Expected ${dimension(habitIds.size)}, got $vectorSize"
        }
    }

    fun validateVersion(expectedVersion: Int) {
        require(expectedVersion == VERSION) {
            "Feature schema version mismatch. Expected $expectedVersion, current is $VERSION"
        }
    }
}
