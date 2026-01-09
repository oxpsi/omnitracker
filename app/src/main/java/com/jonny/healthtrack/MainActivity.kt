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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.jonny.healthtrack.ai.latestAiAnalysis
import com.jonny.healthtrack.data.AppDatabase
import com.jonny.healthtrack.data.LogEntity
import com.jonny.healthtrack.data.LogRepository
import com.jonny.healthtrack.util.normalizeCapturedJpegInPlace
import com.jonny.healthtrack.util.aggregateFoodComponents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
    data class Detail(val logId: String) : Screen()
}

class MainActivity : ComponentActivity() {
    private lateinit var repository: LogRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Init Data Layer
        val db = AppDatabase.getDatabase(this)
        repository = LogRepository(this, db.logDao())
        
        // Trigger Migration (Background)
        repository.checkAndMigrateLegacyData()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            HealthTrackTheme(darkTheme = isDarkTheme) {
                AppContent(
                    repository = repository,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun HealthTrackTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF8BC34A),
            secondary = Color(0xFFAED581),
            tertiary = Color(0xFFDCEDC8)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4CAF50),
            secondary = Color(0xFF8BC34A),
            tertiary = Color(0xFFCDDC39)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun AppContent(
    repository: LogRepository,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
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

    fun createLog(file: File?, note: String, lat: Double?, long: Double?, isOriginal: Boolean) {
        scope.launch {
            val newLog = LogEntity(
                timestamp = System.currentTimeMillis(),
                imagePath = file?.absolutePath ?: "",
                note = note,
                latitude = lat,
                longitude = long,
                isOriginalImage = isOriginal
            )
            repository.addLog(newLog)
            selectedDate = Instant.ofEpochMilli(newLog.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            if (aiEnabled && (newLog.imagePath.isNotEmpty() || newLog.note.isNotBlank())) {
                repository.analyzeLog(newLog)
            }
            // Navigate to detail after creation
            currentScreen = Screen.Detail(newLog.id)
        }
    }

    fun updateAiEnabled(enabled: Boolean) {
        aiEnabled = enabled
        AiPreferences.setEnabled(context, enabled)
    }

    fun requestAnalysis(log: LogEntity, force: Boolean, showErrors: Boolean) {
        scope.launch {
            val result = repository.analyzeLog(log, force)
            if (showErrors && result.isFailure) {
                val message = result.exceptionOrNull()?.message ?: "AI analysis failed"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                        onAddLog = { file, note, lat, long, isOriginal ->
                            createLog(file, note, lat, long, isOriginal)
                        },
                        onNavigateToSettings = { toggleSettingsPane() },
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
                                        onAnalyze = { logToAnalyze ->
                                            requestAnalysis(logToAnalyze, force = true, showErrors = true)
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
                                    showBackButton = false,
                                    showCloseButton = true
                                )
                            }
                            Screen.Settings -> SettingsScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                aiEnabled = aiEnabled,
                                onAiEnabledChange = { updateAiEnabled(it) },
                                onBack = { currentScreen = Screen.Home },
                                repository = repository,
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
                    onAddLog = { file, note, lat, long, isOriginal ->
                        createLog(file, note, lat, long, isOriginal)
                    },
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    onNavigateToDetail = { logId -> currentScreen = Screen.Detail(logId) },
                    onNavigateToDaySummary = { currentScreen = Screen.DaySummary },
                    isWideScreen = false
                )
                Screen.Settings -> SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    aiEnabled = aiEnabled,
                    onAiEnabledChange = { updateAiEnabled(it) },
                    onBack = { currentScreen = Screen.Home },
                    repository = repository,
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
                            onAnalyze = { logToAnalyze ->
                                requestAnalysis(logToAnalyze, force = true, showErrors = true)
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
    onAddLog: (File?, String, Double?, Double?, Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
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
    var showCameraNoteDialog by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraLat by remember { mutableStateOf<Double?>(null) }
    var pendingCameraLong by remember { mutableStateOf<Double?>(null) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    val filteredLogs = logs.filter {
        val logDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        logDate == selectedDate
    }

    // 1. Camera: Immediately creates log on success
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoFile != null) {
            getLastLocation(context) { lat, long ->
                val capturedFile = tempPhotoFile
                scope.launch(Dispatchers.IO) {
                    val normalized = capturedFile?.let { normalizeCapturedJpegInPlace(it) } ?: capturedFile
                    withContext(Dispatchers.Main) {
                        pendingCameraFile = normalized
                        pendingCameraLat = lat
                        pendingCameraLong = long
                        showCameraNoteDialog = true
                    }
                }
            }
        }
        tempPhotoFile = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            val photoFile = createImageFile(context)
            tempPhotoFile = photoFile
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(uri)
        }
    }

    if (showReuseDialog) {
        val recentLogs = logs.take(20)
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
                        false // Original = false (Reuse)
                    )
                    showReuseNoteDialog = false
                    reuseTemplateLog = null
                }
            }
        )
    }

    if (showCameraNoteDialog && pendingCameraFile != null) {
        NoteDialog(
            initialNote = "",
            onDismiss = {
                pendingCameraFile?.let { file ->
                    try {
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {
                    }
                }
                pendingCameraFile = null
                pendingCameraLat = null
                pendingCameraLong = null
                showCameraNoteDialog = false
            },
            onConfirm = { note ->
                onAddLog(pendingCameraFile, note, pendingCameraLat, pendingCameraLong, true)
                pendingCameraFile = null
                pendingCameraLat = null
                pendingCameraLong = null
                showCameraNoteDialog = false
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
                    onAddLog(null, note, lat, long, true) // No image
                    showNoteDialog = false
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
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
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    val components = remember(dayLogs) { aggregateFoodComponents(dayLogs) }

    fun formatQuantity(value: Double): String {
        val isWhole = value % 1.0 == 0.0
        if (isWhole) return value.toLong().toString()
        return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

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
                    if (showCloseButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (components.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No food components found for this day.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(components) { component ->
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = component.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                component.unit?.let { unit ->
                                    Text(
                                        text = unit,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Text(
                                text = formatQuantity(component.quantity),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    aiEnabled: Boolean,
    onAiEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    repository: LogRepository,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openAiModel by remember { mutableStateOf(AiPreferences.getOpenAiModel(context)) }
    var openAiModelExpanded by remember { mutableStateOf(false) }

    // Export State
    var showExportDialog by remember { mutableStateOf(false) }
    var exportFilename by remember { mutableStateOf("") }
    var isFullExport by remember { mutableStateOf(false) }
    
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
                    Text("Range: ${exportStartDate} to ${exportEndDate}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showExportDialog = false
                    scope.launch {
                        // Convert LocalDate to millis
                        val startMillis = exportStartDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endMillis = exportEndDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

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

            Text("OpenAI model", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Used when OPENAI_API_KEY is set (otherwise Gemini is used).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = openAiModelExpanded,
                onExpandedChange = { openAiModelExpanded = !openAiModelExpanded }
            ) {
                OutlinedTextField(
                    value = openAiModel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = openAiModelExpanded) }
                )

                ExposedDropdownMenu(
                    expanded = openAiModelExpanded,
                    onDismissRequest = { openAiModelExpanded = false }
                ) {
                    AiPreferences.openAiModelOptions.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model) },
                            onClick = {
                                openAiModel = model
                                AiPreferences.setOpenAiModel(context, model)
                                openAiModelExpanded = false
                            }
                        )
                    }
                }
            }

            Divider(Modifier.padding(vertical = 16.dp))

            // --- Export ---
            Text("Export Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            // Date Range Selection
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDatePicker(exportStartDate) { exportStartDate = it } },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Start Date", style = MaterialTheme.typography.labelSmall)
                        Text(exportStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                }
                OutlinedButton(
                    onClick = { showDatePicker(exportEndDate) { exportEndDate = it } },
                    modifier = Modifier.weight(1f)
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
    onAnalyze: (LogEntity) -> Unit,
    aiEnabled: Boolean,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNoteEditDialog by remember { mutableStateOf(false) }
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
                        if (!log.isOriginalImage) {
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
                // Clickable Timestamp
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
                            if (analysis.components.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text("Components", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                
                                // Table Header
                                Row(Modifier.fillMaxWidth()) {
                                    Text("Name", Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Qty", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Unit", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                }
                                Divider(Modifier.padding(vertical = 4.dp))
                                
                                // Table Body
                                analysis.components.forEach { component ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text(component.name ?: "-", Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                                        Text(component.quantity?.toString() ?: "-", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                        Text(component.unit ?: "-", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
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
                    text = "Food components summary",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (daySummaryCount == 0) "No components detected yet" else "$daySummaryCount unique components",
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
    val displayText = analysisTitle?.takeIf { it.isNotBlank() }
        ?: log.note.takeIf { it.isNotBlank() }
        ?: "No details"

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

            Text(
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 16.dp)
            )
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
