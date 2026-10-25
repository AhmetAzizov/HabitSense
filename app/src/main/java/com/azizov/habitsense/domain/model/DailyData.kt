package com.azizov.habitsense.domain.model

/**
 * Aggregated daily signals for one [dateString].
 * [activeHabitIds] distinguishes a missed habit from a habit that did not exist yet.
 */
data class DailyData(
    val dateString: String,
    val habitCompletionById: Map<Long, Boolean>,
    val activeHabitIds: Set<Long> = habitCompletionById.keys,
    val mood: Int?,
    val expenseTotal: Double,
    val expenseCount: Int
) {
    fun isHabitActive(habitId: Long): Boolean = habitId in activeHabitIds

    fun completionFor(habitId: Long): Boolean? {
        if (!isHabitActive(habitId)) return null
        return habitCompletionById[habitId] ?: false
    }

    fun activeHabitCompletionRate(): Float {
        if (activeHabitIds.isEmpty()) return 0f
        val completed = activeHabitIds.count { habitCompletionById[it] == true }
        return completed.toFloat() / activeHabitIds.size.toFloat()
    }
}
