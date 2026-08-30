package com.oxpsi.omnitracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * Map of recipeId -> recipe image path, provided by AppContent so that log
 * entries linked to a recipe (but without their own photo) can render a
 * themed thumbnail derived from the recipe image at runtime.
 */
val LocalRecipeImages = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * Renders a recipe image as a thumbnail with a dynamic colored frame: the
 * image is drawn at 60% of the available size, centered on a background filled
 * with the current theme's primary color. The frame recolors automatically
 * when the user changes the theme color or toggles dark mode.
 *
 * Used for logs created from a recipe without their own photo. These logs have
 * an empty imagePath (so "Save to Gallery" stays hidden, same as a text-only
 * log) but still show a recognizable, themed preview.
 */
@Composable
fun RecipeDerivedThumbnail(
    recipeImagePath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 0.dp
) {
    val shape = if (cornerRadius > 0.dp) RoundedCornerShape(cornerRadius) else RectangleShape
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(recipeImagePath))
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.6f),
            contentScale = contentScale
        )
    }
}
