package com.kontext.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.kontext.BuildConfig
import com.kontext.domain.model.LanguageConfig
import com.kontext.domain.usecase.StoryPromptBuilder
import com.kontext.util.Result
import javax.inject.Inject

import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.SafetySetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class StoryRepositoryImpl @Inject constructor(
    private val imageRepository: ImageRepository,
    private val promptBuilder: StoryPromptBuilder,
    private val languageConfig: LanguageConfig
) : StoryRepository {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash", 
        apiKey = BuildConfig.GEMINI_API_KEY,
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
        )
    )

    override suspend fun generateStory(vocabList: List<String>): Result<StoryResponse> = withContext(Dispatchers.IO) {
        try {
            // Build prompt using injected builder
            val prompt = promptBuilder.buildPrompt(vocabList, languageConfig)
            
            val response = generativeModel.generateContent(prompt)
            val text = response.text ?: "{}"
            
            // Clean up markdown
            val jsonString = text.replace("```json", "").replace("```", "").trim()
            
            // Parse to get image prompt
            val jsonObject = JSONObject(jsonString)
            val imagePrompt = jsonObject.optString("image_description", "A ${languageConfig.targetLanguage.displayName} scene")

            // Generate Image (handle Result type)
            val generatedImage = when (val imageResult = imageRepository.generateScene(imagePrompt)) {
                is Result.Success -> imageResult.data
                is Result.Error -> {
                    Log.w("StoryRepository", "Image generation failed, continuing without image", imageResult.exception)
                    null
                }
                Result.Loading -> null
            }

            Result.Success(parseJsonToResponse(jsonString, generatedImage))

        } catch (e: Exception) {
            Log.e("StoryRepository", "Story generation failed", e)
            Result.Error(e)
        }
    }

    private fun parseJsonToResponse(jsonString: String, image: Bitmap?): StoryResponse {
        val jsonObject = JSONObject(jsonString)
        val imageDescription = jsonObject.optString("image_description", "A ${languageConfig.targetLanguage.displayName} scene")
        val jsonSentences = jsonObject.optJSONArray("sentences")
        
        val sentences = mutableListOf<StorySentence>()
        if (jsonSentences != null) {
            for (i in 0 until jsonSentences.length()) {
                val item = jsonSentences.getJSONObject(i)
                sentences.add(
                    StorySentence(
                        de = item.optString("de", ""),
                        en = item.optString("en", "")
                    )
                )
            }
        }
        return StoryResponse(imageDescription, sentences, image)
    }
}
