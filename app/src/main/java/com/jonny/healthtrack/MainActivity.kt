package com.jonny.healthtrack

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.jonny.healthtrack.data.AppDatabase
import com.jonny.healthtrack.data.LogEntity
import com.jonny.healthtrack.data.LogRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// --- Navigation State ---
sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
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
    
    // Subscribe to DB Flow
    val logs by repository.allLogs.collectAsState(initial = emptyList())
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Adaptive Layout Logic
    BoxWithConstraints {
        val isWideScreen = maxWidth > 600.dp

        if (isWideScreen) {
            // Tablet/Unfolded Layout
            Row(Modifier.fillMaxSize()) {
                // Left Pane: List (Home)
                // If Home, full width. If Detail/Settings, 50% width.
                val listWeight = if (currentScreen is Screen.Home) 1f else 0.5f
                
                Box(Modifier.weight(listWeight).fillMaxHeight()) {
                    HomeScreen(
                        logs = logs,
                        onAddLog = { file, note, lat, long ->
                            scope.launch {
                                repository.addLog(
                                    LogEntity(
                                        timestamp = System.currentTimeMillis(),
                                        imagePath = file.absolutePath,
                                        note = note,
                                        latitude = lat,
                                        longitude = long
                                    )
                                )
                            }
                        },
                        onNavigateToSettings = { currentScreen = Screen.Settings },
                        onNavigateToDetail = { logId -> currentScreen = Screen.Detail(logId) },
                        isWideScreen = true
                    )
                }
                
                // Right Pane: Detail or Settings
                if (currentScreen !is Screen.Home) {
                    // Vertical Divider
                    Divider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxHeight().width(1.dp)
                    )

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
                                        showBackButton = false,
                                        showCloseButton = true
                                    )
                                }
                            }
                            Screen.Settings -> SettingsScreen(
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = onToggleTheme,
                                onBack = { currentScreen = Screen.Home },
                                onExportLite = { start, end ->
                                    scope.launch {
                                        try {
                                            val file = repository.exportLite(start, end)
                                            shareFile(context, file, "application/json")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onExportFull = { start, end ->
                                    scope.launch {
                                        try {
                                            Toast.makeText(context, "Preparing ZIP...", Toast.LENGTH_SHORT).show()
                                            val file = repository.exportFull(start, end)
                                            shareFile(context, file, "application/zip")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
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
            // Mobile/Folded Layout
            BackHandler(enabled = currentScreen !is Screen.Home) {
                currentScreen = Screen.Home
            }

            when (val screen = currentScreen) {
                Screen.Home -> HomeScreen(
                    logs = logs,
                    onAddLog = { file, note, lat, long ->
                        scope.launch {
                            repository.addLog(
                                LogEntity(
                                    timestamp = System.currentTimeMillis(),
                                    imagePath = file.absolutePath,
                                    note = note,
                                    latitude = lat,
                                    longitude = long
                                )
                            )
                        }
                    },
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    onNavigateToDetail = { logId -> currentScreen = Screen.Detail(logId) },
                    isWideScreen = false
                )
                Screen.Settings -> SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onBack = { currentScreen = Screen.Home },
                    onExportLite = { start, end ->
                        scope.launch {
                            try {
                                val file = repository.exportLite(start, end)
                                shareFile(context, file, "application/json")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onExportFull = { start, end ->
                        scope.launch {
                            try {
                                Toast.makeText(context, "Preparing ZIP...", Toast.LENGTH_SHORT).show()
                                val file = repository.exportFull(start, end)
                                shareFile(context, file, "application/zip")
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    showBackButton = true,
                    showCloseButton = false
                )
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
    onAddLog: (File, String, Double?, Double?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    isWideScreen: Boolean
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    val filteredLogs = logs.filter {
        val logDate = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        logDate == selectedDate
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoFile != null) {
            showNoteDialog = true
        }
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Log")
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
                    selectedDate = newDate
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            LogList(filteredLogs, onLogClick = { onNavigateToDetail(it.id) })
        }

        if (showNoteDialog) {
            NoteDialog(
                onDismiss = { showNoteDialog = false },
                onConfirm = { note ->
                    tempPhotoFile?.let { file ->
                        getLastLocation(context) { lat, long ->
                            onAddLog(file, note, lat, long)
                            showNoteDialog = false
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onExportLite: (Long?, Long?) -> Unit,
    onExportFull: (Long?, Long?) -> Unit,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    // State for Date Range
    var showDateRangePicker by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    // Helpers to convert LocalDate to timestamp (start of day, end of day)
    fun getStartTimestamp(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun getEndTimestamp(date: LocalDate): Long =
        date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    if (showDateRangePicker) {
        val datePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = datePickerState.selectedStartDateMillis
                    val endMillis = datePickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDateRangePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = datePickerState)
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Theme", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
            }
            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Data Export", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            // Date Range Selection
            Text("Export Range", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedButton(
                onClick = { showDateRangePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DateRange, null)
                Spacer(Modifier.width(8.dp))
                val rangeText = if (startDate != null && endDate != null) {
                    "${startDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE)} - ${endDate!!.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                } else {
                    "All Time (Tap to Select)"
                }
                Text(rangeText)
            }

            // Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = {
                    startDate = LocalDate.now().minusDays(30)
                    endDate = LocalDate.now()
                }) { Text("Last 30 Days") }
                
                TextButton(onClick = {
                    startDate = LocalDate.now().minusYears(1)
                    endDate = LocalDate.now()
                }) { Text("Last Year") }
                
                TextButton(onClick = {
                    startDate = null
                    endDate = null
                }) { Text("Clear") }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val start = startDate?.let { getStartTimestamp(it) }
                    val end = endDate?.let { getEndTimestamp(it) }
                    onExportLite(start, end)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export JSON (Lite)")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val start = startDate?.let { getStartTimestamp(it) }
                    val end = endDate?.let { getEndTimestamp(it) }
                    onExportFull(start, end)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export ZIP (Full + Images)")
            }
            Text(
                "Full export includes all original images.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    log: LogEntity,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(log.imagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Full Log Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentScale = ContentScale.FillWidth
            )
            
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = SimpleDateFormat("EEEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(log.timestamp)),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (log.latitude != null && log.longitude != null) {
                    Text(
                        text = "📍 Location: ${String.format("%.4f, %.4f", log.latitude, log.longitude)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Note", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(log.note, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                
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

@Composable
fun LogList(logs: List<LogEntity>, onLogClick: (LogEntity) -> Unit) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No logs for this day.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(logs) { log ->
                LogItem(log, onClick = { onLogClick(log) })
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntity, onClick: () -> Unit) {
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
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (log.note.isNotBlank()) log.note else "No details",
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
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("EEE")),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) Color.White else Color.Black
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun NoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
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