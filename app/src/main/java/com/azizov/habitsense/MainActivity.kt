package com.azizov.habitsense

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.azizov.habitsense.data.export.DownloadsDatasetExporter
import com.azizov.habitsense.data.repository.HabitPredictionDatasetExportRequest
import com.azizov.habitsense.data.synthetic.SyntheticDataConfig
import com.azizov.habitsense.data.synthetic.SyntheticDataProfile
import com.azizov.habitsense.ui.theme.HabitSenseTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HabitSenseApplication
        setContent {
            HabitSenseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TemporaryDatasetExportScreen(
                        app = app,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun TemporaryDatasetExportScreen(
    app: HabitSenseApplication,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Tap to seed enriched synthetic data and export CSV/JSONL.") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "HabitSense (dev)",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Exports to app storage, then copies into Downloads/HabitSense (visible in Samsung My Files).",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            enabled = !isRunning,
            onClick = {
                scope.launch {
                    isRunning = true
                    statusMessage = "Running…"
                    try {
                        val message = withContext(Dispatchers.IO) {
                            runDatasetExport(app.applicationContext)
                        }
                        statusMessage = message
                    } catch (error: Exception) {
                        Log.e(TAG, "Dataset export failed", error)
                        statusMessage = "Failed: ${error.message}"
                    } finally {
                        isRunning = false
                    }
                }
            }
        ) {
            Text("Seed DB & export dataset")
        }
        if (isRunning) {
            CircularProgressIndicator()
        }
        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private suspend fun runDatasetExport(context: Context): String {
    val app = context.applicationContext as HabitSenseApplication
    val startDate = "2025-01-01"
    val endDate = "2025-12-31"
    val generation = app.syntheticDataGenerator.generate(
        SyntheticDataConfig(
            startDate = startDate,
            endDate = endDate,
            profile = SyntheticDataProfile.ENRICHED,
            clearExistingData = true
        )
    )
    val outputDirectory = File(app.filesDir, "ml_exports")
    val export = app.habitPredictionDatasetRepository.export(
        HabitPredictionDatasetExportRequest(
            startDate = startDate,
            endDate = endDate,
            outputDirectory = outputDirectory,
            fileBaseName = "habit_prediction_train"
        )
    )
    val downloadsExporter = DownloadsDatasetExporter(context)
    val publicCopies = listOfNotNull(
        export.csvPath?.let { File(it) },
        export.jsonlPath?.let { File(it) },
        File(export.metadataPath)
    ).map { file -> downloadsExporter.copyToDownloads(file) }

    Log.i(
        TAG,
        "Synthetic seed=${generation.seed} rows=${export.rowCount} csv=${export.csvPath}"
    )
    return buildString {
        appendLine("Done.")
        appendLine("Synthetic: ${generation.dayCount} days, ${generation.habitLogCount} habit logs.")
        appendLine("Export rows: ${export.rowCount}")
        appendLine()
        appendLine("Open Samsung My Files → Downloads → HabitSense:")
        publicCopies.forEach { copy ->
            appendLine("• ${copy.locationLabel}")
        }
        appendLine()
        appendLine("Private copy (adb only): ${export.csvPath}")
    }
}

private const val TAG = "HabitSenseExport"

@Preview(showBackground = true)
@Composable
fun TemporaryDatasetExportPreview() {
    HabitSenseTheme {
        Text("Preview requires HabitSenseApplication")
    }
}
