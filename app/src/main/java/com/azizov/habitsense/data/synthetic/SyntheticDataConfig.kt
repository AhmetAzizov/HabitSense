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
    val clearExistingData: Boolean = true,
    val profile: SyntheticDataProfile = SyntheticDataProfile.ENRICHED,
    /** Lag-1 autocorrelation of latent mood (ENRICHED Gaussian AR(1)). */
    val moodPersistence: Float = 0.65f,
    /** Mean of latent mood before rounding to 1..5 (ENRICHED). */
    val moodMean: Float = 3f,
    /** Long-run standard deviation of latent mood, independent of [moodPersistence] (ENRICHED). */
    val moodStdDev: Float = 1.1f,
    /** Logit shift per point of mood above/below neutral (3). */
    val moodLinkedCompletionStrength: Float = 0.45f,
    val streakBonusPerDay: Float = 0.32f,
    val maxStreakBonusDays: Int = 5,
    /** Higher spend on low-mood days (ENRICHED expenses). */
    val expenseLowMoodBoost: Float = 1.35f,
    /** Days between each default habit's [HabitEntity.createdAtDateString]; 0 = all on [startDate]. */
    val habitCreationStaggerDays: Int = 14
)


// TODO: ask if we can create separate config for both INDEPENDENT and ENRICHED
