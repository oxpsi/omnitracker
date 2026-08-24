package com.jonny.healthtrack

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jonny.healthtrack.data.RecipeEntity
import com.jonny.healthtrack.data.RecipeRepository
import com.jonny.healthtrack.util.ShareUtils
import com.jonny.healthtrack.util.normalizeCapturedJpegInPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class RecipeView { List, Editor }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    repository: RecipeRepository,
    openRecipeId: String?,
    onBack: () -> Unit,
    onCreateLogFromRecipe: (recipeId: String, imagePath: String, note: String) -> Unit,
    showBackButton: Boolean,
    showCloseButton: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recipes by repository.allRecipes.collectAsState(initial = emptyList())

    var view by remember { mutableStateOf(RecipeView.List) }
    var editingRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var pendingImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewLogEntrySheet by remember { mutableStateOf<RecipeEntity?>(null) }
    var pendingNewLogRecipe by remember { mutableStateOf<RecipeEntity?>(null) }
    var pendingNewLogImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var tempCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewLogNoteDialog by remember { mutableStateOf(false) }

    // Auto-open a recipe when navigated with an openRecipeId (e.g. from a log entry)
    LaunchedEffect(openRecipeId) {
        val id = openRecipeId ?: return@LaunchedEffect
        val recipe = repository.getRecipeById(id) ?: return@LaunchedEffect
        editingRecipe = recipe
        pendingImagePath = recipe.imagePath.takeIf { it.isNotEmpty() }
        view = RecipeView.Editor
    }

    fun resetEditor() {
        editingRecipe = null
        pendingImagePath = null
        view = RecipeView.List
    }

    fun clearPendingNewLog() {
        val path = pendingNewLogImagePath
        if (path != null && path.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try { File(path).delete() } catch (_: Exception) {}
            }
        }
        pendingNewLogImagePath = null
        pendingNewLogRecipe = null
        showNewLogNoteDialog = false
    }

    // --- Launchers for creating a log from a recipe ---

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val tempPath = tempCameraPath
        if (success && !tempPath.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                val capturedFile = File(tempPath)
                val normalized = normalizeCapturedJpegInPlace(capturedFile)
                withContext(Dispatchers.Main) {
                    pendingNewLogImagePath = normalized.absolutePath
                    showNewLogNoteDialog = true
                }
            }
        }
        tempCameraPath = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            val photoFile = createImageFile(context)
            tempCameraPath = photoFile.absolutePath
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
                    withContext(Dispatchers.Main) {
                        pendingNewLogImagePath = destFile.absolutePath
                        showNewLogNoteDialog = true
                    }
                } else {
                    try { if (destFile.exists()) destFile.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    when (view) {
        RecipeView.List -> RecipesListContent(
            recipes = recipes,
            onBack = onBack,
            showBackButton = showBackButton,
            showCloseButton = showCloseButton,
            onCreate = {
                editingRecipe = null
                pendingImagePath = null
                view = RecipeView.Editor
            },
            onOpen = { recipe ->
                editingRecipe = recipe
                pendingImagePath = recipe.imagePath.takeIf { it.isNotEmpty() }
                view = RecipeView.Editor
            },
            onQuickLog = { recipe ->
                showNewLogEntrySheet = recipe
            }
        )

        RecipeView.Editor -> RecipeEditorContent(
            initial = editingRecipe,
            pendingImagePath = pendingImagePath,
            onImagePathChange = { pendingImagePath = it },
            onSave = { title, content, imagePath ->
                scope.launch {
                    val existing = editingRecipe
                    if (existing == null) {
                        repository.addRecipe(
                            RecipeEntity(
                                title = title,
                                description = content,
                                ingredients = "",
                                imagePath = imagePath
                            )
                        )
                    } else {
                        // Drop the old image file if it was replaced/removed.
                        val oldPath = existing.imagePath
                        if (oldPath.isNotEmpty() && oldPath != imagePath) {
                            withContext(Dispatchers.IO) {
                                try { File(oldPath).delete() } catch (_: Exception) {}
                            }
                        }
                        repository.updateRecipe(
                            existing.copy(
                                title = title,
                                description = content,
                                ingredients = "",
                                imagePath = imagePath
                            )
                        )
                    }
                }
                resetEditor()
            },
            onCancel = {
                // Discard any newly picked (unsaved) image
                val newPath = pendingImagePath
                val existing = editingRecipe
                if (newPath != null && newPath.isNotEmpty() && newPath != existing?.imagePath) {
                    scope.launch(Dispatchers.IO) {
                        try { File(newPath).delete() } catch (_: Exception) {}
                    }
                }
                resetEditor()
            },
            onDelete = {
                editingRecipe?.let { recipe ->
                    scope.launch {
                        repository.deleteRecipe(recipe)
                        Toast.makeText(context, "Recipe deleted", Toast.LENGTH_SHORT).show()
                    }
                }
                resetEditor()
            },
            onNewLog = {
                editingRecipe?.let { showNewLogEntrySheet = it }
            }
        )
    }

    // Entry-method selection bottom sheet for creating a log from a recipe
    showNewLogEntrySheet?.let { recipe ->
        RecipeNewLogEntrySheet(
            recipe = recipe,
            onDismiss = { showNewLogEntrySheet = null },
            onCapture = {
                showNewLogEntrySheet = null
                pendingNewLogRecipe = recipe
                cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            },
            onUpload = {
                showNewLogEntrySheet = null
                pendingNewLogRecipe = recipe
                uploadPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onNoteOnly = {
                showNewLogEntrySheet = null
                pendingNewLogRecipe = recipe
                showNewLogNoteDialog = true
            }
        )
    }

    // Note dialog after choosing an entry method (camera, upload, or note-only)
    if (showNewLogNoteDialog) {
        val recipe = pendingNewLogRecipe
        if (recipe != null) {
            NewLogFromRecipeDialog(
                recipe = recipe,
                pendingImagePath = pendingNewLogImagePath,
                onDismiss = { clearPendingNewLog() },
                onConfirm = { note ->
                    val imagePath = pendingNewLogImagePath ?: ""
                    onCreateLogFromRecipe(recipe.id, imagePath, note)
                    pendingNewLogImagePath = null
                    pendingNewLogRecipe = null
                    showNewLogNoteDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipesListContent(
    recipes: List<RecipeEntity>,
    onBack: () -> Unit,
    showBackButton: Boolean,
    showCloseButton: Boolean,
    onCreate: () -> Unit,
    onOpen: (RecipeEntity) -> Unit,
    onQuickLog: (RecipeEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipes") },
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, "New Recipe")
            }
        }
    ) { padding ->
        if (recipes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No recipes yet.", color = Color.Gray)
                    Text("Tap + to create one.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recipes) { recipe ->
                    RecipeCard(
                        recipe = recipe,
                        onClick = { onOpen(recipe) },
                        onQuickLog = { onQuickLog(recipe) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: RecipeEntity, onClick: () -> Unit, onQuickLog: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recipe.imagePath.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(recipe.imagePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .background(Color.Gray),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = recipe.title.ifBlank { "Untitled recipe" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (recipe.description.isNotBlank()) {
                    Text(
                        text = recipe.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onQuickLog,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Log", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeEditorContent(
    initial: RecipeEntity?,
    pendingImagePath: String?,
    onImagePathChange: (String?) -> Unit,
    onSave: (title: String, content: String, imagePath: String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onNewLog: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var content by remember { mutableStateOf(initial?.description ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Drop the previous pending pick if it's not the saved one.
            val current = pendingImagePath
            if (current != null && current.isNotEmpty() && current != initial?.imagePath) {
                try { File(current).delete() } catch (_: Exception) {}
            }
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
                onImagePathChange(destFile.absolutePath)
            } else {
                try { if (destFile.exists()) destFile.delete() } catch (_: Exception) {}
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recipe") },
            text = { Text("Are you sure you want to delete this recipe? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
                },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New Recipe" else "Edit Recipe") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel")
                    }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    // Share recipe text
                    IconButton(onClick = {
                        ShareUtils.shareRecipe(context, title, content)
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image picker preview
            val imgPath = pendingImagePath
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (imgPath != null && imgPath.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(imgPath))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Recipe image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Change", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Spacer(Modifier.height(4.dp))
                        Text("Add photo (optional)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8
            )

            Button(
                onClick = {
                    val path = pendingImagePath ?: ""
                    onSave(title.trim(), content.trim(), path)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (initial == null) "Create Recipe" else "Save Changes")
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }

            if (initial != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onNewLog,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Restaurant, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Log")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeNewLogEntrySheet(
    recipe: RecipeEntity,
    onDismiss: () -> Unit,
    onCapture: () -> Unit,
    onUpload: () -> Unit,
    onNoteOnly: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "New Log from \"${recipe.title.ifBlank { "Recipe" }}\"",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "The recipe will be attached as batch context for AI analysis.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(20.dp))

            EntryOptionRow(
                icon = Icons.Default.Add,
                label = "Capture Photo",
                description = "Take a new photo (with optional note after)",
                onClick = onCapture
            )
            Spacer(Modifier.height(8.dp))
            EntryOptionRow(
                icon = Icons.Default.Upload,
                label = "Upload Photo",
                description = "Choose from gallery (with optional note after)",
                onClick = onUpload
            )
            Spacer(Modifier.height(8.dp))
            EntryOptionRow(
                icon = Icons.Default.Edit,
                label = "Note Only",
                description = "Log entry with text only, no photo",
                onClick = onNoteOnly
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EntryOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun NewLogFromRecipeDialog(
    recipe: RecipeEntity,
    pendingImagePath: String?,
    onDismiss: () -> Unit,
    onConfirm: (note: String) -> Unit
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Log from \"${recipe.title.ifBlank { "Recipe" }}\"") },
        text = {
            Column {
                if (pendingImagePath != null && pendingImagePath.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(pendingImagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Captured photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Add an optional note. The photo and recipe details will be attached for AI analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        "Add an optional note. The recipe details will be attached for AI analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(note.trim()) }) {
                Text("Create Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
