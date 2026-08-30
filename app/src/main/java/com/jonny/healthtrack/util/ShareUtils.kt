package com.jonny.healthtrack.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object ShareUtils {

    fun shareDaySummary(context: Context, date: LocalDate, components: List<AggregatedComponent>) {
        val filtered = components.filter { it.keyName in shareableNutrients }
        val bitmap = generateSummaryBitmap(context, date, filtered)
        val file = saveBitmapToCache(context, bitmap)
        shareImage(context, file)
    }

    /**
     * Only nutrients mentioned in the analysis prompt baseline are shown in the
     * shareable image — keeps output focused and reduces noise (no "net weight", etc.).
     */
    private val shareableNutrients: Set<String> = setOf(
        "energy", "protein", "carbohydrate", "total fat", "saturated fat", "trans fat",
        "dietary fiber", "soluble fiber", "insoluble fiber", "sugar", "added sugars",
        "sodium", "potassium", "calcium", "iron", "magnesium", "phosphorus", "zinc",
        "selenium", "choline",
        "vitamin a", "vitamin c", "vitamin d", "vitamin e", "vitamin k",
        "vitamin b1", "vitamin b2", "vitamin b3", "vitamin b6", "folate", "vitamin b12",
        "omega-3", "omega-6"
    )

    private data class SummaryPalette(
        val background: Int,
        val surface: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val accent: Int,
        val muted: Int,
        val divider: Int
    )

    private fun paletteFor(context: Context): SummaryPalette {
        val dark = ThemePreferences.isDarkTheme(context)
        val color = ThemePreferences.getThemeColor(context)
        val accent = when (color) {
            AppThemeColor.Green -> if (dark) 0xFF8BC34A.toInt() else 0xFF4CAF50.toInt()
            AppThemeColor.Blue -> if (dark) 0xFF64B5F6.toInt() else 0xFF2196F3.toInt()
            AppThemeColor.Red -> if (dark) 0xFFE57373.toInt() else 0xFFF44336.toInt()
            AppThemeColor.Purple -> if (dark) 0xFFBA68C8.toInt() else 0xFF9C27B0.toInt()
            AppThemeColor.Orange -> if (dark) 0xFFFFB74D.toInt() else 0xFFFF9800.toInt()
            AppThemeColor.Teal -> if (dark) 0xFF4DB6AC.toInt() else 0xFF009688.toInt()
        }
        return if (dark) {
            SummaryPalette(
                background = 0xFF101012.toInt(),
                surface = 0xFF1A1A1E.toInt(),
                primaryText = 0xFFE6E6E6.toInt(),
                secondaryText = 0xFF9E9E9E.toInt(),
                accent = accent,
                muted = 0xFF6E6E6E.toInt(),
                divider = 0xFF3A3A3E.toInt()
            )
        } else {
            SummaryPalette(
                background = 0xFFF2F2F0.toInt(),
                surface = 0xFFFFFFFF.toInt(),
                primaryText = 0xFF1A1A1A.toInt(),
                secondaryText = 0xFF616161.toInt(),
                accent = accent,
                muted = 0xFF9E9E9E.toInt(),
                divider = 0xFFD6D6D2.toInt()
            )
        }
    }

    private fun formatQty(unit: String?): String {
        // Image-only exception: render micrograms with the μ glyph for nicer output.
        val u = unit?.trim()?.takeIf { it.isNotBlank() } ?: return ""
        return u.replace("mcg", "μg")
    }

    private fun generateSummaryBitmap(context: Context, date: LocalDate, components: List<AggregatedComponent>): Bitmap {
        val palette = paletteFor(context)

        // Config
        val width = 1080
        val padding = 60f
        val headerHeight = 250f
        val rowHeight = 110f
        val footerHeight = 100f
        val cols = 2
        val rows = (components.size + cols - 1) / cols
        val colSpacing = 80f
        val colWidth = (width - padding * 2 - colSpacing * (cols - 1)) / cols

        // Calculate total height
        val totalHeight = (headerHeight + (rows * rowHeight) + footerHeight).toInt()

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paints
        val bgPaint = Paint().apply { color = palette.background }
        val surfacePaint = Paint().apply { color = palette.surface }
        val titlePaint = TextPaint().apply {
            color = palette.primaryText
            textSize = 80f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val datePaint = TextPaint().apply {
            color = palette.secondaryText
            textSize = 48f
            isAntiAlias = true
        }
        val itemPaint = TextPaint().apply {
            color = palette.primaryText
            textSize = 34f
            isAntiAlias = true
        }
        val quantityPaint = TextPaint().apply {
            color = palette.accent
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val footerPaint = TextPaint().apply {
            color = palette.muted
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = palette.divider
            strokeWidth = 2f
        }

        // Draw Background
        canvas.drawRect(0f, 0f, width.toFloat(), totalHeight.toFloat(), bgPaint)
        // Header surface strip
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight - 20f, surfacePaint)

        // Draw Header
        val dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
        canvas.drawText("Daily Summary", padding, 120f, titlePaint)
        canvas.drawText(dateStr, padding, 200f, datePaint)

        // Draw Separator
        canvas.drawLine(padding, headerHeight - 20, width - padding, headerHeight - 20, linePaint)

        // Draw Items — column-major (fill first column top-to-bottom, then second column)
        // Each cell: name left, value right.
        val perCol = (components.size + cols - 1) / cols

        for (colIdx in 0 until cols) {
            val cellLeft = padding + colIdx * (colWidth + colSpacing)
            val cellRight = cellLeft + colWidth
            var currentY = headerHeight + 60

            for (rowIdx in 0 until perCol) {
                val itemIdx = colIdx * perCol + rowIdx
                if (itemIdx >= components.size) break
                val component = components[itemIdx]
                val emoji = getEmojiForComponent(component.keyName)
                val name = component.displayName
                val qtyString = String.format(Locale.US, "%.1f %s", component.quantity, formatQty(component.unit)).trim()

                // Draw Emoji + Name (left-aligned within cell)
                canvas.drawText("$emoji  $name", cellLeft, currentY, itemPaint)

                // Draw Quantity (right-aligned within cell)
                canvas.drawText(qtyString, cellRight, currentY, quantityPaint)

                currentY += rowHeight
            }
        }

        // Footer
        val footerY = totalHeight - 40f
        canvas.drawText("Generated by OmniTracker", width / 2f, footerY, footerPaint)

        return bitmap
    }

    private fun getEmojiForComponent(key: String): String {
        return when (key.lowercase()) {
            "energy" -> "⚡"
            "protein" -> "🐟"
            "carbohydrate" -> "🥐"
            "total fat" -> "🧈"
            "saturated fat" -> "🍔"
            "trans fat" -> "🍟"
            "dietary fiber" -> "🌿"
            "soluble fiber" -> "🥣"
            "insoluble fiber" -> "🫘"
            "sugar" -> "🍦"
            "added sugars" -> "🍭"
            "sodium" -> "🧂"
            "potassium" -> "🍌"
            "calcium" -> "🦴"
            "iron" -> "🩸"
            "magnesium" -> "🌰"
            "phosphorus" -> "🔥"
            "zinc" -> "⚙️"
            "selenium" -> "🧪"
            "choline" -> "🥚"
            "cholesterol" -> "💓"
            "caffeine" -> "☕"
            "vitamin a" -> "🥕"
            "vitamin c" -> "🍊"
            "vitamin d" -> "☀️"
            "vitamin e" -> "🌻"
            "vitamin k" -> "🥬"
            "vitamin b1" -> "🌾"
            "vitamin b2" -> "🥛"
            "vitamin b3" -> "🍄"
            "vitamin b6" -> "🥔"
            "folate" -> "🥦"
            "vitamin b12" -> "🦪"
            "omega-3" -> "🐟"
            "omega-6" -> "🌽"
            "net weight" -> "⚖️"
            else -> "🔹"
        }
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap): File {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = FileOutputStream("$cachePath/daily_summary.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        return File(cachePath, "daily_summary.png")
    }

    fun shareRecipe(context: Context, title: String, content: String) {
        val text = buildString {
            appendLine(title.ifBlank { "Recipe" })
            appendLine()
            if (content.isNotBlank()) {
                appendLine(content)
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title.ifBlank { "Recipe" })
        }
        context.startActivity(Intent.createChooser(intent, "Share Recipe"))
    }

    private fun shareImage(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Daily Summary"))
    }
}
