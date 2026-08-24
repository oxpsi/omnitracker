package com.jonny.healthtrack.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Creates a derived "log entry" image from a recipe's batch image: the source
 * is center-cropped to a square, scaled to 90%, and drawn centered on a square
 * canvas filled with the theme primary color, producing a colored frame around
 * the image on all sides.
 *
 * The output is square so that UI tiles using ContentScale.Crop (which crop a
 * portrait image's top/bottom to fill a square) don't clip the colored frame.
 *
 * Used when a log is created from a recipe without a new photo, so the log
 * entry still has a recognizable (themed) thumbnail derived from the recipe.
 *
 * @param source the recipe image file
 * @param outFile destination file (JPEG)
 * @param outlineColorArgb fill/border color (theme primary), ARGB int
 * @param scale fraction of the inner square the source image is fit to
 * @param jpegQuality JPEG compression quality (1-100)
 * @return the written [outFile], or null if the source could not be decoded
 */
fun createThemedRecipeThumbnail(
    source: File,
    outFile: File,
    outlineColorArgb: Int,
    scale: Float = 0.6f,
    jpegQuality: Int = 85
): File? {
    if (!source.exists() || source.length() == 0L) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.absolutePath, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    // Output is a square sized to the source's longer edge, so a square UI tile
    // using ContentScale.Crop has nothing to crop and the colored frame shows
    // on all four sides.
    val side = max(srcW, srcH)
    val innerSide = max(1, (side * scale).roundToInt())

    // Bound decode memory for very large sources.
    val maxTargetDecode = innerSide * 2
    var inSampleSize = 1
    while (max(srcW, srcH) / inSampleSize > maxTargetDecode) {
        inSampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        this.inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(source.absolutePath, decodeOptions) ?: return null

    // Square canvas filled with the theme color; inner image (center-cropped
    // to a square) is scaled to innerSide and centered on top, leaving a colored
    // frame on all four sides.
    val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val fillPaint = Paint().apply {
        color = outlineColorArgb
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawRect(0f, 0f, side.toFloat(), side.toFloat(), fillPaint)

    // Center-crop the decoded image to a square of its shorter edge, then
    // scale that square to innerSide x innerSide.
    val cropSide = min(decoded.width, decoded.height)
    val cropX = (decoded.width - cropSide) / 2
    val cropY = (decoded.height - cropSide) / 2
    val cropped = Bitmap.createBitmap(decoded, cropX, cropY, cropSide, cropSide)
    if (cropped !== decoded && !decoded.isRecycled) decoded.recycle()

    val inner = if (cropped.width != innerSide || cropped.height != innerSide) {
        Bitmap.createScaledBitmap(cropped, innerSide, innerSide, true).also {
            if (it !== cropped) cropped.recycle()
        }
    } else {
        cropped
    }

    val offset = ((side - innerSide) / 2f).roundToInt()
    val dst = Rect(offset, offset, offset + innerSide, offset + innerSide)
    val srcRect = Rect(0, 0, inner.width, inner.height)
    canvas.drawBitmap(inner, srcRect, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

    if (!inner.isRecycled) inner.recycle()

    return try {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            result.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        }
        outFile
    } catch (_: Exception) {
        null
    } finally {
        if (!result.isRecycled) result.recycle()
    }
}
