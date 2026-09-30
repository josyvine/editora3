package com.vineyard.aivideostudio.media.transformer.overlays

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import com.vineyard.aivideostudio.core.model.effects.OverlayType
import com.vineyard.aivideostudio.core.model.effects.ReplacementOverlaySpec
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingKeyframe
import com.vineyard.aivideostudio.core.model.effects.TrackingStyle
import java.io.File
import kotlin.math.sin

/**
 * High-performance Media3 BitmapOverlay engine for:
 * 1. 1:1 Brand / Watermark Cover & Emoji/Logo Replacement.
 * 2. Sports Tracking Bounding Boxes (Keyframe Interpolated).
 * 3. Animated Flashing Pointing Arrows & Spotlight Highlights.
 */
@OptIn(UnstableApi::class)
class DynamicGraphicsOverlay(
    private val replacements: List<ReplacementOverlaySpec> = emptyList(),
    private val trackingIndicators: List<TrackingIndicatorSpec> = emptyList(),
    private val targetWidth: Int = 1080,
    private val targetHeight: Int = 1920
) : BitmapOverlay() {

    // Cached paints to prevent allocations during 60fps frame rendering
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val arrowPath = Path()
    private val textBounds = Rect()

    // Pre-allocated reusable canvas buffer
    private val frameBitmap: Bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    private val canvas: Canvas = Canvas(frameBitmap)

    // Decoded bitmap cache for logo / image replacements
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val currentTimeMs = presentationTimeUs / 1000L
        frameBitmap.eraseColor(Color.TRANSPARENT)

        val width = targetWidth.toFloat()
        val height = targetHeight.toFloat()

        // 1. Render 1:1 Brand / Watermark Replacements & Emojis
        renderReplacements(canvas, currentTimeMs, width, height)

        // 2. Render Sports Tracking Boxes & Flashing Arrows
        renderTrackingIndicators(canvas, currentTimeMs, width, height)

        return frameBitmap
    }

    private fun renderReplacements(
        canvas: Canvas,
        currentTimeMs: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        val activeReplacements = replacements.filter {
            currentTimeMs in it.startTimeMs..it.endTimeMs
        }

        for (spec in activeReplacements) {
            val left = spec.bounds.left * canvasWidth
            val top = spec.bounds.top * canvasHeight
            val right = spec.bounds.right * canvasWidth
            val bottom = spec.bounds.bottom * canvasHeight
            val rect = RectF(left, top, right, bottom)

            canvas.save()
            if (spec.rotationDegrees != 0f) {
                canvas.rotate(spec.rotationDegrees, rect.centerX(), rect.centerY())
            }

            when (spec.type) {
                OverlayType.SOLID_BADGE -> {
                    fillPaint.color = try {
                        Color.parseColor(spec.contentValue)
                    } catch (e: Exception) {
                        Color.BLACK
                    }
                    fillPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
                    val cornerRadius = (rect.height() * 0.2f).coerceAtMost(16f)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
                }

                OverlayType.EMOJI -> {
                    // Draw opaque concealment background pill first
                    fillPaint.color = Color.BLACK
                    fillPaint.alpha = 230
                    val cornerRadius = (rect.height() * 0.25f).coerceAtMost(20f)
                    canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

                    // Draw Emoji centered inside bounds
                    textPaint.textSize = rect.height() * 0.75f
                    textPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
                    textPaint.getTextBounds(spec.contentValue, 0, spec.contentValue.length, textBounds)
                    val textY = rect.centerY() + (textBounds.height() / 2f) - textBounds.bottom
                    canvas.drawText(spec.contentValue, rect.centerX(), textY, textPaint)
                }

                OverlayType.BRAND_LOGO, OverlayType.STICKER -> {
                    val bitmap = getOrLoadBitmap(spec.contentValue)
                    if (bitmap != null) {
                        fillPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(bitmap, null, rect, fillPaint)
                    } else {
                        // Fallback placeholder badge
                        fillPaint.color = Color.DKGRAY
                        canvas.drawRoundRect(rect, 8f, 8f, fillPaint)
                    }
                }
            }

            canvas.restore()
        }
    }

    private fun renderTrackingIndicators(
        canvas: Canvas,
        currentTimeMs: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        for (indicator in trackingIndicators) {
            val currentFrame = interpolateKeyframe(indicator.keyframes, currentTimeMs) ?: continue

            val centerX = currentFrame.x * canvasWidth
            val centerY = currentFrame.y * canvasHeight
            val boxWidth = currentFrame.width * canvasWidth
            val boxHeight = currentFrame.height * canvasHeight

            val rect = RectF(
                centerX - (boxWidth / 2f),
                centerY - (boxHeight / 2f),
                centerX + (boxWidth / 2f),
                centerY + (boxHeight / 2f)
            )

            val baseColor = try {
                Color.parseColor(indicator.colorHex)
            } catch (e: Exception) {
                Color.RED
            }

            when (indicator.style) {
                TrackingStyle.RED_BOX -> {
                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = indicator.strokeWidthPx
                    canvas.drawRoundRect(rect, 12f, 12f, strokePaint)

                    indicator.label?.let { label ->
                        drawLabelBadge(canvas, label, rect.centerX(), rect.top - 10f)
                    }
                }

                TrackingStyle.HIGHLIGHT_CIRCLE -> {
                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = indicator.strokeWidthPx
                    val radius = (boxWidth.coerceAtLeast(boxHeight) / 2f)
                    canvas.drawCircle(centerX, centerY, radius, strokePaint)
                }

                TrackingStyle.FLASHING_ARROW -> {
                    val pulse = (sin(currentTimeMs * 0.012) * 0.5 + 0.5).toFloat()
                    val bounceOffset = pulse * 18f

                    fillPaint.color = baseColor
                    fillPaint.alpha = (160 + (pulse * 95)).toInt().coerceIn(0, 255)

                    val tipX = centerX
                    val tipY = rect.top - 12f + bounceOffset
                    val arrowWidth = 36f
                    val arrowHeight = 44f

                    arrowPath.reset()
                    arrowPath.moveTo(tipX, tipY)
                    arrowPath.lineTo(tipX - (arrowWidth / 2f), tipY - arrowHeight)
                    arrowPath.lineTo(tipX + (arrowWidth / 2f), tipY - arrowHeight)
                    arrowPath.close()

                    canvas.drawPath(arrowPath, fillPaint)
                }

                TrackingStyle.SPOTLIGHT -> {
                    fillPaint.color = baseColor
                    fillPaint.alpha = 40
                    canvas.drawOval(rect, fillPaint)
                }
            }
        }
    }

    private fun drawLabelBadge(canvas: Canvas, label: String, centerX: Float, bottomY: Float) {
        textPaint.textSize = 28f
        textPaint.color = Color.WHITE
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        val paddingHorizontal = 16f
        val paddingVertical = 8f
        val badgeRect = RectF(
            centerX - (textBounds.width() / 2f) - paddingHorizontal,
            bottomY - textBounds.height() - (paddingVertical * 2),
            centerX + (textBounds.width() / 2f) + paddingHorizontal,
            bottomY
        )

        fillPaint.color = Color.parseColor("#CC000000")
        canvas.drawRoundRect(badgeRect, 8f, 8f, fillPaint)

        val textY = badgeRect.centerY() + (textBounds.height() / 2f) - textBounds.bottom
        canvas.drawText(label, centerX, textY, textPaint)
    }

    private fun interpolateKeyframe(keyframes: List<TrackingKeyframe>, currentTimeMs: Long): TrackingKeyframe? {
        if (keyframes.isEmpty()) return null
        if (currentTimeMs < keyframes.first().timeMs || currentTimeMs > keyframes.last().timeMs) {
            return null
        }

        val nextIndex = keyframes.indexOfFirst { it.timeMs >= currentTimeMs }
        if (nextIndex <= 0) return keyframes.first()

        val prev = keyframes[nextIndex - 1]
        val next = keyframes[nextIndex]

        val totalDuration = (next.timeMs - prev.timeMs).toFloat()
        if (totalDuration <= 0f) return prev

        val progress = ((currentTimeMs - prev.timeMs) / totalDuration).coerceIn(0f, 1f)

        return TrackingKeyframe(
            timeMs = currentTimeMs,
            x = prev.x + (next.x - prev.x) * progress,
            y = prev.y + (next.y - prev.y) * progress,
            width = prev.width + (next.width - prev.width) * progress,
            height = prev.height + (next.height - prev.height) * progress
        )
    }

    private fun getOrLoadBitmap(pathOrBase64: String): Bitmap? {
        if (bitmapCache.containsKey(pathOrBase64)) {
            return bitmapCache[pathOrBase64]
        }

        val bitmap = try {
            if (pathOrBase64.startsWith("/") || pathOrBase64.startsWith("file://")) {
                val cleanPath = pathOrBase64.removePrefix("file://")
                val file = File(cleanPath)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            } else {
                val decodedBytes = Base64.decode(pathOrBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) {
            bitmapCache[pathOrBase64] = bitmap
        }
        return bitmap
    }
}