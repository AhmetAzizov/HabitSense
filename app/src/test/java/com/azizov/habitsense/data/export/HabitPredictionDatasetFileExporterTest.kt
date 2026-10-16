package com.azizov.habitsense.data.export

import com.azizov.habitsense.domain.ai.dataset.HabitPredictionTrainingExample
import com.azizov.habitsense.domain.model.HabitPredictionFeatureSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HabitPredictionDatasetFileExporterTest {
    @Test
    fun export_writesCsvJsonlAndMetadata() {
        val features = FloatArray(HabitPredictionFeatureSchema.DIMENSION) { index ->
            if (index == 0) 1f else 0f
        }
        val examples = listOf(
            HabitPredictionTrainingExample(
                targetDateString = "2026-07-02",
                habitId = 1L,
                label = true,
                features = features
            )
        )

        val outputDir = File.createTempFile("habit_export", null).apply {
            delete()
            mkdir()
        }
        val result = HabitPredictionDatasetFileExporter().export(
            examples = examples,
            outputDirectory = outputDir,
            fileBaseName = "test_dataset"
        )

        assertEquals(1, result.rowCount)
        assertTrue(File(result.csvPath!!).exists())
        assertTrue(File(result.jsonlPath!!).exists())
        assertTrue(File(result.metadataPath).exists())
        val csv = File(result.csvPath!!).readText()
        assertTrue(csv.startsWith("target_date,habit_id,label,"))
        assertTrue(csv.contains("2026-07-02,1,1,"))
    }
}
