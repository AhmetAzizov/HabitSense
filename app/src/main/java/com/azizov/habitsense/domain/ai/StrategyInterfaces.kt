package com.azizov.habitsense.domain.ai

import com.azizov.habitsense.domain.ai.model.HabitPredictionInsight
import com.azizov.habitsense.domain.model.DailyData

interface HabitPredictor {
    val strategyName: String
    val strategyVersion: String

    /**
     * Predict per-habit completion for [targetDateString] using trailing history.
     * [history] must be sorted ascending by date and exclude [targetDateString].
     */
    fun predict(
        history: List<DailyData>,
        targetDateString: String,
        habitOrder: List<Long>
    ): HabitPredictionInsight
}

interface DayClusterer {
    val strategyName: String
    val strategyVersion: String

    fun cluster(
        dailyData: List<DailyData>,
        habitOrder: List<Long>
    ): com.azizov.habitsense.domain.ai.model.DayClusterInsight
}

interface CorrelationAnalyzer {
    val strategyName: String
    val strategyVersion: String

    fun analyze(
        dailyData: List<DailyData>,
        habitOrder: List<Long>
    ): com.azizov.habitsense.domain.ai.model.CorrelationInsight
}
