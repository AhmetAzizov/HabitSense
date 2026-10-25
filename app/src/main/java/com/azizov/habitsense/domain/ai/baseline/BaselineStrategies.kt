package com.azizov.habitsense.domain.ai.baseline

import com.azizov.habitsense.domain.ai.CorrelationAnalyzer
import com.azizov.habitsense.domain.ai.DayClusterer
import com.azizov.habitsense.domain.ai.HabitPredictor
import com.azizov.habitsense.domain.ai.model.AssociationRule
import com.azizov.habitsense.domain.ai.model.CorrelationInsight
import com.azizov.habitsense.domain.ai.model.CorrelationPair
import com.azizov.habitsense.domain.ai.model.DayClusterAssignment
import com.azizov.habitsense.domain.ai.model.DayClusterInsight
import com.azizov.habitsense.domain.ai.model.HabitPrediction
import com.azizov.habitsense.domain.ai.model.HabitPredictionInsight
import com.azizov.habitsense.domain.model.DailyData
import kotlin.math.abs

class MovingAverageHabitPredictor(
    private val windowDays: Int = 7
) : HabitPredictor {
    override val strategyName: String = "MovingAverage"
    override val strategyVersion: String = "1.0"

    override fun predict(
        history: List<DailyData>,
        targetDateString: String,
        habitIds: List<Long>
    ): HabitPredictionInsight {
        val trailing = history.takeLast(windowDays)
        val predictions = habitIds.map { habitId ->
            val activeHistory = trailing.mapNotNull { day -> day.completionFor(habitId) }
            val rate = if (activeHistory.isEmpty()) {
                0.5f
            } else {
                val completedCount = activeHistory.count { it }
                completedCount.toFloat() / activeHistory.size.toFloat()
            }
            HabitPrediction(
                habitId = habitId,
                probabilityComplete = rate,
                predictedComplete = rate >= 0.5f
            )
        }

        return HabitPredictionInsight(
            targetDateString = targetDateString,
            predictions = predictions,
            strategyName = strategyName,
            strategyVersion = strategyVersion
        )
    }
}

class HeuristicDayClusterer : DayClusterer {
    override val strategyName: String = "HeuristicRules"
    override val strategyVersion: String = "1.0"

    override fun cluster(
        dailyData: List<DailyData>,
        habitIds: List<Long>
    ): DayClusterInsight {
        val assignments = dailyData.map { day ->
            val completionRate = day.activeHabitCompletionRate()
            val mood = day.mood ?: 3
            val spend = day.expenseTotal

            val (clusterId, label) = when {
                mood >= 4 && spend < 50 && completionRate >= 0.7f ->
                    0 to "BalancedDay"
                mood <= 2 || spend > 150 ->
                    1 to "StressedSpendingDay"
                completionRate >= 0.8f ->
                    2 to "ProductiveDay"
                else ->
                    3 to "MixedDay"
            }

            DayClusterAssignment(
                dateString = day.dateString,
                clusterId = clusterId,
                label = label,
                distanceToCentroid = 0f
            )
        }

        return DayClusterInsight(
            assignments = assignments,
            clusterCount = assignments.map { it.clusterId }.distinct().size,
            strategyName = strategyName,
            strategyVersion = strategyVersion
        )
    }
}

class SpearmanCorrelationAnalyzer : CorrelationAnalyzer {
    override val strategyName: String = "SpearmanRank"
    override val strategyVersion: String = "1.0"

    override fun analyze(
        dailyData: List<DailyData>,
        habitIds: List<Long>
    ): CorrelationInsight {
        if (dailyData.size < 3) {
            return CorrelationInsight(
                pairwiseCorrelations = emptyList(),
                strategyName = strategyName,
                strategyVersion = strategyVersion
            )
        }

        val variableSeries = buildVariableSeries(dailyData, habitIds)
        val pairs = mutableListOf<CorrelationPair>()

        for (i in variableSeries.indices) {
            for (j in i + 1 until variableSeries.size) {
                val seriesA = variableSeries[i]
                val seriesB = variableSeries[j]
                val sharedDates = seriesA.valuesByDate.keys.intersect(seriesB.valuesByDate.keys)
                if (sharedDates.size < 3) continue

                val valuesA = sharedDates.map { date -> seriesA.valuesByDate.getValue(date) }
                val valuesB = sharedDates.map { date -> seriesB.valuesByDate.getValue(date) }
                val coefficient = spearman(valuesA, valuesB)
                pairs += CorrelationPair(
                    variableA = seriesA.name,
                    variableB = seriesB.name,
                    coefficient = coefficient,
                    strengthLabel = strengthLabel(coefficient)
                )
            }
        }

        return CorrelationInsight(
            pairwiseCorrelations = pairs,
            strategyName = strategyName,
            strategyVersion = strategyVersion
        )
    }

    private data class Series(
        val name: String,
        val valuesByDate: Map<String, Float>
    )

    private fun buildVariableSeries(
        dailyData: List<DailyData>,
        habitIds: List<Long>
    ): List<Series> {
        val series = mutableListOf<Series>()
        series += Series(
            name = "mood",
            valuesByDate = dailyData.associate { it.dateString to (it.mood ?: 3).toFloat() }
        )
        series += Series(
            name = "expense_total",
            valuesByDate = dailyData.associate { it.dateString to it.expenseTotal.toFloat() }
        )
        series += Series(
            name = "expense_count",
            valuesByDate = dailyData.associate { it.dateString to it.expenseCount.toFloat() }
        )
        series += Series(
            name = "habit_completion_rate",
            valuesByDate = dailyData.associate { it.dateString to it.activeHabitCompletionRate() }
        )

        habitIds.forEach { habitId ->
            val valuesByDate = dailyData.mapNotNull { day ->
                day.completionFor(habitId)?.let { completed ->
                    day.dateString to if (completed) 1f else 0f
                }
            }.toMap()
            if (valuesByDate.size >= 3) series += Series("habit_$habitId", valuesByDate)
        }
        return series
    }

    private fun spearman(x: List<Float>, y: List<Float>): Float {
        require(x.size == y.size && x.size >= 2)
        val rx = rank(x)
        val ry = rank(y)
        return pearson(rx, ry)
    }

    private fun rank(values: List<Float>): List<Float> {
        val indexed = values.withIndex().sortedBy { it.value }
        val ranks = FloatArray(values.size)
        var i = 0
        while (i < indexed.size) {
            var j = i
            while (j + 1 < indexed.size && indexed[j + 1].value == indexed[i].value) j++
            val avgRank = (i + j + 2) / 2f
            for (k in i..j) ranks[indexed[k].index] = avgRank
            i = j + 1
        }
        return ranks.toList()
    }

    private fun pearson(x: List<Float>, y: List<Float>): Float {
        val n = x.size
        val meanX = x.average().toFloat()
        val meanY = y.average().toFloat()
        var num = 0f
        var denX = 0f
        var denY = 0f
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            num += dx * dy
            denX += dx * dx
            denY += dy * dy
        }
        if (denX == 0f || denY == 0f) return 0f
        return (num / kotlin.math.sqrt(denX * denY)).coerceIn(-1f, 1f)
    }

    private fun strengthLabel(coefficient: Float): String = when {
        abs(coefficient) >= 0.7f -> "Strong"
        abs(coefficient) >= 0.4f -> "Moderate"
        abs(coefficient) >= 0.2f -> "Weak"
        else -> "Negligible"
    }
}
