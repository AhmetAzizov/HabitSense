package com.azizov.habitsense.domain.ai

import com.azizov.habitsense.domain.ai.model.CorrelationInsight
import com.azizov.habitsense.domain.ai.model.DayClusterInsight
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
        habitIds: List<Long>
    ): HabitPredictionInsight
}

interface DayClusterer {
    val strategyName: String
    val strategyVersion: String

    fun cluster(
        dailyData: List<DailyData>,
        habitIds: List<Long>
    ): DayClusterInsight
}

interface CorrelationAnalyzer {
    val strategyName: String
    val strategyVersion: String

    fun analyze(
        dailyData: List<DailyData>,
        habitIds: List<Long>
    ): CorrelationInsight
}
