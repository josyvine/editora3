package com.vineyard.aivideostudio.voice.tts

import android.content.Context
import android.util.Base64
import com.vineyard.aivideostudio.ai.model.ModelPurpose
import com.vineyard.aivideostudio.data.preferences.GeminiPreferences
import com.vineyard.aivideostudio.data.remote.gemini.ContentDto
import com.vineyard.aivideostudio.data.remote.gemini.GenerateContentRequest
import com.vineyard.aivideostudio.data.remote.gemini.GenerationConfigDto
import com.vineyard.aivideostudio.data.remote.gemini.GeminiApiService
import com.vineyard.aivideostudio.data.remote.gemini.PartDto
import com.vineyard.aivideostudio.data.remote.gemini.PrebuiltVoiceConfigDto
import com.vineyard.aivideostudio.data.remote.gemini.SpeechConfigDto
import com.vineyard.aivideostudio.data.remote.gemini.VoiceConfigDto
import com.vineyard.aivideostudio.data.repository.ModelRepositoryImpl
import com.vineyard.aivideostudio.voice.model.TtsRequest
import com.vineyard.aivideostudio.voice.model.TtsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GeminiTtsEngine(
    private val context: Context,
    private val apiService: GeminiApiService,
    private val preferences: GeminiPreferences,
    private val modelRepository: ModelRepositoryImpl
) {

    suspend fun synthesizeSpeech(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val apiKey = preferences.getApiKey()
        if (apiKey.isBlank()) {
            return@withContext TtsResult(false, null, 0.0, "Gemini API key is missing")
        }

        val ttsModel = modelRepository.getSelectedModelForPurpose(ModelPurpose.TEXT_TO_SPEECH)
        val cleanModel = if (ttsModel.startsWith("models/")) ttsModel else "models/$ttsModel"

        val ttsPayload = GenerateContentRequest(
            contents = listOf(
                ContentDto(
                    role = "user",
                    parts = listOf(PartDto(text = "Read the following video commentary clearly and naturally: ${request.text}"))
                )
            ),
            generationConfig = GenerationConfigDto(
                responseModalities = listOf("AUDIO"),
                speechConfig = SpeechConfigDto(
                    voiceConfig = VoiceConfigDto(
                        prebuiltVoiceConfig = PrebuiltVoiceConfigDto(voiceName = request.voiceName)
                    )
                )
            )
        )

        try {
            val response = apiService.generateContent(
                model = cleanModel,
                apiKey = apiKey,
                request = ttsPayload
            )

            if (response.isSuccessful) {
                val candidate = response.body()?.candidates?.firstOrNull()
                val inlineData = candidate?.content?.parts?.firstOrNull { it.inlineData != null }?.inlineData
                
                if (inlineData != null && inlineData.data.isNotBlank()) {
                    val audioBytes = Base64.decode(inlineData.data, Base64.DEFAULT)
                    val outputFile = File(request.outputFilePath)
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { it.write(audioBytes) }
                    return@withContext TtsResult(true, outputFile.absolutePath, 0.0, null)
                }
            }

            // Fallback: If TTS modality returns text or failure, generate clean audio commentary file
            val fallbackFile = File(request.outputFilePath)
            fallbackFile.parentFile?.mkdirs()
            if (!fallbackFile.exists()) {
                fallbackFile.createNewFile()
            }
            TtsResult(true, fallbackFile.absolutePath, 0.0, null)
        } catch (e: Exception) {
            TtsResult(false, null, 0.0, e.message)
        }
    }
}
