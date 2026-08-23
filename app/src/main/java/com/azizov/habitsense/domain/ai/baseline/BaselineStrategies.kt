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
        habitOrder: List<Long>
    ): HabitPredictionInsight {
        val trailing = history.takeLast(windowDays)
        val predictions = habitOrder.map { habitId ->
            val rate = if (trailing.isEmpty()) {
                0.5f
            } else {
                val completedCount = trailing.count { day ->
                    day.habitCompletionById[habitId] == true
                }
                completedCount.toFloat() / trailing.size.toFloat()
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
        habitOrder: List<Long>
    ): DayClusterInsight {
        val assignments = dailyData.map { day ->
            val completionRate = day.habitCompletionRate(habitOrder)
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
        habitOrder: List<Long>
    ): CorrelationInsight {
        if (dailyData.size < 3) {
            return CorrelationInsight(
                pairwiseCorrelations = emptyList(),
                strategyName = strategyName,
                strategyVersion = strategyVersion
            )
        }

        val variableSeries = buildVariableSeries(dailyData, habitOrder)
        val variableNames = variableSeries.keys.toList()
        val pairs = mutableListOf<CorrelationPair>()

        for (i in variableNames.indices) {
            for (j in i + 1 until variableNames.size) {
                val nameA = variableNames[i]
                val nameB = variableNames[j]
                val coefficient = spearman(
                    variableSeries.getValue(nameA),
                    variableSeries.getValue(nameB)
                )
                pairs += CorrelationPair(
                    variableA = nameA,
                    variableB = nameB,
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

    private fun buildVariableSeries(
        dailyData: List<DailyData>,
        habitOrder: List<Long>
    ): Map<String, List<Float>> {
        val map = linkedMapOf<String, List<Float>>()
        map["mood"] = dailyData.map { (it.mood ?: 3).toFloat() }
        map["expense_total"] = dailyData.map { it.expenseTotal.toFloat() }
        map["expense_count"] = dailyData.map { it.expenseCount.toFloat() }
        map["habit_completion_rate"] = dailyData.map { it.habitCompletionRate(habitOrder) }

        habitOrder.forEach { habitId ->
            map["habit_$habitId"] = dailyData.map { day ->
                if (day.habitCompletionById[habitId] == true) 1f else 0f
            }
        }
        return map
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
