package com.azizov.habitsense.domain.model

/**
 * Aggregated daily signals for one [dateString].
 * [habitOrder] defines the fixed index mapping used by [toFloatArray].
 */
data class DailyData(
    val dateString: String,
    val habitCompletionById: Map<Long, Boolean>,
    val mood: Int?,
    val expenseTotal: Double,
    val expenseCount: Int
) {
    fun toFloatArray(habitOrder: List<Long>): FloatArray {
        FeatureSchema.validateDimension(habitOrder, FeatureSchema.dimension(habitOrder.size))

        val vector = FloatArray(FeatureSchema.dimension(habitOrder.size))
        var index = 0

        habitOrder.forEach { habitId ->
            vector[index++] = if (habitCompletionById[habitId] == true) 1f else 0f
        }

        val moodMissing = mood == null
        vector[index++] = if (moodMissing) {
            0f
        } else {
            mood!!.toFloat() / FeatureSchema.MOOD_MAX.toFloat()
        }
        vector[index++] = if (moodMissing) 1f else 0f
        vector[index++] = (expenseTotal / FeatureSchema.EXPENSE_TOTAL_NORMALIZER).toFloat().coerceAtMost(1f)
        vector[index] = (expenseCount / 10f).coerceAtMost(1f)

        return vector
    }

    fun habitCompletionRate(habitOrder: List<Long>): Float {
        if (habitOrder.isEmpty()) return 0f
        val completed = habitOrder.count { habitCompletionById[it] == true }
        return completed.toFloat() / habitOrder.size.toFloat()
    }
}
