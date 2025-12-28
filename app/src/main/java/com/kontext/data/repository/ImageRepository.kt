package com.kontext.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.kontext.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

interface ImageRepository {
    suspend fun generateScene(prompt: String): Bitmap?
}

@Serializable
data class GenerationRequest(
    val instances: List<PromptInstance>,
    val parameters: GenerationParameters
)

@Serializable
data class PromptInstance(val prompt: String)

@Serializable
data class GenerationParameters(val sampleCount: Int)

@Singleton
class ImageRepositoryImpl @Inject constructor(
    private val client: HttpClient
) : ImageRepository {

    override suspend fun generateScene(prompt: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val url = "https://generativelanguage.googleapis.com/v1beta/models/imagen-4.0-generate-001:predict?key=$apiKey"

            val requestBody = GenerationRequest(
                instances = listOf(PromptInstance(prompt)),
                parameters = GenerationParameters(sampleCount = 1)
            )

            val response: JsonObject = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val predictions = response["predictions"]?.jsonArray
            if (predictions != null && predictions.isNotEmpty()) {
                val firstPrediction = predictions[0].jsonObject
                // Usually "bytesBase64Encoded" or similar
                val base64String = firstPrediction["bytesBase64Encoded"]?.jsonPrimitive?.content
                
                if (!base64String.isNullOrEmpty()) {
                    val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                    return@withContext BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
