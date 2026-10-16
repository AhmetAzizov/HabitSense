package com.azizov.habitsense.data.export

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class DownloadsCopyResult(
    val displayName: String,
    val locationLabel: String
)

/**
 * Copies export files into public Downloads so they appear in Samsung My Files / Files.
 */
class DownloadsDatasetExporter(private val context: Context) {
    fun copyToDownloads(sourceFile: File): DownloadsCopyResult {
        require(sourceFile.exists()) { "Source file does not exist: ${sourceFile.absolutePath}" }
        val displayName = sourceFile.name
        val mimeType = mimeTypeFor(displayName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyViaMediaStore(displayName, mimeType, sourceFile)
            return DownloadsCopyResult(
                displayName = displayName,
                locationLabel = "Downloads/$displayName"
            )
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val destination = File(downloadsDir, displayName)
        sourceFile.inputStream().use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return DownloadsCopyResult(
            displayName = displayName,
            locationLabel = destination.absolutePath
        )
    }

    private fun copyViaMediaStore(displayName: String, mimeType: String, sourceFile: File) {
        val resolver = context.contentResolver
        deleteExistingDownload(displayName)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HabitSense")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create file in Downloads")

        resolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not open Downloads output stream")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun deleteExistingDownload(displayName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
        val args = arrayOf(
            displayName,
            "${Environment.DIRECTORY_DOWNLOADS}/HabitSense/"
        )
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val itemUri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                resolver.delete(itemUri, null, null)
            }
        }
    }

    private fun mimeTypeFor(fileName: String): String {
        return when {
            fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
            fileName.endsWith(".jsonl", ignoreCase = true) -> "application/json"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
