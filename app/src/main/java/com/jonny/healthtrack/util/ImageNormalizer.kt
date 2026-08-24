package com.jonny.healthtrack.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

fun normalizeCapturedJpegInPlace(
    file: File,
    maxDimensionPx: Int = 1600,
    jpegQuality: Int = 85
): File {
    if (!file.exists() || file.length() == 0L) return file

    val orientation = try {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)

    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return file

    val alreadySmall = max(srcW, srcH) <= maxDimensionPx
    val orientationNormal = orientation == ExifInterface.ORIENTATION_NORMAL
    if (alreadySmall && orientationNormal) return file

    val maxTargetDecode = maxDimensionPx * 2
    var inSampleSize = 1
    while (max(srcW, srcH) / inSampleSize > maxTargetDecode) {
        inSampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        this.inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return file

    val rotated = try {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }
    } catch (_: Exception) {
        decoded
    }

    if (rotated !== decoded) {
        decoded.recycle()
    }

    val longestEdge = max(rotated.width, rotated.height)
    val scaled = if (longestEdge > maxDimensionPx) {
        val scale = maxDimensionPx.toFloat() / longestEdge.toFloat()
        val targetW = (rotated.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (rotated.height * scale).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
    } else {
        rotated
    }

    if (scaled !== rotated) {
        rotated.recycle()
    }

    val tmp = File.createTempFile(file.nameWithoutExtension + "_norm_", ".jpg", file.parentFile)
    try {
        FileOutputStream(tmp).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
        }
        if (!file.delete()) {
            FileOutputStream(file, false).use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            }
            tmp.delete()
        } else {
            tmp.renameTo(file)
        }
    } catch (_: Exception) {
        // Best effort; keep original if anything goes wrong.
    } finally {
        if (!scaled.isRecycled) scaled.recycle()
    }

    return file
}

/**
 * Encodes an image file for upload to an AI vision endpoint as JPEG bytes.
 *
 * This is a defensive sanity cap: if the source image is larger than
 * [maxDimensionPx] on its longest edge, it is downscaled (with bounded decode
 * memory via inSampleSize) and re-encoded to JPEG. The result is always JPEG
 * so callers can assume a fixed MIME type. Returns null if the file cannot be
 * decoded or read.
 *
 * Stored images are normally already normalized at capture time, so for most
 * inputs this only triggers when a high-resolution import slipped through
 * normalization, guaranteeing no oversized payload is sent to the API. Tune
 * [maxDimensionPx] / [jpegQuality] down later if token/size limits are hit.
 */
fun encodeImageForAiUpload(
    file: File,
    maxDimensionPx: Int = 1280,
    jpegQuality: Int = 80
): ByteArray? {
    if (!file.exists() || file.length() == 0L) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    val longestSrc = max(srcW, srcH)
    if (longestSrc <= maxDimensionPx) {
        // Already within the cap. Re-encode to JPEG so the caller can assume a
        // fixed MIME type and a predictable payload size.
        val decodeOptions = BitmapFactory.Options().apply {
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        return try {
            compressToJpegBytes(decoded, jpegQuality)
        } catch (_: Exception) {
            null
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    // Pre-scale decode to bound memory for very large sources.
    val maxTargetDecode = maxDimensionPx * 2
    var inSampleSize = 1
    while (longestSrc / inSampleSize > maxTargetDecode) {
        inSampleSize *= 2
    }
    val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        this.inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

    val longestEdge = max(decoded.width, decoded.height)
    val scaled = if (longestEdge > maxDimensionPx) {
        val scale = maxDimensionPx.toFloat() / longestEdge.toFloat()
        val targetW = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } else {
        decoded
    }

    return try {
        compressToJpegBytes(scaled, jpegQuality)
    } catch (_: Exception) {
        null
    } finally {
        if (!scaled.isRecycled) scaled.recycle()
    }
}

private fun compressToJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return baos.toByteArray()
}

