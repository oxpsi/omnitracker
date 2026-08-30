package com.oxpsi.omnitracker

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import com.oxpsi.omnitracker.ai.AiAnalysisStatus
import com.oxpsi.omnitracker.ai.AiAnalysisService
import com.oxpsi.omnitracker.ai.AiAnalysisWork
import com.oxpsi.omnitracker.ai.AiPreferences
import com.oxpsi.omnitracker.ai.AiPrompts
import com.oxpsi.omnitracker.ai.latestAiAnalysis
import com.oxpsi.omnitracker.ai.providers.ChatCompletionsProvider
import com.oxpsi.omnitracker.data.AppDatabase
import com.oxpsi.omnitracker.data.LogEntity
import com.oxpsi.omnitracker.data.LogRepository
import com.oxpsi.omnitracker.data.RecipeRepository
import com.oxpsi.omnitracker.data.DatabaseStats
import com.oxpsi.omnitracker.util.aggregateFoodComponents
import com.oxpsi.omnitracker.util.caloricContributionPercent
import com.oxpsi.omnitracker.util.normalizeCapturedJpegInPlace
import com.oxpsi.omnitracker.util.AppThemeColor
import com.oxpsi.omnitracker.util.ThemePreferences
import com.oxpsi.omnitracker.util.ShareUtils
import androidx.compose.runtime.CompositionLocalProvider
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

