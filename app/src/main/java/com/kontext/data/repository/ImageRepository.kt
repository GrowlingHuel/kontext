package com.kontext.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.kontext.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

interface ImageRepository {
    suspend fun generateScene(prompt: String): Bitmap?
}

@Singleton
class ImageRepositoryImpl @Inject constructor() : ImageRepository {

    override suspend fun generateScene(prompt: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/imagen-4.0-generate-001:predict?key=$apiKey")
            
            // Imagen 4 might handle prompt slightly differently, but standard predict format usually:
            // { "instances": [ { "prompt": "..." } ], "parameters": { "sampleCount": 1 } }
            // Or simpler. Let's try standard Google AI format.
            // Documentation implies: instances: [{ prompt: "..." }]
            
            val jsonBody = JSONObject().apply {
                put("instances", org.json.JSONArray().put(
                    JSONObject().put("prompt", prompt)
                ))
                put("parameters", JSONObject().put("sampleCount", 1))
            }

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(jsonBody.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                // Parse response
                val jsonResponse = JSONObject(response.toString())
                val predictions = jsonResponse.optJSONArray("predictions")
                if (predictions != null && predictions.length() > 0) {
                    val firstPrediction = predictions.getJSONObject(0)
                    // Usually "bytesBase64Encoded" or "mimeType" + "bytesBase64Encoded"
                    val base64String = firstPrediction.optString("bytesBase64Encoded")
                    
                    if (base64String.isNotEmpty()) {
                        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                        return@withContext BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    }
                }
            } else {
                // Read error stream
                val reader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val errorResponse = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    errorResponse.append(line)
                }
                reader.close()
                println("Image Gen Error: $responseCode - $errorResponse")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
