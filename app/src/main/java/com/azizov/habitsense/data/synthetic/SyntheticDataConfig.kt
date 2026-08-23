package com.azizov.habitsense.data.synthetic

data class SyntheticDataConfig(
    val seed: Long = 42L,
    val startDate: String,
    val endDate: String,
    val defaultHabitNames: List<String> = listOf("Exercise", "Reading", "Meditation", "Sleep Early"),
    val habitCompletionProbability: Float = 0.65f,
    val moodLogProbability: Float = 0.85f,
    val expenseDayProbability: Float = 0.75f,
    val maxExpensesPerDay: Int = 4,
    val minExpenseAmount: Double = 5.0,
    val maxExpenseAmount: Double = 250.0,
    val clearExistingData: Boolean = true
)
