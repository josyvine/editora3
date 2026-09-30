package com.vineyard.aivideostudio.core.common

object AppConstants {
    const val DATABASE_NAME = "editora_studio.db"
    const val PREFERENCES_NAME = "editora_preferences"
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    // Default models based on capability rules
    const val DEFAULT_ANALYSIS_MODEL = "gemini-3.5-flash"
    const val DEFAULT_DIRECTOR_MODEL = "gemini-3.5-flash"
    const val DEFAULT_COMMENTARY_MODEL = "gemini-3.5-flash"
    const val DEFAULT_TRANSCRIPTION_MODEL = "gemini-3.5-flash"
    const val DEFAULT_TTS_MODEL = "gemini-2.5-flash-preview-tts"
    const val DEFAULT_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"

    const val DEFAULT_MAX_QA_RETRIES = 3
    const val DEFAULT_KEEP_INTERMEDIATE_VIDEOS = true
    const val DEFAULT_AUTO_FINAL_QA = true

    const val MIME_VIDEO_MP4 = "video/mp4"
    const val MIME_AUDIO_M4A = "audio/mp4"
    const val MIME_AUDIO_WAV = "audio/wav"

    const val NOTIFICATION_CHANNEL_PROCESSING = "editora_video_processing"
    const val NOTIFICATION_ID_PROCESSING = 1001
}
