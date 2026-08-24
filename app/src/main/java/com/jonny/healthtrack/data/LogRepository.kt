package com.jonny.healthtrack.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jonny.healthtrack.ai.AiAnalysisStatus
import com.jonny.healthtrack.ai.AiAnalysisService
import com.jonny.healthtrack.ai.AiAnalysisResult
import com.jonny.healthtrack.ai.AiAnalysisWork
import com.jonny.healthtrack.ai.parseAiAnalysis
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
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// Legacy Data Class for Migration
private data class LegacyLogEntry(
    val id: String,
    val timestamp: Long,
    val imagePath: String,
    val note: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isOriginalImage: Boolean = true,
    val isPrivate: Boolean = false
)

// Export Model
data class ExportLogModel(
    val timestamp: Long,
    val imagePath: String,
    val note: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val analysis: List<AiAnalysisResult>? = null,
    val analysisData: String? = null,
    val analysisModel: String? = null,
    val analysisUpdatedAt: Long? = null,
    val analysisStatus: String? = null,
    val analysisError: String? = null,
    val isOriginalImage: Boolean = true,
    val isPrivate: Boolean = false,
    val quantity: Double = 1.0
)

// Lightweight statistics for sanity-check display
data class DatabaseStats(
    val entryCount: Int,
    val imageCount: Int,
    val earliestTimestamp: Long?,
    val latestTimestamp: Long?,
    val totalImageSizeBytes: Long
)

class LogRepository(private val context: Context, private val logDao: LogDao, private val recipeDao: RecipeDao) {    private val aiService = AiAnalysisService()

    val allLogs: Flow<List<LogEntity>> = logDao.getAllLogs()

    suspend fun addLog(log: LogEntity) {
        logDao.insertLog(log)
    }

    suspend fun updateLog(log: LogEntity) {
        logDao.insertLog(log)
    }

