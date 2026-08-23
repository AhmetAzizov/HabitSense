package com.azizov.habitsense.domain.ai

import com.azizov.habitsense.data.local.dao.ExperimentRunDao
import com.azizov.habitsense.data.local.entity.ExperimentRunEntity
import com.azizov.habitsense.domain.ai.model.PerformanceMetrics
import com.azizov.habitsense.domain.ai.model.StrategyExecutionResult
import com.azizov.habitsense.domain.model.FeatureSchema
import org.json.JSONObject

class StrategyExecutor(
    private val experimentRunDao: ExperimentRunDao
) {
    suspend fun <T> executeAndLog(
        strategyName: String,
        strategyVersion: String,
        inputStartDate: String,
        inputEndDate: String,
        parameters: Map<String, Any> = emptyMap(),
        block: () -> T
    ): StrategyExecutionResult<T> {
        val (result, metrics) = PerformanceProbe.measure(block)

        experimentRunDao.insert(
            ExperimentRunEntity(
                strategyName = strategyName,
                strategyVersion = strategyVersion,
                featureSchemaVersion = FeatureSchema.VERSION,
                inputStartDate = inputStartDate,
                inputEndDate = inputEndDate,
                parametersJson = JSONObject(parameters).toString(),
                outputSummaryJson = JSONObject(
                    mapOf(
                        "resultType" to (result?.let { it::class.simpleName } ?: "null")
                    )
                ).toString(),
                executionTimeMs = metrics.executionTimeMs,
                memoryDeltaKb = metrics.memoryDeltaKb
            )
        )

        return StrategyExecutionResult(result = result, metrics = metrics)
    }
}
