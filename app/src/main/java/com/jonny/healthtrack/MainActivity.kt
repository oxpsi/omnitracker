package com.jonny.healthtrack

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.jonny.healthtrack.ai.AiAnalysisStatus
import com.jonny.healthtrack.ai.AiPreferences
import com.jonny.healthtrack.ai.AiModelPreset
import com.jonny.healthtrack.ai.OpenAiApiType
import com.jonny.healthtrack.ai.latestAiAnalysis
import com.jonny.healthtrack.data.AppDatabase
import com.jonny.healthtrack.data.LogEntity
import com.jonny.healthtrack.data.LogRepository
import com.jonny.healthtrack.data.RecipeRepository
import com.jonny.healthtrack.data.DatabaseStats
import com.jonny.healthtrack.util.aggregateFoodComponents
import com.jonny.healthtrack.util.normalizeCapturedJpegInPlace
import com.jonny.healthtrack.util.AppThemeColor
import com.jonny.healthtrack.util.ThemePreferences
import com.jonny.healthtrack.util.ShareUtils
import com.jonny.healthtrack.util.createThemedRecipeThumbnail
import com.jonny.healthtrack.util.primaryColorArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// --- Navigation State ---
sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    object DaySummary : Screen()
    data class Recipes(val openRecipeId: String? = null) : Screen()
    data class Detail(val logId: String) : Screen()
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: LogRepository
    private lateinit var recipeRepository: RecipeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Init Data Layer
        val db = AppDatabase.getDatabase(this)
        repository = LogRepository(this, db.logDao(), db.recipeDao())
        recipeRepository = RecipeRepository(this, db.recipeDao(), db.logDao())
        
        // Trigger Migration (Background)
        repository.checkAndMigrateLegacyData()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            // Theme Color State
            val context = LocalContext.current
            var themeColor by remember { mutableStateOf(ThemePreferences.getThemeColor(context)) }

            HealthTrackTheme(darkTheme = isDarkTheme, themeColor = themeColor) {
                AppContent(
                    repository = repository,
                    recipeRepository = recipeRepository,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    themeColor = themeColor,
                    onThemeColorChange = { newColor ->
                        themeColor = newColor
                        ThemePreferences.setThemeColor(context, newColor)
                    }
                )
            }
        }
    }
}