// --- Navigation State ---
sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
    object DaySummary : Screen()
    object DatePicker : Screen()
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
            val context = LocalContext.current
            var isDarkTheme by remember { mutableStateOf(ThemePreferences.isDarkTheme(context)) }

            // Theme Color State
            var themeColor by remember { mutableStateOf(ThemePreferences.getThemeColor(context)) }

            OmniTrackerTheme(darkTheme = isDarkTheme, themeColor = themeColor) {
                AppContent(
                    repository = repository,
                    recipeRepository = recipeRepository,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        ThemePreferences.setDarkTheme(context, isDarkTheme)
                    },
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
fun OmniTrackerTheme(
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
                background = Color(0xFF101012),
                surface = Color(0xFF101012),
                surfaceVariant = Color(0xFF2A2A2E),
                primaryContainer = Color(0xFF4A6B2E),
                secondaryContainer = Color(0xFF5A7D33),
                tertiaryContainer = Color(0xFF6B8E3D)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF4CAF50),
                secondary = Color(0xFF8BC34A),
                tertiary = Color(0xFFCDDC39),
                background = Color(0xFFF2F2F0),
                surface = Color(0xFFF2F2F0),
                surfaceVariant = Color(0xFFE4E4E0),
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
                background = Color(0xFF101012),
                surface = Color(0xFF101012),
                surfaceVariant = Color(0xFF2A2A2E),
                primaryContainer = Color(0xFF1B5E8A),
                secondaryContainer = Color(0xFF2273A0),
                tertiaryContainer = Color(0xFF2D87B5)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF2196F3),
                secondary = Color(0xFF03A9F4),
                tertiary = Color(0xFFB3E5FC),
                background = Color(0xFFF2F2F0),
                surface = Color(0xFFF2F2F0),
                surfaceVariant = Color(0xFFE4E4E0),
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
                background = Color(0xFF101012),
                surface = Color(0xFF101012),
                surfaceVariant = Color(0xFF2A2A2E),
                primaryContainer = Color(0xFF8A2A2A),
                secondaryContainer = Color(0xFFA03030),
                tertiaryContainer = Color(0xFFB53838)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFF44336),
                secondary = Color(0xFFE57373),
                tertiary = Color(0xFFFFCDD2),
                background = Color(0xFFF2F2F0),
                surface = Color(0xFFF2F2F0),
                surfaceVariant = Color(0xFFE4E4E0),
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
                background = Color(0xFF101012),
                surface = Color(0xFF101012),
                surfaceVariant = Color(0xFF2A2A2E),
                primaryContainer = Color(0xFF6A3A72),
                secondaryContainer = Color(0xFF7A4482),
                tertiaryContainer = Color(0xFF8A5092)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF9C27B0),
                secondary = Color(0xFFBA68C8),
                tertiary = Color(0xFFE1BEE7),
                background = Color(0xFFF2F2F0),
                surface = Color(0xFFF2F2F0),
                surfaceVariant = Color(0xFFE4E4E0),
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
                background = Color(0xFF101012),
                surface = Color(0xFF101012),
                surfaceVariant = Color(0xFF2A2A2E),
                primaryContainer = Color(0xFF8A5A1E),
                secondaryContainer = Color(0xFF9D6925),
                tertiaryContainer = Color(0xFFB0782C)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFFF9800),
                secondary = Color(0xFFFFB74D),
                tertiary = Color(0xFFFFE0B2),
                background = Color(0xFFF2F2F0),
                surface = Color(0xFFF2F2F0),
                surfaceVariant = Color(0xFFE4E4E0),
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
                background = Color(0xFF101012),
                surface = Color(0xFF101012),
                surfaceVariant = Color(0xFF2A2A2E),
                primaryContainer = Color(0xFF1A5E58),
                secondaryContainer = Color(0xFF227068),
                tertiaryContainer = Color(0xFF2D827A)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF009688),
                secondary = Color(0xFF26A69A),
                tertiary = Color(0xFFB2DFDB),
                background = Color(0xFFF2F2F0),
                surface = Color(0xFFF2F2F0),
                surfaceVariant = Color(0xFFE4E4E0),
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
    val recipes by recipeRepository.allRecipes.collectAsState(initial = emptyList())
    val recipeImageMap = remember(recipes) { recipes.associate { it.id to it.imagePath }.filterValues { it.isNotEmpty() } }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val datesWithEntries = remember(logs) {
        logs.mapTo(mutableSetOf()) { log ->
            Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    fun navigateToDate(date: LocalDate) {
        selectedDate = date
        currentScreen = Screen.Home
    }

    CompositionLocalProvider(LocalRecipeImages provides recipeImageMap) {

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
     * chose "note only"), no file is written: the log's imagePath stays empty
     * (so it behaves like a text log w.r.t. Save to Gallery), and a themed
     * thumbnail derived from the recipe image is rendered dynamically in the UI.
     */
    fun createLogFromRecipe(recipeId: String, imagePath: String, note: String) {
        scope.launch {
            val imageFile = imagePath.takeIf { it.isNotEmpty() }?.let { File(it) }
            getLastLocation(context) { lat, long ->
                createLog(imageFile, note, lat, long, true, null, recipeId)
                scope.launch { recipeRepository.bumpLastActivity(recipeId) }
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

	    fun cancelAnalysis(log: LogEntity) {
	        AiAnalysisWork.cancel(context, log.id)
	        AiAnalysisService.cancel(log.id)
	        scope.launch {
	            repository.updateLog(log.copy(
	                analysisStatus = null,
	                analysisError = null,
	                analysisUpdatedAt = System.currentTimeMillis()
	            ))
	            Toast.makeText(context, "Analysis cancelled", Toast.LENGTH_SHORT).show()
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
                        onNavigateToCalendar = { currentScreen = Screen.DatePicker },
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
                                        onCancelAnalysis = { logToCancel ->
                                            cancelAnalysis(logToCancel)
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
                            Screen.DatePicker -> CalendarScreen(
                                selectedDate = selectedDate,
                                datesWithEntries = datesWithEntries,
                                onDateSelected = { navigateToDate(it) },
                                onBack = { currentScreen = Screen.Home },
                                showBackButton = false,
                                showCloseButton = true
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
                    onNavigateToCalendar = { currentScreen = Screen.DatePicker },
                    isWideScreen = false
                )
                Screen.DatePicker -> CalendarScreen(
                    selectedDate = selectedDate,
                    datesWithEntries = datesWithEntries,
                    onDateSelected = { navigateToDate(it) },
                    onBack = { currentScreen = Screen.Home },
                    showBackButton = true,
                    showCloseButton = false
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
                            onCancelAnalysis = { logToCancel ->
                                cancelAnalysis(logToCancel)
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
    onNavigateToCalendar: () -> Unit,
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
                        template.recipeId // Preserve recipe association on reuse
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
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        DateSelector(selectedDate) { newDate ->
                            onSelectedDateChange(newDate)
                        }
                    }
                    CalendarIconButton(onClick = onNavigateToCalendar)
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
                selectedDate = selectedDate,
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
                                    val contributionPct = caloricContributionPercent(component, components)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${formatQuantity(component.quantity)} ${component.unit ?: ""}".trim(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (contributionPct != null) {
                                            Text(
                                                text = String.format(Locale.US, "%.0f%% energy", contributionPct),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                            )
                                        }
                                    }
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
                                                    if (log.imagePath.isEmpty() && log.recipeId != null && !log.isPrivate) {
                                                        val recipeImg = LocalRecipeImages.current[log.recipeId]
                                                        if (recipeImg != null) {
                                                            Box(contentAlignment = Alignment.BottomEnd) {
                                                                RecipeDerivedThumbnail(
                                                                    recipeImagePath = recipeImg,
                                                                    modifier = Modifier.size(32.dp),
                                                                    cornerRadius = 4.dp
                                                                )
                                                                if (!log.isOriginalImage) {
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
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(32.dp)
                                                                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            }
                                                        }
                                                    } else if (log.imagePath.isEmpty()) {
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

private enum class SettingsSection(val title: String) {
    Appearance("Appearance"),
    AI("AI Analysis"),
    Data("Data")
}

@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
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

    // AI provider settings (chat completions endpoint)
    var chatBaseUrl by remember { mutableStateOf(AiPreferences.getBaseUrl(context)) }
    var chatApiKey by remember { mutableStateOf(AiPreferences.getApiKey(context)) }
    var chatModel by remember { mutableStateOf(AiPreferences.getModel(context)) }
    var discoveredModels by remember { mutableStateOf(AiPreferences.getDiscoveredModels(context)) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var discoverError by remember { mutableStateOf<String?>(null) }
    var showApiKey by remember { mutableStateOf(false) }

    // Reasoning effort
    var reasoningLevel by remember { mutableStateOf(AiPreferences.getReasoningLevel(context)) }
    var reasoningMenuExpanded by remember { mutableStateOf(false) }
    val reasoningOptions = AiPreferences.reasoningOptionList

    // Custom analysis prompt (shows the currently used prompt; falls back to built-in default)
    var analysisPrompt by remember {
        mutableStateOf(
            AiPreferences.getCustomPrompt(context).takeIf { it.isNotBlank() }
                ?: AiPrompts.DEFAULT_ANALYSIS_PROMPT
        )
    }

    // Settings sub-page navigation (null = hub)
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    BackHandler(enabled = section != null) { section = null }

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
                title = { Text(section?.title ?: "Settings") },
                navigationIcon = {
                    when {
                        section != null -> {
                            IconButton(onClick = { section = null }) {
                                Icon(Icons.Default.ArrowBack, "Back")
                            }
                        }
                        showBackButton -> {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, "Back")
                            }
                        }
                        else -> {}
                    }
                },
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
            when (section) {
                null -> {
                    SettingsNavRow(
                        title = "Appearance",
                        subtitle = "Dark theme and accent colors",
                        icon = Icons.Default.Palette
                    ) { section = SettingsSection.Appearance }
                    SettingsNavRow(
                        title = "AI Analysis",
                        subtitle = "Endpoint, model, reasoning and prompt",
                        icon = Icons.Default.SmartToy
                    ) { section = SettingsSection.AI }
                    SettingsNavRow(
                        title = "Data",
                        subtitle = "Export, import, database and deletion",
                        icon = Icons.Default.Storage
                    ) { section = SettingsSection.Data }
                }
                SettingsSection.Appearance -> {
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
                }
                SettingsSection.AI -> {
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

            Text("Chat Completions Endpoint", style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = chatBaseUrl,
                onValueChange = {
                    chatBaseUrl = it
                    AiPreferences.setBaseUrl(context, it)
                },
                label = { Text("Base URL") },
                placeholder = { Text(AiPreferences.DEFAULT_BASE_URL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = chatApiKey,
                onValueChange = {
                    chatApiKey = it
                    AiPreferences.setApiKey(context, it)
                },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            Text("Model", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Tap Discover to fetch available models from the endpoint, then pick one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = !modelMenuExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = chatModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Model") },
                        placeholder = { Text("Discover models first") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) }
                    )

                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        val menuItems = buildList {
                            if (chatModel.isNotBlank() && chatModel !in discoveredModels) add(chatModel)
                            addAll(discoveredModels)
                        }
                        if (menuItems.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No models — tap Discover") },
                                onClick = { modelMenuExpanded = false },
                                enabled = false
                            )
                        } else {
                            menuItems.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        chatModel = model
                                        AiPreferences.setModel(context, model)
                                        modelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (chatBaseUrl.isBlank() || chatApiKey.isBlank()) {
                            discoverError = "Set Base URL and API Key first"
                            return@OutlinedButton
                        }
                        discovering = true
                        discoverError = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ChatCompletionsProvider.discoverModels(chatBaseUrl, chatApiKey)
                            }
                            discovering = false
                            result.onSuccess { models ->
                                discoveredModels = models
                                AiPreferences.setDiscoveredModels(context, models)
                                if (models.isEmpty()) {
                                    discoverError = "Endpoint returned no models"
                                } else if (chatModel.isBlank() || chatModel !in models) {
                                    chatModel = models.first()
                                    AiPreferences.setModel(context, chatModel)
                                }
                            }.onFailure { e ->
                                discoverError = e.message ?: "Discovery failed"
                            }
                        }
                    },
                    enabled = !discovering
                ) {
                    if (discovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Discover models")
                        Spacer(Modifier.width(6.dp))
                        Text("Discover")
                    }
                }
            }
            discoverError?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (discoveredModels.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${discoveredModels.size} models discovered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Reasoning effort", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Depth of AI thinking (applied only when the selected model supports it).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = reasoningMenuExpanded,
                onExpandedChange = { reasoningMenuExpanded = !reasoningMenuExpanded }
            ) {
                OutlinedTextField(
                    value = reasoningLevel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reasoningMenuExpanded) }
                )

                ExposedDropdownMenu(
                    expanded = reasoningMenuExpanded,
                    onDismissRequest = { reasoningMenuExpanded = false }
                ) {
                    reasoningOptions.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }) },
                            onClick = {
                                reasoningLevel = level
                                AiPreferences.setReasoningLevel(context, level)
                                reasoningMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Analysis Prompt", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "The prompt currently used for analysis. Your text is sent first and the log note is automatically appended after it. Edits are saved automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = analysisPrompt,
                onValueChange = {
                    analysisPrompt = it
                    AiPreferences.setCustomPrompt(context, it)
                },
                label = { Text("Custom prompt") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = {
                analysisPrompt = AiPrompts.DEFAULT_ANALYSIS_PROMPT
                AiPreferences.setCustomPrompt(context, "")
            }) {
                Text("Use default prompt")
            }
                }
                SettingsSection.Data -> {
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
    onCancelAnalysis: (LogEntity) -> Unit,
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
                        log.recipeId // Preserve recipe association on reuse
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
                if (log.imagePath.isEmpty() && log.recipeId != null && !log.isPrivate) {
                    val recipeImg = LocalRecipeImages.current[log.recipeId]
                    if (recipeImg != null) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            RecipeDerivedThumbnail(
                                recipeImagePath = recipeImg,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp)
                            )
                            if (!log.isOriginalImage) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 8.dp),
                                    color = Color.Black.copy(alpha = 0.6f)
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
                    } else {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No Image (Text Log)", color = Color.Gray)
                        }
                    }
                } else if (log.imagePath.isEmpty()) {
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
                                    TextButton(
                                        onClick = { onCancelAnalysis(log) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Stop", style = MaterialTheme.typography.labelMedium)
                                    }
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
                                                    if (!analysis?.foodItems.isNullOrEmpty()) {
                                                        append("Food Items:\n")
                                                        analysis?.foodItems?.forEach {
                                                            append("- $it\n")
                                                        }
                                                    }
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
                            if (analysis.foodItems.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Food Items", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                analysis.foodItems.forEach { item ->
                                    Text(
                                        text = "• $item",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
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
    selectedDate: LocalDate,
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
                DaySummaryCard(
                    daySummaryCount = daySummaryCount,
                    selectedDate = selectedDate,
                    onClick = onDaySummaryClick
                )
            }
            items(logs) { log ->
                LogItem(log, onClick = { onLogClick(log) })
            }
        }
    }
}

@Composable
fun DaySummaryCard(daySummaryCount: Int, selectedDate: LocalDate, onClick: () -> Unit) {
    val today = LocalDate.now()
    val includeYear = selectedDate.year != today.year
    val pattern = if (includeYear) "MMMM d, yyyy" else "MMMM d"
    val title = "Summary for " + selectedDate.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

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
                    text = title,
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
            if (log.imagePath.isEmpty() && log.recipeId != null && !log.isPrivate) {
                val recipeImg = LocalRecipeImages.current[log.recipeId]
                if (recipeImg != null) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        RecipeDerivedThumbnail(
                            recipeImagePath = recipeImg,
                            modifier = Modifier.width(80.dp).fillMaxHeight()
                        )
                        if (!log.isOriginalImage) {
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
                } else {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.secondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }
                }
            } else if (log.imagePath.isEmpty()) {
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
    val today = LocalDate.now()
    val halfWindow = 15
    val start = selectedDate.minusDays(halfWindow.toLong())
    val end = run {
        val candidate = selectedDate.plusDays(halfWindow.toLong())
        if (candidate.isAfter(today)) today else candidate
    }
    val count = (end.toEpochDay() - start.toEpochDay() + 1L).toInt()
    val days = (0 until count).map { start.plusDays(it.toLong()) }
    val selectedIndex = (selectedDate.toEpochDay() - start.toEpochDay()).toInt()

    BoxWithConstraints {
        val viewportPx = with(LocalDensity.current) { maxWidth.toPx() }
        val itemWidthPx = with(LocalDensity.current) { 64.dp.toPx() }
        val spacingPx = with(LocalDensity.current) { 8.dp.toPx() }
        // scrollToItem's offset is how far *past* the item's start the container
        // left edge lands (positive => item scrolls off the left edge). To center
        // the item we move the container's left edge to before the item's start,
        // hence the negative value. Spacing is subtracted so the item's visual
        // center (not its leading edge + gap) lands in the viewport center.
        val centerOffsetPx = (itemWidthPx / 2f - viewportPx / 2f - spacingPx / 2f).toInt()
        val state = rememberLazyListState()
        LaunchedEffect(selectedDate, days.size) {
            state.scrollToItem(selectedIndex, centerOffsetPx)
        }
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(days) { date ->
                val isSelected = date == selectedDate
                val isFuture = date.isAfter(today)
                Card(
                    onClick = { onDateSelected(date) },
                    enabled = !isFuture,
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
}

@Composable
fun CalendarIconButton(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = "Open calendar",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    selectedDate: LocalDate,
    datesWithEntries: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    var displayMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val today = LocalDate.now()
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a day") },
                navigationIcon = if (showBackButton) {
                    {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                } else ({}),
                actions = {
                    TextButton(onClick = {
                        displayMonth = YearMonth.from(today)
                        onDateSelected(today)
                    }) {
                        Text("Today")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Previous month")
                }
                Text(
                    text = displayMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, "Next month")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val weekDayLabels = remember(firstDayOfWeek) {
                (0..6).map { offset -> firstDayOfWeek.plus(offset.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val firstOfMonth = displayMonth.atDay(1)
            val leadingBlanks = (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
            val cells: List<LocalDate?> = List(leadingBlanks) { null } +
                (1..displayMonth.lengthOfMonth()).map { displayMonth.atDay(it) }
            val weeks = cells.chunked(7)

            weeks.forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (date != null) {
                                CalendarDayCell(
                                    date = date,
                                    isSelected = date == selectedDate,
                                    hasEntries = date in datesWithEntries,
                                    isFuture = date.isAfter(today),
                                    onClick = { onDateSelected(date) }
                                )
                            } else {
                                Spacer(modifier = Modifier.aspectRatio(1f))
                            }
                        }
                    }
                    // Pad the final row so cells keep a consistent size.
                    repeat(7 - week.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Days with entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    hasEntries: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    hasEntries -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }
            )
            .alpha(if (isFuture) 0.3f else 1f)
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || hasEntries) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                hasEntries -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
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
        put(MediaStore.Images.Media.DISPLAY_NAME, "OmniTracker_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OmniTracker_Exports")
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
