package com.azizov.habitsense.domain.ai.model

data class HabitPrediction(
    val habitId: Long,
    val probabilityComplete: Float,
    val predictedComplete: Boolean
)

data class HabitPredictionInsight(
    val targetDateString: String,
    val predictions: List<HabitPrediction>,
    val strategyName: String,
    val strategyVersion: String
)

data class DayClusterAssignment(
    val dateString: String,
    val clusterId: Int,
    val label: String,
    val distanceToCentroid: Float
)

data class DayClusterInsight(
    val assignments: List<DayClusterAssignment>,
    val clusterCount: Int,
    val strategyName: String,
    val strategyVersion: String
)

data class CorrelationPair(
    val variableA: String,
    val variableB: String,
    val coefficient: Float,
    val strengthLabel: String
)

data class AssociationRule(
    val antecedent: List<String>,
    val consequent: List<String>,
    val support: Float,
    val confidence: Float,
    val lift: Float
)

data class CorrelationInsight(
    val pairwiseCorrelations: List<CorrelationPair>,
    val associationRules: List<AssociationRule> = emptyList(),
    val strategyName: String,
    val strategyVersion: String
)

data class PerformanceMetrics(
    val executionTimeMs: Long,
    val memoryDeltaKb: Long
)

data class StrategyExecutionResult<T>(
    val result: T,
    val metrics: PerformanceMetrics
)
