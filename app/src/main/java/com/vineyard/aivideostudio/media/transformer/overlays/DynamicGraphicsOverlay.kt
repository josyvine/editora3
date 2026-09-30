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
import com.vineyard.aivideostudio.core.model.effects.ArrowDirection
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
 * 2. Multi-Directional Pointing Arrows (UP, DOWN, LEFT, RIGHT).
 * 3. UI Button Callouts (Corner Brackets & Pulsating Highlights).
 * 4. Sports Motion Tracking & Full-Height Person Column Pillars.
 * 5. Spotlight Background Dimming.
 */
@OptIn(UnstableApi::class)
class DynamicGraphicsOverlay(
    private val replacements: List<ReplacementOverlaySpec> = emptyList(),
    private val trackingIndicators: List<TrackingIndicatorSpec> = emptyList(),
    private val targetWidth: Int = 1080,
    private val targetHeight: Int = 1920
) : BitmapOverlay() {

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

        // 2. Render Pointing Arrows, Button Highlights, Tracking Pillars & Spotlights
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
                    // Opaque concealment background pill
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
            // Check active time window
            val isWithinWindow = currentTimeMs in indicator.startTimeMs..indicator.endTimeMs
            if (!isWithinWindow) continue

            // Determine target bounding rectangle (Motion Keyframe OR Static UI Bounds)
            val rect: RectF = if (indicator.staticBounds != null) {
                RectF(
                    indicator.staticBounds.left * canvasWidth,
                    indicator.staticBounds.top * canvasHeight,
                    indicator.staticBounds.right * canvasWidth,
                    indicator.staticBounds.bottom * canvasHeight
                )
            } else {
                val currentFrame = interpolateKeyframe(indicator.keyframes, currentTimeMs) ?: continue
                val cx = currentFrame.x * canvasWidth
                val cy = currentFrame.y * canvasHeight
                val bw = currentFrame.width * canvasWidth
                val bh = currentFrame.height * canvasHeight
                RectF(cx - (bw / 2f), cy - (bh / 2f), cx + (bw / 2f), cy + (bh / 2f))
            }

            // 1. Spotlight Dimming (Darkens background around target element)
            if (indicator.dimBackgroundOpacity > 0f) {
                renderBackgroundDimming(canvas, rect, canvasWidth, canvasHeight, indicator.dimBackgroundOpacity)
            }

            val baseColor = try {
                Color.parseColor(indicator.colorHex)
            } catch (e: Exception) {
                Color.RED
            }

            when (indicator.style) {
                TrackingStyle.BUTTON_HIGHLIGHT -> {
                    // Pulsating corner brackets for stationary UI buttons (e.g. "Copy" icon)
                    val pulse = (sin(currentTimeMs * 0.010) * 0.5 + 0.5).toFloat()
                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = indicator.strokeWidthPx + (pulse * 2f)
                    strokePaint.alpha = (180 + (pulse * 75)).toInt().coerceIn(0, 255)

                    val pad = 8f + (pulse * 4f)
                    val bracketRect = RectF(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
                    drawCornerBrackets(canvas, bracketRect, strokePaint)

                    indicator.label?.let { label ->
                        drawLabelBadge(canvas, label, bracketRect.centerX(), bracketRect.top - 8f)
                    }
                }

                TrackingStyle.VERTICAL_COLUMN -> {
                    // Full-height person / athlete framing pillar
                    strokePaint.color = baseColor
                    strokePaint.strokeWidth = indicator.strokeWidthPx
                    val pillarRect = RectF(rect.left, 40f, rect.right, canvasHeight - 40f)
                    canvas.drawRoundRect(pillarRect, 24f, 24f, strokePaint)

                    indicator.label?.let { label ->
                        drawLabelBadge(canvas, label, pillarRect.centerX(), pillarRect.top + 60f)
                    }
                }

                TrackingStyle.FLASHING_ARROW -> {
                    // Multi-Directional Pointing Arrow (UP, DOWN, LEFT, RIGHT)
                    renderDirectionalArrow(canvas, rect, indicator.arrowDirection, baseColor, currentTimeMs)
                }

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
                    val radius = (rect.width().coerceAtLeast(rect.height()) / 2f) + 6f
                    canvas.drawCircle(rect.centerX(), rect.centerY(), radius, strokePaint)
                }

                TrackingStyle.SPOTLIGHT -> {
                    fillPaint.color = baseColor
                    fillPaint.alpha = 50
                    canvas.drawOval(rect, fillPaint)
                }
            }
        }
    }

    private fun renderDirectionalArrow(
        canvas: Canvas,
        targetRect: RectF,
        direction: ArrowDirection,
        color: Int,
        currentTimeMs: Long
    ) {
        val pulse = (sin(currentTimeMs * 0.012) * 0.5 + 0.5).toFloat()
        val bounceOffset = pulse * 18f

        fillPaint.color = color
        fillPaint.alpha = (170 + (pulse * 85)).toInt().coerceIn(0, 255)

        val arrowWidth = 36f
        val arrowLength = 48f
        arrowPath.reset()

        when (direction) {
            ArrowDirection.DOWN -> {
                // Points down at top edge of target
                val tipX = targetRect.centerX()
                val tipY = targetRect.top - 12f + bounceOffset
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX - (arrowWidth / 2f), tipY - arrowLength)
                arrowPath.lineTo(tipX + (arrowWidth / 2f), tipY - arrowLength)
            }
            ArrowDirection.UP -> {
                // Points up at bottom edge of target
                val tipX = targetRect.centerX()
                val tipY = targetRect.bottom + 12f - bounceOffset
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX - (arrowWidth / 2f), tipY + arrowLength)
                arrowPath.lineTo(tipX + (arrowWidth / 2f), tipY + arrowLength)
            }
            ArrowDirection.RIGHT -> {
                // Points right at left edge of target
                val tipX = targetRect.left - 12f + bounceOffset
                val tipY = targetRect.centerY()
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX - arrowLength, tipY - (arrowWidth / 2f))
                arrowPath.lineTo(tipX - arrowLength, tipY + (arrowWidth / 2f))
            }
            ArrowDirection.LEFT -> {
                // Points left at right edge of target
                val tipX = targetRect.right + 12f - bounceOffset
                val tipY = targetRect.centerY()
                arrowPath.moveTo(tipX, tipY)
                arrowPath.lineTo(tipX + arrowLength, tipY - (arrowWidth / 2f))
                arrowPath.lineTo(tipX + arrowLength, tipY + (arrowWidth / 2f))
            }
        }

        arrowPath.close()
        canvas.drawPath(arrowPath, fillPaint)
    }

    private fun drawCornerBrackets(canvas: Canvas, r: RectF, paint: Paint) {
        val len = minOf(r.width(), r.height()) * 0.28f

        // Top-Left
        canvas.drawLine(r.left, r.top, r.left + len, r.top, paint)
        canvas.drawLine(r.left, r.top, r.left, r.top + len, paint)

        // Top-Right
        canvas.drawLine(r.right, r.top, r.right - len, r.top, paint)
        canvas.drawLine(r.right, r.top, r.right, r.top + len, paint)

        // Bottom-Left
        canvas.drawLine(r.left, r.bottom, r.left + len, r.bottom, paint)
        canvas.drawLine(r.left, r.bottom, r.left, r.bottom - len, paint)

        // Bottom-Right
        canvas.drawLine(r.right, r.bottom, r.right - len, r.bottom, paint)
        canvas.drawLine(r.right, r.bottom, r.right, r.bottom - len, paint)
    }

    private fun renderBackgroundDimming(
        canvas: Canvas,
        cutoutRect: RectF,
        canvasWidth: Float,
        canvasHeight: Float,
        dimOpacity: Float
    ) {
        fillPaint.color = Color.BLACK
        fillPaint.alpha = (dimOpacity.coerceIn(0.0f, 1.0f) * 255).toInt()

        // Draw 4 rectangles around the cutout region (zero hardware layer allocations)
        canvas.drawRect(0f, 0f, canvasWidth, cutoutRect.top, fillPaint)                               // Top
        canvas.drawRect(0f, cutoutRect.bottom, canvasWidth, canvasHeight, fillPaint)                  // Bottom
        canvas.drawRect(0f, cutoutRect.top, cutoutRect.left, cutoutRect.bottom, fillPaint)           // Left
        canvas.drawRect(cutoutRect.right, cutoutRect.top, canvasWidth, cutoutRect.bottom, fillPaint) // Right
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