    suspend fun deleteLog(log: LogEntity) {
        logDao.deleteLog(log)
        if (log.imagePath.isNotEmpty()) {
            val otherLogs = logDao.countByImagePath(log.imagePath)
            val recipeRefs = recipeDao.countByImagePath(log.imagePath)
            if (otherLogs == 0 && recipeRefs == 0) {
                val file = File(log.imagePath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    suspend fun clearAllData() {
        logDao.clearAll()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        dir?.listFiles()?.forEach { it.delete() }
    }

    suspend fun analyzeLog(log: LogEntity, force: Boolean = false): Result<LogEntity> = withContext(Dispatchers.IO) {
        val effectiveNote = buildAnalysisNote(log, recipeDao)
        val effectiveImages = buildAnalysisImages(log, recipeDao)
        if (effectiveImages.isEmpty() && effectiveNote.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Nothing to analyze (missing photo and note)"))
        }

        val latest = logDao.getLogById(log.id) ?: log
        if (!force && latest.analysisStatus == AiAnalysisStatus.PENDING) {
            return@withContext Result.success(latest)
        }

        val pendingUpdate = latest.copy(
            analysisStatus = AiAnalysisStatus.PENDING,
            analysisUpdatedAt = System.currentTimeMillis(),
            analysisError = null,
            analysisModel = aiService.getActiveModelName(context)
        )
        logDao.insertLog(pendingUpdate)

        val analysisImages = buildAnalysisImages(pendingUpdate, recipeDao)
        val imageLabels = analysisImages.map { it.label }
        val contextPreamble = buildImageContextPreamble(imageLabels)
        val analysisText = contextPreamble + buildAnalysisNote(pendingUpdate, recipeDao)
        val result = aiService.analyzeLog(context, analysisImages.map { it.file }, analysisText)
        val now = System.currentTimeMillis()

        return@withContext if (result.isSuccess) {
            val refreshed = logDao.getLogById(log.id) ?: pendingUpdate
            val parsed = parseAiAnalysis(result.getOrNull())
            val nextResults = if (parsed != null) {
                refreshed.analysisResults.orEmpty() + parsed
            } else {
                refreshed.analysisResults
            }
            val aiPrivate = parsed?.isPrivate == true
            val updated = refreshed.copy(
                analysisResults = nextResults,
                analysisStatus = AiAnalysisStatus.COMPLETE,
                analysisUpdatedAt = now,
                analysisError = null,
                analysisModel = aiService.getActiveModelName(context),
                isPrivate = if (aiPrivate) true else refreshed.isPrivate
            )
            logDao.insertLog(updated)
            Result.success(updated)
        } else {
            val refreshed = logDao.getLogById(log.id) ?: pendingUpdate
            val updated = refreshed.copy(
                analysisStatus = AiAnalysisStatus.ERROR,
                analysisUpdatedAt = now,
                analysisError = result.exceptionOrNull()?.message
            )
            logDao.insertLog(updated)
            Result.failure(result.exceptionOrNull() ?: IllegalStateException("AI analysis failed"))
        }
    }

    suspend fun queueAnalysis(log: LogEntity, force: Boolean = false) = withContext(Dispatchers.IO) {
        val effectiveNote = buildAnalysisNote(log, recipeDao)
        val effectiveImages = buildAnalysisImages(log, recipeDao)
        if (effectiveImages.isEmpty() && effectiveNote.isBlank()) return@withContext

        val latest = logDao.getLogById(log.id) ?: log
        if (!force && latest.analysisStatus == AiAnalysisStatus.PENDING) return@withContext

        val pendingUpdate = latest.copy(
            analysisStatus = AiAnalysisStatus.PENDING,
            analysisUpdatedAt = System.currentTimeMillis(),
            analysisError = null,
            analysisModel = aiService.getActiveModelName(context)
        )
        logDao.insertLog(pendingUpdate)

        AiAnalysisWork.enqueue(context, log.id, force)
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
                                longitude = it.longitude,
                                isOriginalImage = true, // Default for legacy
                                isPrivate = false
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

    suspend fun getDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
        val entryCount = logDao.getLogCount()
        val imageCount = logDao.getImageCount()
        val earliest = logDao.getEarliestTimestamp()
        val latest = logDao.getLatestTimestamp()
        val imageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageSize = imageDir?.listFiles()?.map { it.length() }?.sum() ?: 0L
        DatabaseStats(entryCount, imageCount, earliest, latest, imageSize)
    }

    // --- Import Logic ---

    suspend fun importImages(uris: List<Uri>, overwrite: Boolean) = withContext(Dispatchers.IO) {
        if (overwrite) clearAllData()

        uris.forEach { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val timeStamp = System.currentTimeMillis()
                val tempFile = createImageFile(context) 
                
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val exif = ExifInterface(tempFile.absolutePath)
                val latLong = exif.latLong
                val dateString = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                val finalTimestamp = if (dateString != null) {
                    try {
                        val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                        sdf.parse(dateString)?.time ?: timeStamp
                    } catch (e: Exception) { timeStamp }
                } else {
                    timeStamp
                }

                val log = LogEntity(
                    timestamp = finalTimestamp,
                    imagePath = tempFile.absolutePath,
                    note = "", 
                    latitude = latLong?.get(0),
                    longitude = latLong?.get(1)
                )
                logDao.insertLog(log)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun importData(uri: Uri, overwrite: Boolean) = withContext(Dispatchers.IO) {
        if (overwrite) clearAllData()

        val mimeType = context.contentResolver.getType(uri)
        val isZip = mimeType?.contains("zip") == true || uri.path?.endsWith(".zip") == true

        if (isZip) {
            importZip(uri)
        } else {
            importJsonl(uri)
        }
    }

    private suspend fun importJsonl(uri: Uri) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().useLines { lines ->
                val gson = Gson()
                lines.forEach { line ->
                    try {
                        val exportModel = gson.fromJson(line, ExportLogModel::class.java)
                        val imagePath = if (exportModel.imagePath.isNotEmpty()) {
                             File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), File(exportModel.imagePath).name).absolutePath
                        } else ""
                        
                        val log = LogEntity(
                            timestamp = exportModel.timestamp,
                            imagePath = imagePath,
                            note = exportModel.note,
                            latitude = exportModel.latitude,
                            longitude = exportModel.longitude,
                            analysisResults = exportModel.analysis ?: parseAiAnalysis(exportModel.analysisData)?.let { listOf(it) },
                            analysisModel = exportModel.analysisModel,
                            analysisUpdatedAt = exportModel.analysisUpdatedAt,
                            analysisStatus = exportModel.analysisStatus,
                            analysisError = exportModel.analysisError,
                            isOriginalImage = exportModel.isOriginalImage,
                            isPrivate = exportModel.isPrivate,
                            quantity = exportModel.quantity
                        )
                        logDao.insertLog(log)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    private suspend fun importZip(uri: Uri) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(BufferedInputStream(stream)).use { zis ->
                var entry: ZipEntry?
                var lineNum = 0
                val gson = Gson()
                while (true) {
                    try {
                        zis.nextEntry.also { entry = it }
                    } catch (e: java.io.IOException) {
                        break
                    }
                    if (entry == null) break
                    val name = entry!!.name
                    if (name == "data.jsonl") {
                        val reader = zis.bufferedReader()
                        var line = reader.readLine()
                        while (line != null) {
                            lineNum++
                            if (line.isNotBlank()) {
                                try {
                                    val exportModel = gson.fromJson(line, ExportLogModel::class.java)
                                    val imagePath = if (exportModel.imagePath.isNotEmpty()) {
                                        File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), File(exportModel.imagePath).name).absolutePath
                                    } else ""

                                    val log = LogEntity(
                                        timestamp = exportModel.timestamp,
                                        imagePath = imagePath,
                                        note = exportModel.note,
                                        latitude = exportModel.latitude,
                                        longitude = exportModel.longitude,
                                        analysisResults = exportModel.analysis ?: parseAiAnalysis(exportModel.analysisData)?.let { listOf(it) },
                                        analysisModel = exportModel.analysisModel,
                                        analysisUpdatedAt = exportModel.analysisUpdatedAt,
                                        analysisStatus = exportModel.analysisStatus,
                                        analysisError = exportModel.analysisError,
                                        isOriginalImage = exportModel.isOriginalImage,
                                        isPrivate = exportModel.isPrivate,
                                        quantity = exportModel.quantity
                                    )
                                    logDao.insertLog(log)
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                            line = reader.readLine()
                        }
                    } else if (name.startsWith("images/") && !entry!!.isDirectory) {
                        val filename = File(name).name
                        val targetFile = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename)

                        FileOutputStream(targetFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (zis.read(buffer).also { len = it } != -1) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Exports ---

    suspend fun exportLite(startTime: Long? = null, endTime: Long? = null, filename: String = "healthtrack_lite"): File = withContext(Dispatchers.IO) {
        val logs = if (startTime != null && endTime != null) {
            logDao.getLogsInRange(startTime, endTime)
        } else {
            logDao.getAllLogsSnapshot()
        }
        val exportFile = File(context.cacheDir, "$filename.jsonl")
        
        exportFile.bufferedWriter().use { writer ->
            val gson = Gson()
            logs.forEach { log ->
                val cleanName = if (log.imagePath.isNotEmpty()) getCleanFilename(log.timestamp) else ""
                
                val exportLog = ExportLogModel(
                    timestamp = log.timestamp,
                    imagePath = cleanName, 
                    note = log.note,
                    latitude = log.latitude,
                    longitude = log.longitude,
                    analysis = log.analysisResults,
                    analysisModel = log.analysisModel,
                    analysisUpdatedAt = log.analysisUpdatedAt,
                    analysisStatus = log.analysisStatus,
                    analysisError = log.analysisError,
                    isOriginalImage = log.isOriginalImage,
                    isPrivate = log.isPrivate,
                    quantity = log.quantity
                )
                writer.write(gson.toJson(exportLog))
                writer.newLine()
            }
        }
        exportFile
    }

    suspend fun exportFull(startTime: Long? = null, endTime: Long? = null, filename: String = "healthtrack_full"): File = withContext(Dispatchers.IO) {
        val logs = if (startTime != null && endTime != null) {
            logDao.getLogsInRange(startTime, endTime)
        } else {
            logDao.getAllLogsSnapshot()
        }
        val zipFile = File(context.cacheDir, "$filename.zip")
        val gson = Gson()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // 1. Create JSONL
            val jsonlBuilder = StringBuilder()
            logs.forEach { log ->
                val relativePath = if (log.imagePath.isNotEmpty()) {
                    val cleanName = getCleanFilename(log.timestamp)
                    "images/$cleanName"
                } else ""
                
                val exportLog = ExportLogModel(
                    timestamp = log.timestamp,
                    imagePath = relativePath,
                    note = log.note,
                    latitude = log.latitude,
                    longitude = log.longitude,
                    analysis = log.analysisResults,
                    analysisModel = log.analysisModel,
                    analysisUpdatedAt = log.analysisUpdatedAt,
                    analysisStatus = log.analysisStatus,
                    analysisError = log.analysisError,
                    isOriginalImage = log.isOriginalImage,
                    isPrivate = log.isPrivate,
                    quantity = log.quantity
                )
                jsonlBuilder.append(gson.toJson(exportLog)).append("\n")
            }

            // Write JSONL
            zos.putNextEntry(ZipEntry("data.jsonl"))
            zos.write(jsonlBuilder.toString().toByteArray())
            zos.closeEntry()

            // 2. Add Images (Unique)
            val addedImages = mutableSetOf<String>()
            
            logs.forEach { log ->
                if (log.imagePath.isNotEmpty()) {
                    val imageFile = File(log.imagePath)
                    if (imageFile.exists() && !addedImages.contains(log.imagePath)) {
                        val cleanName = getCleanFilename(log.timestamp)
                        val zipEntryName = "images/$cleanName"
                        zos.putNextEntry(ZipEntry(zipEntryName))
                        
                        FileInputStream(imageFile).use { fis ->
                            BufferedInputStream(fis).copyTo(zos)
                        }
                        zos.closeEntry()
                        addedImages.add(log.imagePath)
                    }
                }
            }
        }
        zipFile
    }

    private fun getCleanFilename(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "img_" + sdf.format(Date(timestamp)) + ".jpg"
    }

    private fun createImageFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }
}
