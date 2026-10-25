package com.azizov.habitsense.data.repository

import com.azizov.habitsense.data.export.HabitPredictionDatasetExportResult
import com.azizov.habitsense.data.export.HabitPredictionDatasetFileExporter
import com.azizov.habitsense.data.export.HabitPredictionDatasetFormat
import com.azizov.habitsense.domain.ai.dataset.HabitPredictionDatasetBuilder
import com.azizov.habitsense.domain.ai.dataset.HabitPredictionTrainingExample
import java.io.File

data class HabitPredictionDatasetExportRequest(
    val startDate: String,
    val endDate: String,
    val outputDirectory: File,
    val fileBaseName: String = "habit_prediction_dataset",
    val format: HabitPredictionDatasetFormat = HabitPredictionDatasetFormat.CSV_AND_JSONL
)

class HabitPredictionDatasetRepository(
    private val dailyDataRepository: DailyDataRepository,
    private val datasetBuilder: HabitPredictionDatasetBuilder = HabitPredictionDatasetBuilder(),
    private val fileExporter: HabitPredictionDatasetFileExporter = HabitPredictionDatasetFileExporter()
) {
    suspend fun buildExamples(
        startDate: String,
        endDate: String
    ): List<HabitPredictionTrainingExample> {
        val dailyData = dailyDataRepository.buildDailyDataRange(startDate, endDate)
        val habitIds = dailyDataRepository.getHabitIds()
        return datasetBuilder.build(dailyData, habitIds)
    }

    suspend fun export(request: HabitPredictionDatasetExportRequest): HabitPredictionDatasetExportResult {
        val examples = buildExamples(request.startDate, request.endDate)
        return fileExporter.export(
            examples = examples,
            outputDirectory = request.outputDirectory,
            fileBaseName = request.fileBaseName,
            format = request.format
        )
    }
}
