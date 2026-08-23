package com.azizov.habitsense.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "experiment_runs")
data class ExperimentRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val strategyName: String,
    val strategyVersion: String,
    val featureSchemaVersion: Int,
    val inputStartDate: String,
    val inputEndDate: String,
    val parametersJson: String,
    val outputSummaryJson: String,
    val executionTimeMs: Long,
    val memoryDeltaKb: Long,
    val timestamp: Long = System.currentTimeMillis()
)
