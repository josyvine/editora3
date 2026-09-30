package com.vineyard.aivideostudio.core.model.effects

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Domain and JSON data models representing granular editing tools
 * configured exclusively via the Master Recipe JSON script.
 * Uses Moshi code generation matching the rest of the project.
 */

enum class BlurShape {
    @Json(name = "rectangle") RECTANGLE,
    @Json(name = "circle") CIRCLE,
    @Json(name = "full_frame") FULL_FRAME
}

enum class BlurType {
    @Json(name = "gaussian") GAUSSIAN,
    @Json(name = "mosaic") MOSAIC,
    @Json(name = "privacy_box") PRIVACY_BOX
}

@JsonClass(generateAdapter = true)
data class NormalizedBounds(
    @Json(name = "left") val left: Float,
    @Json(name = "top") val top: Float,
    @Json(name = "right") val right: Float,
    @Json(name = "bottom") val bottom: Float
) {
    init {
        require(left in 0.0f..1.0f) { "left bound must be between 0.0 and 1.0" }
        require(top in 0.0f..1.0f) { "top bound must be between 0.0 and 1.0" }
        require(right in 0.0f..1.0f) { "right bound must be between 0.0 and 1.0" }
        require(bottom in 0.0f..1.0f) { "bottom bound must be between 0.0 and 1.0" }
        require(left <= right) { "left must be <= right" }
        require(top <= bottom) { "top must be <= bottom" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + (width / 2.0f)
    val centerY: Float get() = top + (height / 2.0f)
}

@JsonClass(generateAdapter = true)
data class BlurSpec(
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "shape") val shape: BlurShape = BlurShape.RECTANGLE,
    @Json(name = "type") val type: BlurType = BlurType.GAUSSIAN,
    @Json(name = "bounds") val bounds: NormalizedBounds,
    @Json(name = "intensity") val intensity: Float = 15.0f // 1.0 to 50.0 radius / pixel size
)

@JsonClass(generateAdapter = true)
data class SpeedRampSpec(
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "speed_multiplier") val speedMultiplier: Float // e.g. 0.5x, 2.0x, 4.0x
) {
    init {
        require(speedMultiplier in 0.25f..8.0f) { "Speed multiplier must be between 0.25x and 8.0x" }
        require(startTimeMs < endTimeMs) { "startTimeMs must be less than endTimeMs" }
    }
}

enum class OverlayType {
    @Json(name = "emoji") EMOJI,
    @Json(name = "brand_logo") BRAND_LOGO,
    @Json(name = "solid_badge") SOLID_BADGE,
    @Json(name = "sticker") STICKER
}

@JsonClass(generateAdapter = true)
data class ReplacementOverlaySpec(
    @Json(name = "id") val id: String,
    @Json(name = "start_time_ms") val startTimeMs: Long,
    @Json(name = "end_time_ms") val endTimeMs: Long,
    @Json(name = "type") val type: OverlayType,
    @Json(name = "content_value") val contentValue: String, // Emoji unicode, asset path, or base64
    @Json(name = "bounds") val bounds: NormalizedBounds,
    @Json(name = "rotation_degrees") val rotationDegrees: Float = 0.0f,
    @Json(name = "opacity") val opacity: Float = 1.0f
)

enum class ColorPreset {
    @Json(name = "none") NONE,
    @Json(name = "vintage") VINTAGE,
    @Json(name = "dawn") DAWN,
    @Json(name = "dusk") DUSK,
    @Json(name = "halo") HALO,
    @Json(name = "retro_film") RETRO_FILM,
    @Json(name = "bw") BW,
    @Json(name = "high_contrast") HIGH_CONTRAST,
    @Json(name = "cyberpunk") CYBERPUNK,
    @Json(name = "warm") WARM,
    @Json(name = "cool") COOL
}

@JsonClass(generateAdapter = true)
data class ColorGradeSpec(
    @Json(name = "preset") val preset: ColorPreset = ColorPreset.NONE,
    @Json(name = "brightness") val brightness: Float = 0.0f,    // -1.0 to 1.0 (0.0 = neutral)
    @Json(name = "contrast") val contrast: Float = 0.0f,        // -1.0 to 1.0 (0.0 = neutral)
    @Json(name = "saturation") val saturation: Float = 1.0f,    // 0.0 (B&W) to 2.0 (vibrant)
    @Json(name = "sharpness") val sharpness: Float = 0.0f,      // 0.0 to 1.0
    @Json(name = "hue") val hue: Float = 0.0f                  // -180.0 to 180.0 degrees
)

enum class TrackingStyle {
    @Json(name = "red_box") RED_BOX,
    @Json(name = "highlight_circle") HIGHLIGHT_CIRCLE,
    @Json(name = "flashing_arrow") FLASHING_ARROW,
    @Json(name = "spotlight") SPOTLIGHT
}

@JsonClass(generateAdapter = true)
data class TrackingKeyframe(
    @Json(name = "time_ms") val timeMs: Long,
    @Json(name = "x") val x: Float, // Normalized 0.0 - 1.0
    @Json(name = "y") val y: Float, // Normalized 0.0 - 1.0
    @Json(name = "width") val width: Float = 0.15f,
    @Json(name = "height") val height: Float = 0.15f
)

@JsonClass(generateAdapter = true)
data class TrackingIndicatorSpec(
    @Json(name = "id") val id: String,
    @Json(name = "style") val style: TrackingStyle = TrackingStyle.RED_BOX,
    @Json(name = "color_hex") val colorHex: String = "#FF0000",
    @Json(name = "stroke_width_px") val strokeWidthPx: Float = 6.0f,
    @Json(name = "label") val label: String? = null,
    @Json(name = "keyframes") val keyframes: List<TrackingKeyframe> = emptyList()
)