@Composable
fun HealthTrackTheme(
    darkTheme: Boolean,
    themeColor: AppThemeColor,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeColor) {
        AppThemeColor.Green -> if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFF8BC34A),
                secondary = Color(0xFFAED581),
                tertiary = Color(0xFFDCEDC8),
                primaryContainer = Color(0xFF4A6B2E),
                secondaryContainer = Color(0xFF5A7D33),
                tertiaryContainer = Color(0xFF6B8E3D)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF4CAF50),
                secondary = Color(0xFF8BC34A),
                tertiary = Color(0xFFCDDC39),
                primaryContainer = Color(0xFFD7EBC4),
                secondaryContainer = Color(0xFFE8F5DC),
                tertiaryContainer = Color(0xFFEFF7E5)
            )
        }
        AppThemeColor.Blue -> if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFF64B5F6),
                secondary = Color(0xFF4FC3F7),
                tertiary = Color(0xFFB3E5FC),
                primaryContainer = Color(0xFF1B5E8A),
                secondaryContainer = Color(0xFF2273A0),
                tertiaryContainer = Color(0xFF2D87B5)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF2196F3),
                secondary = Color(0xFF03A9F4),
                tertiary = Color(0xFFB3E5FC),
                primaryContainer = Color(0xFFC6DEF5),
                secondaryContainer = Color(0xFFD4ECFB),
                tertiaryContainer = Color(0xFFE3F3FC)
            )
        }
        AppThemeColor.Red -> if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFE57373),
                secondary = Color(0xFFFF8A80),
                tertiary = Color(0xFFFFCDD2),
                primaryContainer = Color(0xFF8A2A2A),
                secondaryContainer = Color(0xFFA03030),
                tertiaryContainer = Color(0xFFB53838)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFF44336),
                secondary = Color(0xFFE57373),
                tertiary = Color(0xFFFFCDD2),
                primaryContainer = Color(0xFFF5D0CC),
                secondaryContainer = Color(0xFFFCDAD7),
                tertiaryContainer = Color(0xFFFEE8E6)
            )
        }
        AppThemeColor.Purple -> if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFBA68C8),
                secondary = Color(0xFFCE93D8),
                tertiary = Color(0xFFE1BEE7),
                primaryContainer = Color(0xFF6A3A72),
                secondaryContainer = Color(0xFF7A4482),
                tertiaryContainer = Color(0xFF8A5092)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF9C27B0),
                secondary = Color(0xFFBA68C8),
                tertiary = Color(0xFFE1BEE7),
                primaryContainer = Color(0xFFEAC9EF),
                secondaryContainer = Color(0xFFF0D9F4),
                tertiaryContainer = Color(0xFFF5E4F8)
            )
        }
        AppThemeColor.Orange -> if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFFFFB74D),
                secondary = Color(0xFFFFCC80),
                tertiary = Color(0xFFFFE0B2),
                primaryContainer = Color(0xFF8A5A1E),
                secondaryContainer = Color(0xFF9D6925),
                tertiaryContainer = Color(0xFFB0782C)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFFF9800),
                secondary = Color(0xFFFFB74D),
                tertiary = Color(0xFFFFE0B2),
                primaryContainer = Color(0xFFFCDDB8),
                secondaryContainer = Color(0xFFFDE8CF),
                tertiaryContainer = Color(0xFFFEEFE0)
            )
        }
        AppThemeColor.Teal -> if (darkTheme) {
            darkColorScheme(
                primary = Color(0xFF4DB6AC),
                secondary = Color(0xFF80CBC4),
                tertiary = Color(0xFFB2DFDB),
                primaryContainer = Color(0xFF1A5E58),
                secondaryContainer = Color(0xFF227068),
                tertiaryContainer = Color(0xFF2D827A)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF009688),
                secondary = Color(0xFF26A69A),
                tertiary = Color(0xFFB2DFDB),
                primaryContainer = Color(0xFFC0E8E4),
                secondaryContainer = Color(0xFFD0EFEB),
                tertiaryContainer = Color(0xFFE0F5F3)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun AppContent(
    repository: LogRepository,
    recipeRepository: RecipeRepository,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    themeColor: AppThemeColor,
    onThemeColorChange: (AppThemeColor) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var aiEnabled by remember { mutableStateOf(AiPreferences.isEnabled(context)) }
    
    val logs by repository.allLogs.collectAsState(initial = emptyList())
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    fun toggleSettingsPane() {
        if (currentScreen is Screen.Settings) {
            currentScreen = Screen.Home
        } else {
            currentScreen = Screen.Settings
        }
    }

	    fun createLog(file: File?, note: String, lat: Double?, long: Double?, isOriginal: Boolean, analysisSource: LogEntity? = null, recipeId: String? = null) {
	        scope.launch {
	            val newLog = LogEntity(
	                timestamp = System.currentTimeMillis(),
	                imagePath = file?.absolutePath ?: "",
	                note = note,
	                latitude = lat,
	                longitude = long,
	                isOriginalImage = isOriginal,
	                analysisResults = analysisSource?.analysisResults,
	                analysisStatus = analysisSource?.analysisStatus,
	                analysisModel = analysisSource?.analysisModel,
	                analysisUpdatedAt = if (analysisSource != null) System.currentTimeMillis() else null,
	                analysisError = analysisSource?.analysisError,
	                recipeId = recipeId
	            )
	            repository.addLog(newLog)
	            selectedDate = Instant.ofEpochMilli(newLog.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

	            // Navigate immediately after creation. (AI analysis completion should never trigger navigation.)
	            currentScreen = Screen.Detail(newLog.id)

	            // Kick off analysis in the background so the UI doesn't "jump" later when analysis finishes.
	            if (analysisSource == null && aiEnabled && (newLog.imagePath.isNotEmpty() || newLog.note.isNotBlank() || newLog.recipeId != null)) {
	                launch { repository.queueAnalysis(newLog, force = false) }
	            }
	        }
	    }

    /**
     * Creates a log entry from a recipe. When [imagePath] is empty (the user
     * chose "note only"), a themed thumbnail is derived from the recipe's batch
     * image (scaled down and framed with the active theme color) so the log
     * entry still has a recognizable image.
     */
    fun createLogFromRecipe(recipeId: String, imagePath: String, note: String) {
        scope.launch {
            val imageFile = if (imagePath.isNotEmpty()) {
                File(imagePath)
            } else {
                withContext(Dispatchers.IO) {
                    val recipe = recipeRepository.getRecipeById(recipeId)
                    val recipeImage = recipe?.imagePath?.takeIf { it.isNotEmpty() }?.let { File(it) }
                    if (recipeImage != null && recipeImage.exists()) {
                        val outline = primaryColorArgb(themeColor, isDarkTheme)
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        val thumbFile = File(
                            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "pictures"),
                            "JPEG_${stamp}_thumb.jpg"
                        )
                        createThemedRecipeThumbnail(recipeImage, thumbFile, outline)
                    } else null
                }
            }
            getLastLocation(context) { lat, long ->
                createLog(imageFile, note, lat, long, false, null, recipeId)
            }
        }
    }

    fun updateAiEnabled(enabled: Boolean) {
        aiEnabled = enabled
        AiPreferences.setEnabled(context, enabled)
    }

	    fun requestAnalysis(log: LogEntity, force: Boolean, showErrors: Boolean) {
	        scope.launch {
	            repository.queueAnalysis(log, force)
	            if (showErrors) {
	                Toast.makeText(context, "Analysis queued (runs in background)", Toast.LENGTH_SHORT).show()
	            }
	        }
	    }

    // Adaptive Layout Logic
    BoxWithConstraints {
        val isWideScreen = maxWidth > 600.dp

        if (isWideScreen) {
            Row(Modifier.fillMaxSize()) {
                val listWeight = if (currentScreen is Screen.Home) 1f else 0.5f
                
                Box(Modifier.weight(listWeight).fillMaxHeight()) {
                    HomeScreen(
                        logs = logs,
                        selectedDate = selectedDate,
                        onSelectedDateChange = { selectedDate = it },
                        onAddLog = { file, note, lat, long, isOriginal, template, recipeId ->
                            createLog(file, note, lat, long, isOriginal, template, recipeId)
                        },
                        onNavigateToSettings = { toggleSettingsPane() },
                        onNavigateToRecipes = { currentScreen = Screen.Recipes() },
                        onNavigateToDetail = { logId -> currentScreen = Screen.Detail(logId) },
                        onNavigateToDaySummary = { currentScreen = Screen.DaySummary },
                        isWideScreen = true
                    )
                }
                
                if (currentScreen !is Screen.Home) {
                    Box(Modifier.weight(0.5f).fillMaxHeight()) {
                        when (val screen = currentScreen) {
                            is Screen.Detail -> {
                                val log = logs.find { it.id == screen.logId }
                                if (log != null) {
                                    DetailScreen(
                                        log = log,
                                        onBack = { currentScreen = Screen.Home },
                                        onDelete = {
                                            scope.launch {
                                                repository.deleteLog(log)
                                                currentScreen = Screen.Home
                                            }
                                        },
                                        onUpdate = { updatedLog ->
                                            scope.launch { repository.updateLog(updatedLog) }
                                        },
                                        onAddLog = { file, note, lat, long, isOriginal, template, recipeId ->
                                            createLog(file, note, lat, long, isOriginal, template, recipeId)
                                        },
                                        onAnalyze = { logToAnalyze ->
                                            requestAnalysis(logToAnalyze, force = true, showErrors = true)
                                        },
                                        onViewRecipe = { recipeId ->
                                            currentScreen = Screen.Recipes(openRecipeId = recipeId)
                                        },
                                        aiEnabled = aiEnabled,
                                        showBackButton = false,
                                        showCloseButton = true
                                    )
                                }
                            }
                            Screen.DaySummary -> {
                                val dayLogs = logs.filter {
                                    val logDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                                    logDate == selectedDate
                                }
                                DaySummaryScreen(
                                    selectedDate = selectedDate,
                                    dayLogs = dayLogs,
                                    onBack = { currentScreen = Screen.Home },
                                    onNavigateToDetail = { logId -> currentScreen = Screen.Detail(logId) },
                                    showBackButton = false,
                                    showCloseButton = true
                                )
                            }
                            Screen.Settings -> SettingsScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                themeColor = themeColor,
                                onThemeColorChange = onThemeColorChange,
                                aiEnabled = aiEnabled,
                                onAiEnabledChange = { updateAiEnabled(it) },
                                onBack = { currentScreen = Screen.Home },
                                repository = repository,
                                showBackButton = false,
                                showCloseButton = true
                            )
                            is Screen.Recipes -> RecipesScreen(
                                repository = recipeRepository,
                                openRecipeId = screen.openRecipeId,
                                onBack = { currentScreen = Screen.Home },
                                onCreateLogFromRecipe = { recipeId, imagePath, note ->
                                    createLogFromRecipe(recipeId, imagePath, note)
                                },
                                showBackButton = false,
                                showCloseButton = true
                            )
                            else -> {}
                        }
                    }
                }
            }
        } else {
            BackHandler(enabled = currentScreen !is Screen.Home) {
                currentScreen = Screen.Home
            }

            when (val screen = currentScreen) {
                Screen.Home -> HomeScreen(
                    logs = logs,
                    selectedDate = selectedDate,
                    onSelectedDateChange = { selectedDate = it },
                    onAddLog = { file, note, lat, long, isOriginal, template, recipeId ->
                        createLog(file, note, lat, long, isOriginal, template, recipeId)
                    },
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    onNavigateToRecipes = { currentScreen = Screen.Recipes() },
                    onNavigateToDetail = { logId -> currentScreen = Screen.Detail(logId) },
                    onNavigateToDaySummary = { currentScreen = Screen.DaySummary },
                    isWideScreen = false
                )
                Screen.Settings -> SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    themeColor = themeColor,
                    onThemeColorChange = onThemeColorChange,
                    aiEnabled = aiEnabled,
                    onAiEnabledChange = { updateAiEnabled(it) },
                    onBack = { currentScreen = Screen.Home },
                    repository = repository,
                    showBackButton = true,
                    showCloseButton = false
                )
                is Screen.Recipes -> RecipesScreen(
                    repository = recipeRepository,
                    openRecipeId = screen.openRecipeId,
                    onBack = { currentScreen = Screen.Home },
                    onCreateLogFromRecipe = { recipeId, imagePath, note ->
                        createLogFromRecipe(recipeId, imagePath, note)
                    },
                    showBackButton = true,
                    showCloseButton = false
                )
                Screen.DaySummary -> {
                    val dayLogs = logs.filter {
                        val logDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                        logDate == selectedDate
                    }
                    DaySummaryScreen(
                        selectedDate = selectedDate,
                        dayLogs = dayLogs,
                        onBack = { currentScreen = Screen.Home },
                        onNavigateToDetail = { logId -> currentScreen = Screen.Detail(logId) },
                        showBackButton = true,
                        showCloseButton = false
                    )
                }
                is Screen.Detail -> {
                    val log = logs.find { it.id == screen.logId }
                    if (log != null) {
                        DetailScreen(
                            log = log,
                            onBack = { currentScreen = Screen.Home },
                            onDelete = {
                                scope.launch {
                                    repository.deleteLog(log)
                                    currentScreen = Screen.Home
                                }
                            },
                            onUpdate = { updatedLog ->
                                scope.launch { repository.updateLog(updatedLog) }
                            },
                            onAddLog = { file, note, lat, long, isOriginal, template, recipeId ->
                                createLog(file, note, lat, long, isOriginal, template, recipeId)
                            },
                            onAnalyze = { logToAnalyze ->
                                requestAnalysis(logToAnalyze, force = true, showErrors = true)
                            },
                            onViewRecipe = { recipeId ->
                                currentScreen = Screen.Recipes(openRecipeId = recipeId)
                            },
                            aiEnabled = aiEnabled,
                            showBackButton = true,
                            showCloseButton = false
                        )
                    } else {
                        LaunchedEffect(Unit) { currentScreen = Screen.Home }
                    }
                }
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    logs: List<LogEntity>,
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    onAddLog: (File?, String, Double?, Double?, Boolean, LogEntity?, String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecipes: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToDaySummary: () -> Unit,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReuseDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) } // For pure note entry
    var showReuseNoteDialog by remember { mutableStateOf(false) }
    var reuseTemplateLog by remember { mutableStateOf<LogEntity?>(null) }
    var showCameraNoteDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pendingCameraLong by rememberSaveable { mutableStateOf<Double?>(null) }
    var tempPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showUploadNoteDialog by rememberSaveable { mutableStateOf(false) }
    var pendingUploadPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUploadLat by rememberSaveable { mutableStateOf<Double?>(null) }
    var pendingUploadLong by rememberSaveable { mutableStateOf<Double?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }
    var activePreset by remember { mutableStateOf(AiPreferences.getActivePreset(context)) }

    val filteredLogs = logs.filter {
        val logDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        logDate == selectedDate
    }

    // 1. Camera: Immediately creates log on success
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val tempPath = tempPhotoPath
        if (success && !tempPath.isNullOrBlank()) {
            val capturedFile = File(tempPath!!)
            getLastLocation(context) { lat, long ->
                scope.launch(Dispatchers.IO) {
                    val normalized = normalizeCapturedJpegInPlace(capturedFile)
                    withContext(Dispatchers.Main) {
                        pendingCameraPath = normalized.absolutePath
                        pendingCameraLat = lat
                        pendingCameraLong = long
                        showCameraNoteDialog = true
                    }
                }
            }
        }
        tempPhotoPath = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            val photoFile = createImageFile(context)
            tempPhotoPath = photoFile.absolutePath
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(uri)
        }
    }

    val uploadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            getLastLocation(context) { lat, long ->
                scope.launch(Dispatchers.IO) {
                    val destFile = createImageFile(context)
                    var copied = false
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(destFile).use { output -> input.copyTo(output) }
                        }
                        copied = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (copied && destFile.exists() && destFile.length() > 0) {
                        try { normalizeCapturedJpegInPlace(destFile) } catch (_: Exception) {}
                    }
                    withContext(Dispatchers.Main) {
                        if (copied) {
                            pendingUploadPath = destFile.absolutePath
                            pendingUploadLat = lat
                            pendingUploadLong = long
                            showUploadNoteDialog = true
                        } else {
                            try { if (destFile.exists()) destFile.delete() } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    if (showReuseDialog) {
        val recentLogs = logs.filter { !it.isPrivate }.take(20)
        ModalBottomSheet(onDismissRequest = { showReuseDialog = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Reuse Recent Entry", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentLogs) { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showReuseDialog = false
                                    reuseTemplateLog = log
                                    showReuseNoteDialog = true
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (log.isPrivate || log.imagePath.isEmpty()) {
                                Box(
                                    modifier = Modifier.size(40.dp).background(Color.Gray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if(log.imagePath.isEmpty()) Text("T", color = Color.White) else Icon(Icons.Default.Lock, null, tint = Color.White)
                                }
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(log.imagePath))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(if(log.note.isNotEmpty()) log.note else "No Note", fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Divider()
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showReuseNoteDialog && reuseTemplateLog != null) {
        val template = reuseTemplateLog!!
        NoteDialog(
            initialNote = template.note,
            onDismiss = {
                showReuseNoteDialog = false
                reuseTemplateLog = null
            },
            onConfirm = { note ->
                getLastLocation(context) { lat, long ->
                    onAddLog(
                        if (template.imagePath.isNotEmpty()) File(template.imagePath) else null,
                        note,
                        lat,
                        long,
                        false, // Original = false (Reuse)
                        if (note == template.note) template else null, // Reuse analysis if note unchanged
                        null
                    )
                    showReuseNoteDialog = false
                    reuseTemplateLog = null
                }
            }
        )
    }

    if (showCameraNoteDialog && pendingCameraPath != null) {
        val pendingFile = File(pendingCameraPath!!)
        NoteDialog(
            initialNote = "",
            onDismiss = {
                pendingFile.let { file ->
                    try {
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {
                    }
                }
                pendingCameraPath = null
                pendingCameraLat = null
                pendingCameraLong = null
                showCameraNoteDialog = false
            },
            onConfirm = { note ->
                onAddLog(pendingFile, note, pendingCameraLat, pendingCameraLong, true, null, null)
                pendingCameraPath = null
                pendingCameraLat = null
                pendingCameraLong = null
                showCameraNoteDialog = false
            }
        )
    }

    if (showUploadNoteDialog && pendingUploadPath != null) {
        val pendingFile = File(pendingUploadPath!!)
        NoteDialog(
            initialNote = "",
            onDismiss = {
                try { if (pendingFile.exists()) pendingFile.delete() } catch (_: Exception) {}
                pendingUploadPath = null
                pendingUploadLat = null
                pendingUploadLong = null
                showUploadNoteDialog = false
            },
            onConfirm = { note ->
                onAddLog(pendingFile, note, pendingUploadLat, pendingUploadLong, true, null, null)
                pendingUploadPath = null
                pendingUploadLat = null
                pendingUploadLong = null
                showUploadNoteDialog = false
            }
        )
    }

    // Pure Note Dialog
    if (showNoteDialog) {
        NoteDialog(
            initialNote = "",
            onDismiss = { showNoteDialog = false },
            onConfirm = { note ->
                getLastLocation(context) { lat, long ->
                    onAddLog(null, note, lat, long, true, null, null) // No image
                    showNoteDialog = false
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Spacer(Modifier.weight(1f))

                FloatingActionButton(
                    onClick = {
                        activePreset = if (activePreset == AiModelPreset.LOW) AiModelPreset.HIGH else AiModelPreset.LOW
                        AiPreferences.setActivePreset(context, activePreset)
                    },
                    containerColor = if (activePreset == AiModelPreset.HIGH) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    if (activePreset == AiModelPreset.HIGH) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = "High model preset",
                                modifier = Modifier.size(18.dp)
                            )
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = "Low model preset"
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (showFabMenu) {
                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                showReuseDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.Refresh, "Reuse History")
                        }
                        
                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                uploadPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.Upload, "Log from Gallery")
                        }

                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                showNoteDialog = true
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.Create, "Text Only")
                        }

                        FloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(Icons.Default.Add, "New Entry")
                        }
                    }
                    
                    FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                        if (showFabMenu) {
                            Icon(Icons.Default.Close, "Close Menu")
                        } else {
                            Icon(Icons.Default.Add, "Menu")
                        }
                    }
                }
            }
        },
        topBar = {
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "OmniTracker",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateToRecipes) {
                            Icon(Icons.Default.Restaurant, contentDescription = "Recipes")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
                
                DateSelector(selectedDate) { newDate ->
                    onSelectedDateChange(newDate)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            val componentSummary = remember(filteredLogs) { aggregateFoodComponents(filteredLogs) }
            LogList(
                logs = filteredLogs,
                onLogClick = { onNavigateToDetail(it.id) },
                onDaySummaryClick = onNavigateToDaySummary,
                daySummaryCount = componentSummary.size
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySummaryScreen(
    selectedDate: LocalDate,
    dayLogs: List<LogEntity>,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    fun titleCaseType(value: String): String {
        return value.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.split("-")
                    .filter { it.isNotBlank() }
                    .joinToString("-") { part ->
                        if (part.length == 1) part.uppercase(Locale.US)
                        else part.take(1).uppercase(Locale.US) + part.drop(1).lowercase(Locale.US)
                    }
            }
    }

    val typeOptions = remember(dayLogs) {
        dayLogs.mapNotNull { log ->
            latestAiAnalysis(log.analysisResults)?.type?.trim()?.lowercase(Locale.US)
        }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var expandedComponentKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(typeOptions) {
        if (typeOptions.isNotEmpty()) {
            selectedType = when {
                selectedType in typeOptions -> selectedType
                "food" in typeOptions -> "food"
                else -> typeOptions.first()
            }
        } else {
            selectedType = null
        }
    }

    val components = remember(dayLogs, selectedType) {
        if (selectedType == "food") aggregateFoodComponents(dayLogs) else emptyList()
    }

    fun formatQuantity(value: Double): String {
        val isWhole = value % 1.0 == 0.0
        if (isWhole) return value.toLong().toString()
        return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Components · ${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}"
                    )
                },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                } else ({}),
                actions = {
                    IconButton(onClick = {
                        if (components.isNotEmpty()) {
                            ShareUtils.shareDaySummary(context, selectedDate, components)
                        } else {
                            Toast.makeText(context, "Nothing to share", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share Summary")
                    }
                    if (showCloseButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Type", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { if (typeOptions.isNotEmpty()) typeMenuExpanded = !typeMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedType?.let { titleCaseType(it) } ?: "No types",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        typeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(titleCaseType(option)) },
                                onClick = {
                                    selectedType = option
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (components.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyText = if (selectedType == "food") {
                        "No food components found for this day."
                    } else {
                        val displayType = selectedType?.let { titleCaseType(it) } ?: "Type"
                        "Summary for $displayType isn't available yet."
                    }
                    Text(emptyText, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(components) { component ->
                        val isExpanded = expandedComponentKey == component.keyName
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { 
                                expandedComponentKey = if (isExpanded) null else component.keyName
                            }
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (component.keyName) {
                                        "energy" -> Icons.Default.LocalFireDepartment
                                        "net weight" -> Icons.Default.MonitorWeight
                                        "protein" -> Icons.Default.SetMeal
                                        "carbohydrate" -> Icons.Default.BakeryDining
                                        "total fat" -> Icons.Default.TripOrigin
                                        "saturated fat" -> Icons.Default.Lens
                                        "dietary fiber" -> Icons.Default.Grass
                                        "sugar" -> Icons.Default.Icecream
                                        "sodium" -> Icons.Default.Waves
                                        "potassium" -> Icons.Default.Bolt
                                        "cholesterol" -> Icons.Default.MonitorHeart
                                        "caffeine" -> Icons.Default.LocalCafe
                                        else -> Icons.Default.QuestionMark
                                    }
                                    
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = component.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        val sourceCount = component.sources.size
                                        Text(
                                            text = "$sourceCount source${if (sourceCount != 1) "s" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Text(
                                        text = "${formatQuantity(component.quantity)} ${component.unit ?: ""}".trim(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                if (isExpanded) {
                                    HorizontalDivider()
                                    Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                                        component.sources.sortedByDescending { it.quantity }.forEach { source ->
                                            val log = dayLogs.find { it.id == source.logId }
                                            if (log != null) {
                                                val analysisTitle = latestAiAnalysis(log.analysisResults)?.title?.trim()
                                                val displayText = analysisTitle?.takeIf { it.isNotBlank() }
                                                    ?: log.note.takeIf { it.isNotBlank() }
                                                    ?: "No details"
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onNavigateToDetail(log.id) }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Thumbnail
                                                    if (log.imagePath.isEmpty()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        }
                                                    } else {
                                                        Box(contentAlignment = Alignment.BottomEnd) {
                                                            AsyncImage(
                                                                model = ImageRequest.Builder(LocalContext.current)
                                                                    .data(File(log.imagePath))
                                                                    .crossfade(true)
                                                                    .build(),
                                                                contentDescription = null,
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .clip(RoundedCornerShape(4.dp)),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            if (!log.isOriginalImage && log.recipeId == null) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 2.dp))
                                                                        .padding(1.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Refresh, 
                                                                        null, 
                                                                        tint = Color.White, 
                                                                        modifier = Modifier.size(8.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    
                                                    Spacer(Modifier.width(12.dp))
                                                    
                                                    Column(Modifier.weight(1f)) {
                                                        Text(displayText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(
                                                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp)),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    
                                                    Text(
                                                        text = "${formatQuantity(source.quantity)} ${component.unit ?: ""}".trim(),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format("%.1f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatDate(millis: Long?): String {
    if (millis == null || millis <= 0) return "N/A"
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}

private fun formatQuantityValue(value: Double): String {
    val isWhole = value % 1.0 == 0.0
    if (isWhole) return value.toLong().toString()
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}

@Composable
fun DatabaseStatsCard(repository: LogRepository) {
    var stats by remember { mutableStateOf<DatabaseStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            stats = repository.getDatabaseStats()
            loading = false
        }
    }

    if (loading) {
        Text("Loading statistics...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        return
    }

    val s = stats
    if (s == null || s.entryCount == 0) {
        Text("No entries found. Database is empty.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        return
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total entries", style = MaterialTheme.typography.bodyMedium)
                Text("${s.entryCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Entries with images", style = MaterialTheme.typography.bodyMedium)
                Text("${s.imageCount}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Image storage", style = MaterialTheme.typography.bodyMedium)
                Text(formatBytes(s.totalImageSizeBytes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Earliest entry", style = MaterialTheme.typography.bodyMedium)
                Text(formatDate(s.earliestTimestamp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Latest entry", style = MaterialTheme.typography.bodyMedium)
                Text(formatDate(s.latestTimestamp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun gpt56ModelLabel(model: String): String {
    return when (model) {
        "gpt-5.6-luna" -> "GPT-5.6 Luna (low cost)"
        "gpt-5.6-terra" -> "GPT-5.6 Terra (balanced)"
        "gpt-5.6-sol" -> "GPT-5.6 Sol (flagship)"
        else -> model
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    themeColor: AppThemeColor,
    onThemeColorChange: (AppThemeColor) -> Unit,
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    repository: LogRepository,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openAiApiType by remember { mutableStateOf(AiPreferences.getOpenAiApiType(context)) }
    var openAiApiTypeExpanded by remember { mutableStateOf(false) }
    var openAiModelLow by remember { mutableStateOf(AiPreferences.getOpenAiModel(context, AiModelPreset.LOW)) }
    var openAiModelLowExpanded by remember { mutableStateOf(false) }
    var openAiModelHigh by remember { mutableStateOf(AiPreferences.getOpenAiModel(context, AiModelPreset.HIGH)) }
    var openAiModelHighExpanded by remember { mutableStateOf(false) }
    var webSearchEnabled by remember { mutableStateOf(AiPreferences.isWebSearchEnabled(context)) }
    
    // Reasoning Level
    var reasoningLevelLow by remember { mutableStateOf(AiPreferences.getReasoningLevel(context, AiModelPreset.LOW)) }
    var reasoningLevelLowExpanded by remember { mutableStateOf(false) }
    var reasoningLevelHigh by remember { mutableStateOf(AiPreferences.getReasoningLevel(context, AiModelPreset.HIGH)) }
    var reasoningLevelHighExpanded by remember { mutableStateOf(false) }
    val reasoningOptions = listOf("low", "medium", "high")

    // Export State
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFilename by remember { mutableStateOf("") }
    var isFullExport by remember { mutableStateOf(false) }
    var exportAll by remember { mutableStateOf(false) }
    
    // Default range: Last 30 days
    var exportStartDate by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var exportEndDate by remember { mutableStateOf(LocalDate.now()) }

    // Helper to pick date
    fun showDatePicker(initialDate: LocalDate, onDatePicked: (LocalDate) -> Unit) {
        val calendar = Calendar.getInstance()
        calendar.set(initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onDatePicked(LocalDate.of(year, month + 1, dayOfMonth))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    // Import State
    var overwriteData by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Helpers
    val defaultFilename = "omnitracker_export_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}"

    // Launchers
    val imageImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "Importing ${uris.size} images...", Toast.LENGTH_SHORT).show()
            scope.launch {
                repository.importImages(uris, overwriteData)
                Toast.makeText(context, "Import Complete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Importing data...", Toast.LENGTH_SHORT).show()
            scope.launch {
                repository.importData(uri, overwriteData)
                Toast.makeText(context, "Import Complete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Data") },
            text = {
                Column {
                    Text("Filename:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportFilename,
                        onValueChange = { exportFilename = it },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    if (exportAll) {
                        Text("Range: All data (no date filter)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Range: ${exportStartDate} to ${exportEndDate}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    scope.launch {
                        val startMillis = if (exportAll) 0L else exportStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endMillis = if (exportAll) Long.MAX_VALUE else exportEndDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

                        if (isFullExport) {
                            Toast.makeText(context, "Preparing ZIP...", Toast.LENGTH_SHORT).show()
                            val file = repository.exportFull(startTime = startMillis, endTime = endMillis, filename = exportFilename)
                            shareFile(context, file, "application/zip")
                        } else {
                            val file = repository.exportLite(startTime = startMillis, endTime = endMillis, filename = exportFilename)
                            shareFile(context, file, "application/json")
                        }
                    }
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Data", color = MaterialTheme.colorScheme.error) },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            text = { Text("Are you absolutely sure? This will delete ALL logs and images. Please export your data first.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.clearAllData()
                            showDeleteDialog = false
                            Toast.makeText(context, "All data deleted", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DELETE EVERYTHING")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                } else ({}),
                actions = {
                    if (showCloseButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- Appearance ---
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Theme", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
            }

            Spacer(Modifier.height(16.dp))
            Text("Color Theme", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppThemeColor.values().forEach { color ->
                    val colorValue = when (color) {
                         AppThemeColor.Green -> Color(0xFF4CAF50)
                         AppThemeColor.Blue -> Color(0xFF2196F3)
                         AppThemeColor.Red -> Color(0xFFF44336)
                         AppThemeColor.Purple -> Color(0xFF9C27B0)
                         AppThemeColor.Orange -> Color(0xFFFF9800)
                         AppThemeColor.Teal -> Color(0xFF009688)
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colorValue)
                            .clickable { onThemeColorChange(color) }
                            .then(
                                if (themeColor == color) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            )
                    )
                }
            }

            Divider(Modifier.padding(vertical = 16.dp))

            // --- AI Analysis ---
            Text("AI Analysis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable AI analysis", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Analyze new logs and store structured results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(checked = aiEnabled, onCheckedChange = { onAiEnabledChange(it) })
            }

            Spacer(Modifier.height(12.dp))

            Text("OpenAI API type", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Completions = /v1/chat/completions, Responses = /v1/responses (recommended).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = openAiApiTypeExpanded,
                onExpandedChange = { openAiApiTypeExpanded = !openAiApiTypeExpanded }
            ) {
                OutlinedTextField(
                    value = openAiApiType.name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openAiApiTypeExpanded) }
                )

	                ExposedDropdownMenu(
	                    expanded = openAiApiTypeExpanded,
	                    onDismissRequest = { openAiApiTypeExpanded = false }
	                ) {
	                    AiPreferences.openAiApiTypeOptions.forEach { apiType ->
	                        DropdownMenuItem(
	                            text = { Text(apiType.name.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }) },
	                            onClick = {
                                openAiApiType = apiType
                                AiPreferences.setOpenAiApiType(context, apiType)
                                openAiModelLow = AiPreferences.getOpenAiModel(context, AiModelPreset.LOW)
                                openAiModelHigh = AiPreferences.getOpenAiModel(context, AiModelPreset.HIGH)
                                openAiApiTypeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val webSearchSupported = openAiApiType == OpenAiApiType.RESPONSES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable web search (Responses API)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Lets the model call the server-side web_search tool during analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = webSearchEnabled,
                    onCheckedChange = {
                        webSearchEnabled = it
                        AiPreferences.setWebSearchEnabled(context, it)
                    },
                    enabled = webSearchSupported
                )
            }

            Spacer(Modifier.height(12.dp))

            Text("Model presets (Low / High)", style = MaterialTheme.typography.bodyLarge)
            Text(
                "GPT-5.6 family: Luna (low cost) · Terra (balanced) · Sol (flagship). Used when OPENAI_API_KEY is set (otherwise Gemini is used).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = openAiModelLowExpanded,
                onExpandedChange = { openAiModelLowExpanded = !openAiModelLowExpanded }
            ) {
                OutlinedTextField(
                    value = gpt56ModelLabel(openAiModelLow),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openAiModelLowExpanded) },
                    label = { Text("Low preset model") }
                )

                ExposedDropdownMenu(
                    expanded = openAiModelLowExpanded,
                    onDismissRequest = { openAiModelLowExpanded = false }
                ) {
                    AiPreferences.getOpenAiModelOptions(openAiApiType).forEach { model ->
                        DropdownMenuItem(
                            text = { Text(gpt56ModelLabel(model)) },
                            onClick = {
                                openAiModelLow = model
                                AiPreferences.setOpenAiModel(context, model, AiModelPreset.LOW)
                                openAiModelLowExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text("Low preset reasoning effort", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Adjust the depth of AI thinking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = reasoningLevelLowExpanded,
                onExpandedChange = { reasoningLevelLowExpanded = !reasoningLevelLowExpanded }
            ) {
                OutlinedTextField(
                    value = reasoningLevelLow.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningLevelLowExpanded) }
                )

                ExposedDropdownMenu(
                    expanded = reasoningLevelLowExpanded,
                    onDismissRequest = { reasoningLevelLowExpanded = false }
                ) {
                    reasoningOptions.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }) },
                            onClick = {
                                reasoningLevelLow = level
                                AiPreferences.setReasoningLevel(context, level, AiModelPreset.LOW)
                                reasoningLevelLowExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Low preset estimated cost: ${AiPreferences.getEstimatedCost(context, AiModelPreset.LOW)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            Text("High preset model", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = openAiModelHighExpanded,
                onExpandedChange = { openAiModelHighExpanded = !openAiModelHighExpanded }
            ) {
                OutlinedTextField(
                    value = gpt56ModelLabel(openAiModelHigh),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openAiModelHighExpanded) }
                )

                ExposedDropdownMenu(
                    expanded = openAiModelHighExpanded,
                    onDismissRequest = { openAiModelHighExpanded = false }
                ) {
                    AiPreferences.getOpenAiModelOptions(openAiApiType).forEach { model ->
                        DropdownMenuItem(
                            text = { Text(gpt56ModelLabel(model)) },
                            onClick = {
                                openAiModelHigh = model
                                AiPreferences.setOpenAiModel(context, model, AiModelPreset.HIGH)
                                openAiModelHighExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("High preset reasoning effort", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = reasoningLevelHighExpanded,
                onExpandedChange = { reasoningLevelHighExpanded = !reasoningLevelHighExpanded }
            ) {
                OutlinedTextField(
                    value = reasoningLevelHigh.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningLevelHighExpanded) }
                )

                ExposedDropdownMenu(
                    expanded = reasoningLevelHighExpanded,
                    onDismissRequest = { reasoningLevelHighExpanded = false }
                ) {
                    reasoningOptions.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }) },
                            onClick = {
                                reasoningLevelHigh = level
                                AiPreferences.setReasoningLevel(context, level, AiModelPreset.HIGH)
                                reasoningLevelHighExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "High preset estimated cost: ${AiPreferences.getEstimatedCost(context, AiModelPreset.HIGH)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Divider(Modifier.padding(vertical = 16.dp))

            // --- Export ---
            Text("Export Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Export all data", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ignore date range and include everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(checked = exportAll, onCheckedChange = { exportAll = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date Range Selection (disabled when exportAll is on)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker(exportStartDate) { exportStartDate = it } },
                    modifier = Modifier.weight(1f),
                    enabled = !exportAll
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Start Date", style = MaterialTheme.typography.labelSmall)
                        Text(exportStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                }
                OutlinedButton(
                    onClick = { showDatePicker(exportEndDate) { exportEndDate = it } },
                    modifier = Modifier.weight(1f),
                    enabled = !exportAll
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("End Date", style = MaterialTheme.typography.labelSmall)
                        Text(exportEndDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    exportFilename = defaultFilename
                    isFullExport = false
                    showExportDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export JSON (Lite)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    exportFilename = defaultFilename
                    isFullExport = true
                    showExportDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export ZIP (Full + Images)")
            }
            
            Divider(Modifier.padding(vertical = 16.dp))

            // --- Import ---
            Text("Import Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Overwrite Existing Data", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = overwriteData, onCheckedChange = { overwriteData = it })
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    imageImportLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Import Images")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    fileImportLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import ZIP / JSONL")
            }

            Divider(Modifier.padding(vertical = 16.dp))

            // --- Database Stats ---
            Text("Database Summary", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            DatabaseStatsCard(repository)

            Divider(Modifier.padding(vertical = 16.dp))

            // --- Danger Zone ---
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(Icons.Default.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Delete App Data")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    log: LogEntity,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onUpdate: (LogEntity) -> Unit,
    onAddLog: (File?, String, Double?, Double?, Boolean, LogEntity?, String?) -> Unit,
    onAnalyze: (LogEntity) -> Unit,
    onViewRecipe: (String) -> Unit,
    aiEnabled: Boolean,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNoteEditDialog by remember { mutableStateOf(false) }
    var showReuseNoteDialog by remember { mutableStateOf(false) }
    val analysis = remember(log.analysisResults) { latestAiAnalysis(log.analysisResults) }
    val isAnalyzing = log.analysisStatus == AiAnalysisStatus.PENDING
    
    // Time Edit State
    val calendar = Calendar.getInstance().apply { timeInMillis = log.timestamp }
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            calendar.set(Calendar.MINUTE, minute)
            onUpdate(log.copy(timestamp = calendar.timeInMillis))
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false
    )
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            // Show time picker after date
            timePickerDialog.show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete this log? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNoteEditDialog) {
        var text by remember { mutableStateOf(log.note) }
        Dialog(onDismissRequest = { showNoteEditDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Edit Note", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = text, 
                        onValueChange = { text = it }, 
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { 
                        onUpdate(log.copy(note = text))
                        showNoteEditDialog = false 
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showReuseNoteDialog) {
        NoteDialog(
            initialNote = log.note,
            onDismiss = { showReuseNoteDialog = false },
            onConfirm = { note ->
                getLastLocation(context) { lat, long ->
                    onAddLog(
                        if (log.imagePath.isNotEmpty()) File(log.imagePath) else null,
                        note,
                        lat,
                        long,
                        false,
                        if (note == log.note) log else null,
                        null
                    )
                    showReuseNoteDialog = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Details") },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                } else ({}),
                actions = {
                    // Show recipe icon if this log is linked to a recipe
                    if (log.recipeId != null) {
                        IconButton(onClick = { onViewRecipe(log.recipeId) }) {
                            Icon(Icons.Default.Restaurant, "View Recipe")
                        }
                    }
                    // Private Toggle
                    IconButton(onClick = { onUpdate(log.copy(isPrivate = !log.isPrivate)) }) {
                        Icon(
                            if (log.isPrivate) Icons.Default.VisibilityOff else Icons.Default.Visibility, 
                            "Toggle Private",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                    if (showCloseButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            focusManager.clearFocus()
                        }
                    )
                }
        ) {
            // Main Image Box
            Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp).background(Color.LightGray)) {
                if (log.imagePath.isEmpty()) {
                     Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Image (Text Log)", color = Color.Gray)
                    }
                } else if (log.isPrivate) {
                    Box(
                        modifier = Modifier.matchParentSize().background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VisibilityOff, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("Private Image", color = Color.White)
                        }
                    }
                } else {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(log.imagePath))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Full Log Image",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth
                        )
                        if (!log.isOriginalImage && log.recipeId == null) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 8.dp),
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 0.dp) // Align to corner
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh, 
                                        contentDescription = "Reused",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reused", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                // Clickable Timestamp + Re-use
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { datePickerDialog.show() },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = SimpleDateFormat("EEEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(log.timestamp)) + " ✎",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedIconButton(onClick = { showReuseNoteDialog = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-use")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (log.latitude != null && log.longitude != null) {
                    Text(
                        text = "📍 Location: ${String.format("%.4f, %.4f", log.latitude, log.longitude)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Clickable Note
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showNoteEditDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Note", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text("✎", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if(log.note.isNotBlank()) log.note else "Tap to add note...", 
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quantity", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(Modifier.width(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = {
                                    val next = (kotlin.math.round(log.quantity).toInt() - 1).coerceAtLeast(1).toDouble()
                                    if (next != log.quantity) onUpdate(log.copy(quantity = next))
                                },
                                enabled = log.quantity > 1.0,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            var quantityText by remember(log.id, log.quantity) {
                                mutableStateOf(formatQuantityValue(log.quantity))
                            }
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = { input ->
                                    val sanitized = input.filter { it.isDigit() || it == '.' }
                                    quantityText = sanitized
                                    val parsed = sanitized.toDoubleOrNull()
                                    if (parsed != null && parsed > 0 && parsed != log.quantity) {
                                        onUpdate(log.copy(quantity = parsed))
                                    } else if (sanitized.isBlank() || parsed == null) {
                                        // Keep typed text (e.g. "0.", "0.5") without forcing log update.
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(96.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            )
                            FilledIconButton(
                                onClick = {
                                    val next = (kotlin.math.round(log.quantity).toInt() + 1).toDouble()
                                    onUpdate(log.copy(quantity = next))
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        val clipboardManager = LocalClipboardManager.current
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("AI Analysis", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAnalyzing) {
                                    Text("Analyzing...", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                    Spacer(Modifier.width(8.dp))
                                }
                                
                                if (log.analysisStatus == AiAnalysisStatus.ERROR || analysis != null) {
                                    IconButton(
                                        onClick = {
                                            val textToCopy = if (log.analysisStatus == AiAnalysisStatus.ERROR) {
                                                "Error: ${log.analysisError}"
                                            } else {
                                                buildString {
                                                    append("Title: ${analysis?.title}\n")
                                                    append("Type: ${analysis?.type}\n")
                                                    if (!analysis?.components.isNullOrEmpty()) {
                                                        append("Components:\n")
                                                        analysis?.components?.forEach { 
                                                            append("- ${it.name}: ${it.quantity} ${it.unit}\n") 
                                                        }
                                                    }
                                                }
                                            }
                                            clipboardManager.setText(AnnotatedString(textToCopy))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy, 
                                            contentDescription = "Copy Analysis", 
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }

                        if (isAnalyzing) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        val analysisTimestamp = log.analysisUpdatedAt?.let {
                            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(it))
                        }
                        if (analysisTimestamp != null || log.analysisModel != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = listOfNotNull(
                                    log.analysisModel?.let { "Model: $it" },
                                    analysisTimestamp?.let { "Updated: $it" }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }

                        Spacer(Modifier.height(8.dp))

	                        when {
	                            !aiEnabled -> {
	                                Text("Enable AI analysis in Settings to use this feature.", style = MaterialTheme.typography.bodyMedium)
	                            }
	                            log.imagePath.isEmpty() && log.note.isBlank() -> {
	                                Text("Add a note or photo to analyze.", style = MaterialTheme.typography.bodyMedium)
	                            }
	                            log.analysisStatus == AiAnalysisStatus.ERROR -> {
	                                Text(
	                                    text = "Last analysis failed: ${log.analysisError ?: "Unknown error"}",
	                                    style = MaterialTheme.typography.bodyMedium,
	                                    color = MaterialTheme.colorScheme.error
	                                )
	                            }
	                            log.analysisStatus == AiAnalysisStatus.PENDING && !log.analysisError.isNullOrBlank() -> {
	                                Text(
	                                    text = "Retrying: ${log.analysisError}",
	                                    style = MaterialTheme.typography.bodyMedium,
	                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
	                                )
	                            }
	                        }

                        if (analysis != null) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Title: ${analysis.title ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Type: ${analysis.type ?: "Unknown"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            // Components are rendered in their own section below.
                        } else if (log.analysisStatus == AiAnalysisStatus.COMPLETE) {
                            Spacer(Modifier.height(12.dp))
                            Text("No structured data returned.", style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onAnalyze(log) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = aiEnabled && !isAnalyzing && (log.imagePath.isNotEmpty() || log.note.isNotBlank())
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (analysis == null) "Analyze" else "Re-analyze")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (analysis != null && analysis.components.isNotEmpty()) {
                    Text("Components", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        Text("Name", Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Qty", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Net", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("Unit", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    analysis.components.forEach { component ->
                        val net = component.quantity?.let { it * log.quantity }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(component.name ?: "-", Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                                Text(component.quantity?.let { formatQuantityValue(it) } ?: "-", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                Text(net?.let { formatQuantityValue(it) } ?: "-", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(component.unit ?: "-", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (!log.isPrivate && log.imagePath.isNotEmpty()) {
                    Button(
                        onClick = {
                            exportImageToGallery(context, File(log.imagePath))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save to Gallery")
                    }
                }
            }
        }
    }
}

@Composable
fun LogList(
    logs: List<LogEntity>,
    onLogClick: (LogEntity) -> Unit,
    onDaySummaryClick: () -> Unit,
    daySummaryCount: Int
) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No logs for this day.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DaySummaryCard(daySummaryCount = daySummaryCount, onClick = onDaySummaryClick)
            }
            items(logs) { log ->
                LogItem(log, onClick = { onLogClick(log) })
            }
        }
    }
}

@Composable
fun DaySummaryCard(daySummaryCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Summarize, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Day summary",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (daySummaryCount == 0) "No food components detected yet" else "$daySummaryCount unique food components",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntity, onClick: () -> Unit) {
    val analysisTitle = remember(log.analysisResults) { latestAiAnalysis(log.analysisResults)?.title?.trim() }
    val displayText = if (log.isPrivate) {
        "Private entry"
    } else {
        analysisTitle?.takeIf { it.isNotBlank() }
            ?: log.note.takeIf { it.isNotBlank() }
            ?: "No details"
    }
    val isAnalyzing = log.analysisStatus == AiAnalysisStatus.PENDING

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (log.imagePath.isEmpty()) {
                 Box(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
            } else if (log.isPrivate) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VisibilityOff, null, tint = Color.White)
                }
            } else {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(log.imagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight()
                            .background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                    if (!log.isOriginalImage && log.recipeId == null) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp))
                                .padding(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh, 
                                null, 
                                tint = Color.White, 
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (log.latitude != null) {
                    Text(
                        text = "📍 Recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
	            }

	            Column(
	                modifier = Modifier.padding(end = 16.dp),
	                horizontalAlignment = Alignment.End,
	                verticalArrangement = Arrangement.Center
	            ) {
	                Text(
	                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp)),
	                    style = MaterialTheme.typography.labelMedium,
	                    color = MaterialTheme.colorScheme.primary
	                )
	                if (isAnalyzing) {
	                    Spacer(Modifier.height(6.dp))
	                    CircularProgressIndicator(
	                        modifier = Modifier.size(16.dp),
	                        strokeWidth = 2.dp
	                    )
	                }
	            }
	        }
	    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelector(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val days = (0..30).map { LocalDate.now().minusDays(it.toLong()) }
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        reverseLayout = true 
    ) {
        items(days) { date ->
            val isSelected = date == selectedDate
            Card(
                onClick = { onDateSelected(date) },
                modifier = Modifier.width(64.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEE")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NoteDialog(initialNote: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialNote) }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Add details", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onConfirm(text) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Log")
                }
            }
        }
    }
}

// --- Helpers ---

fun createImageFile(context: Context): File {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
}

@SuppressLint("MissingPermission")
fun getLastLocation(context: Context, onLocationResult: (Double?, Double?) -> Unit) {
    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    onLocationResult(location.latitude, location.longitude)
                } else {
                    onLocationResult(null, null)
                }
            }
            .addOnFailureListener {
                onLocationResult(null, null)
            }
    } else {
        onLocationResult(null, null)
    }
}

fun exportImageToGallery(context: Context, imageFile: File) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "HealthTrack_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HealthTrack_Exports")
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

    if (uri != null) {
        try {
            resolver.openOutputStream(uri).use { outputStream ->
                FileInputStream(imageFile).use { inputStream ->
                    inputStream.copyTo(outputStream!!)
                }
            }
            Toast.makeText(context, "Exported to Gallery!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
        }
    }
}

fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Data"))
}
