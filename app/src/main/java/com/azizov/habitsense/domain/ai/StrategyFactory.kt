package com.azizov.habitsense.domain.ai

import android.content.Context
import com.azizov.habitsense.domain.ai.baseline.HeuristicDayClusterer
import com.azizov.habitsense.domain.ai.baseline.MovingAverageHabitPredictor
import com.azizov.habitsense.domain.ai.baseline.SpearmanCorrelationAnalyzer
import com.azizov.habitsense.domain.ai.complex.AprioriCorrelationAnalyzer
import com.azizov.habitsense.domain.ai.complex.KMeansDayClusterer
import com.azizov.habitsense.domain.ai.complex.TfliteHabitPredictor

enum class StrategyMode {
    BASELINE,
    COMPLEX
}

class StrategyFactory(private val context: Context) {
    fun habitPredictor(mode: StrategyMode): HabitPredictor = when (mode) {
        StrategyMode.BASELINE -> MovingAverageHabitPredictor()
        StrategyMode.COMPLEX -> TfliteHabitPredictor(context)
    }

    fun dayClusterer(mode: StrategyMode): DayClusterer = when (mode) {
        StrategyMode.BASELINE -> HeuristicDayClusterer()
        StrategyMode.COMPLEX -> KMeansDayClusterer()
    }

    fun correlationAnalyzer(mode: StrategyMode): CorrelationAnalyzer = when (mode) {
        StrategyMode.BASELINE -> SpearmanCorrelationAnalyzer()
        StrategyMode.COMPLEX -> AprioriCorrelationAnalyzer()
    }
}
