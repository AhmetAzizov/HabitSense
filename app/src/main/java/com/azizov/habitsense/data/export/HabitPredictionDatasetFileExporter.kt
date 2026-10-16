package com.azizov.habitsense.data.export

import com.azizov.habitsense.domain.ai.dataset.HabitPredictionTrainingExample
import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema
import java.io.File
import java.util.Locale

enum class HabitPredictionDatasetFormat {
    CSV,
    JSONL,
    CSV_AND_JSONL
}

data class HabitPredictionDatasetExportResult(
    val rowCount: Int,
    val csvPath: String? = null,
    val jsonlPath: String? = null,
    val metadataPath: String
)

class HabitPredictionDatasetFileExporter {
    fun export(
        examples: List<HabitPredictionTrainingExample>,
        outputDirectory: File,
        fileBaseName: String = "habit_prediction_dataset",
        format: HabitPredictionDatasetFormat = HabitPredictionDatasetFormat.CSV_AND_JSONL
    ): HabitPredictionDatasetExportResult {
        require(fileBaseName.isNotBlank()) { "fileBaseName must not be blank" }
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }
        require(outputDirectory.isDirectory) { "outputDirectory must be a directory" }

        val metadataFile = File(outputDirectory, "${fileBaseName}_metadata.json")
        metadataFile.writeText(buildMetadataJson())

        var csvPath: String? = null
        var jsonlPath: String? = null

        if (format == HabitPredictionDatasetFormat.CSV || format == HabitPredictionDatasetFormat.CSV_AND_JSONL) {
            val csvFile = File(outputDirectory, "$fileBaseName.csv")
            csvFile.writeText(buildCsv(examples))
            csvPath = csvFile.absolutePath
        }
        if (format == HabitPredictionDatasetFormat.JSONL || format == HabitPredictionDatasetFormat.CSV_AND_JSONL) {
            val jsonlFile = File(outputDirectory, "$fileBaseName.jsonl")
            jsonlFile.writeText(buildJsonl(examples))
            jsonlPath = jsonlFile.absolutePath
        }

        return HabitPredictionDatasetExportResult(
            rowCount = examples.size,
            csvPath = csvPath,
            jsonlPath = jsonlPath,
            metadataPath = metadataFile.absolutePath
        )
    }

    private fun buildMetadataJson(): String {
        val featureNames = HabitPredictionFeatureSchema.featureNames()
            .joinToString(",\n    ") { name -> jsonString(name) }
        return """
            {
              "featureSchemaVersion": ${HabitPredictionFeatureSchema.VERSION},
              "featureDimension": ${HabitPredictionFeatureSchema.DIMENSION},
              "windowDays": ${HabitPredictionFeatureSchema.WINDOW_DAYS},
              "featuresPerDay": ${HabitPredictionFeatureSchema.FEATURES_PER_DAY},
              "featureNames": [
                $featureNames
              ],
              "labelField": "label",
              "labelSemantics": "1 = habit completed on targetDateString, 0 = not completed (habit active that day)",
              "historySemantics": "Features use calendar days strictly before targetDateString (up to 7, left-padded)"
            }
            """.trimIndent()
    }

    private fun buildCsv(examples: List<HabitPredictionTrainingExample>): String {
        val featureHeaders = HabitPredictionFeatureSchema.featureNames()
        val header = buildList {
            add("target_date")
            add("habit_id")
            add("label")
            addAll(featureHeaders)
        }.joinToString(",")

        val rows = examples.map { example ->
            val featureValues = example.features.joinToString(",") { value ->
                String.format(Locale.US, "%.6f", value)
            }
            listOf(
                example.targetDateString,
                example.habitId.toString(),
                if (example.label) "1" else "0",
                featureValues
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun buildJsonl(examples: List<HabitPredictionTrainingExample>): String {
        return examples.joinToString("\n") { example ->
            val features = example.features.joinToString(", ") { value ->
                String.format(Locale.US, "%.6f", value)
            }
            """
                {
                  "targetDateString": ${jsonString(example.targetDateString)},
                  "habitId": ${example.habitId},
                  "label": ${if (example.label) 1 else 0},
                  "featureSchemaVersion": ${example.featureSchemaVersion},
                  "features": [$features]
                }
                """.trimIndent().replace("\n", "")
        }
    }

    private fun jsonString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
