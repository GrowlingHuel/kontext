package com.kontext.data.repository

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.kontext.BuildConfig
import com.kontext.data.local.FileStorageHelper
import com.kontext.data.local.dao.StoryDao
import com.kontext.data.local.entity.StoryEntity
import javax.inject.Inject

import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.SafetySetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

import com.kontext.data.local.KontextDatabase // Added

class StoryRepositoryImpl @Inject constructor(
    private val database: KontextDatabase, // Changed
    private val fileStorageHelper: FileStorageHelper,
    private val imageRepository: ImageRepository
) : StoryRepository {

    private val storyDao = database.storyDao() // Added

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

    override suspend fun generateStory(vocabList: List<String>): StoryResponse = withContext(Dispatchers.IO) {
        try {
            // 1. Generate Signature
            val sortedWords = vocabList.map { it.lowercase().trim() }.sorted()
            val signature = sortedWords.joinToString("-")

            // 2. Check Cache
//            val cachedStory = storyDao.getStoryBySignature(signature)
//            if (cachedStory != null) {
//                val image = fileStorageHelper.loadBitmapFromPath(cachedStory.imagePath)
//                return@withContext parseJsonToResponse(cachedStory.jsonContent, image)
//            }

            // 3. Network Call (Cache Miss)
            val wordsJoined = sortedWords.joinToString(", ")
            val prompt = """
                You are a conservative A1 German teacher. 95% of your story MUST use the top 500 most common German words. You may use exactly TWO 'imagination' words per story, but they must be clear from context. Do NOT use bizarre, rare, or complex compound words. Keep sentences under 10 words each.
                Write a story using these target words: [$wordsJoined].
                Format the response as a valid JSON object with no markdown formatting. The JSON should have this structure:
                { 
                    "image_description": "(A vivid English prompt for an AI image generator describing the scene)", 
                    "sentences": [ 
                        {"de": "German sentence", "en": "English translation"}, 
                        ... 
                    ] 
                }
            """.trimIndent()
            
            val response = generativeModel.generateContent(prompt)
            val text = response.text ?: "{}"
            
            // Clean up markdown
            val jsonString = text.replace("```json", "").replace("```", "").trim()
            
            // Parse early to get image prompt
            val jsonObject = JSONObject(jsonString)
            val imagePrompt = jsonObject.optString("image_description", "A German scene")

            // Generate Image
            val generatedImage = imageRepository.generateScene(imagePrompt)

            // 4. Persist
            var imagePath = ""
            if (generatedImage != null) {
                imagePath = fileStorageHelper.saveBitmapToInternalStorage(generatedImage, "story_${System.currentTimeMillis()}.png")
            }

            val entity = StoryEntity(
                vocabSignature = signature,
                jsonContent = jsonString,
                imagePath = imagePath
            )
            storyDao.insertStory(entity)

            return@withContext parseJsonToResponse(jsonString, generatedImage)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext StoryResponse(
                imageDescription = "Error generating story", 
                sentences = listOf(StorySentence("Fehler beim Generieren der Geschichte.", "Error generating story: ${e.message}"))
            )
        }
    }

    private fun parseJsonToResponse(jsonString: String, image: Bitmap?): StoryResponse {
        val jsonObject = JSONObject(jsonString)
        val imageDescription = jsonObject.optString("image_description", "A German scene")
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
    override fun getHistory(): kotlinx.coroutines.flow.Flow<List<StoryEntity>> {
        return storyDao.getAllStories()
    }
}
