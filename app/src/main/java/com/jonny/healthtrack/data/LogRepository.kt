package com.jonny.healthtrack.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Legacy Data Class for Migration
private data class LegacyLogEntry(
    val id: String,
    val timestamp: Long,
    val imagePath: String,
    val note: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

// Export Model (No ID, clean filename)
data class ExportLogModel(
    val timestamp: Long,
    val imagePath: String,
    val note: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class LogRepository(private val context: Context, private val logDao: LogDao) {

    val allLogs: Flow<List<LogEntity>> = logDao.getAllLogs()

    suspend fun addLog(log: LogEntity) {
        logDao.insertLog(log)
    }

    suspend fun deleteLog(log: LogEntity) {
        logDao.deleteLog(log)
        val file = File(log.imagePath)
        if (file.exists()) {
            file.delete()
        }
    }

    fun checkAndMigrateLegacyData() {
        CoroutineScope(Dispatchers.IO).launch {
            val legacyFile = File(context.filesDir, "logs.json")
            if (legacyFile.exists()) {
                try {
                    val json = legacyFile.readText()
                    val type = object : TypeToken<List<LegacyLogEntry>>() {}.type
                    val oldLogs: List<LegacyLogEntry>? = Gson().fromJson(json, type)

                    if (!oldLogs.isNullOrEmpty()) {
                        val newLogs = oldLogs.map {
                            LogEntity(
                                id = it.id,
                                timestamp = it.timestamp,
                                imagePath = it.imagePath,
                                note = it.note,
                                latitude = it.latitude,
                                longitude = it.longitude
                            )
                        }
                        logDao.insertAll(newLogs)
                    }
                    legacyFile.renameTo(File(context.filesDir, "logs.json.migrated"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun exportLite(startTime: Long? = null, endTime: Long? = null): File = withContext(Dispatchers.IO) {
        val logs = if (startTime != null && endTime != null) {
            logDao.getLogsInRange(startTime, endTime)
        } else {
            logDao.getAllLogsSnapshot()
        }
        val exportFile = File(context.cacheDir, "healthtrack_lite.jsonl")
        
        exportFile.bufferedWriter().use { writer ->
            val gson = Gson()
            logs.forEach { log ->
                val cleanName = getCleanFilename(log.timestamp)
                
                val exportLog = ExportLogModel(
                    timestamp = log.timestamp,
                    imagePath = cleanName, 
                    note = log.note,
                    latitude = log.latitude,
                    longitude = log.longitude
                )
                writer.write(gson.toJson(exportLog))
                writer.newLine()
            }
        }
        exportFile
    }

    suspend fun exportFull(startTime: Long? = null, endTime: Long? = null): File = withContext(Dispatchers.IO) {
        val logs = if (startTime != null && endTime != null) {
            logDao.getLogsInRange(startTime, endTime)
        } else {
            logDao.getAllLogsSnapshot()
        }
        val zipFile = File(context.cacheDir, "healthtrack_full.zip")
        val gson = Gson()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // 1. Create JSONL
            val jsonlBuilder = StringBuilder()
            logs.forEach { log ->
                val cleanName = getCleanFilename(log.timestamp)
                val relativePath = "images/$cleanName"
                
                val exportLog = ExportLogModel(
                    timestamp = log.timestamp,
                    imagePath = relativePath,
                    note = log.note,
                    latitude = log.latitude,
                    longitude = log.longitude
                )
                jsonlBuilder.append(gson.toJson(exportLog)).append("\n")
            }

            // Write JSONL to ZIP
            zos.putNextEntry(ZipEntry("data.jsonl"))
            zos.write(jsonlBuilder.toString().toByteArray())
            zos.closeEntry()

            // 2. Add Images
            logs.forEach { log ->
                val imageFile = File(log.imagePath)
                if (imageFile.exists()) {
                    val cleanName = getCleanFilename(log.timestamp)
                    val zipEntryName = "images/$cleanName"
                    zos.putNextEntry(ZipEntry(zipEntryName))
                    
                    FileInputStream(imageFile).use { fis ->
                        BufferedInputStream(fis).copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
        zipFile
    }

    private fun getCleanFilename(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "img_" + sdf.format(Date(timestamp)) + ".jpg"
    }
}
