package com.kontext.data.repository

import com.google.ai.client.generativeai.GenerativeModel
import com.kontext.BuildConfig
import javax.inject.Inject

import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.SafetySetting
import org.json.JSONObject

class StoryRepositoryImpl @Inject constructor() : StoryRepository {

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

    override suspend fun generateStory(vocabList: List<String>): StoryResponse {
        try {
            val wordsJoined = vocabList.joinToString(", ")
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
            
            // Clean up potential markdown code blocks if the model puts them in
            val jsonString = text.replace("```json", "").replace("```", "").trim()
            
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
            
            return StoryResponse(imageDescription, sentences)

        } catch (e: Exception) {
            // Fallback in case of error
            return StoryResponse(
                imageDescription = "Error generating story", 
                sentences = listOf(StorySentence("Fehler beim Generieren der Geschichte.", "Error generating story: ${e.message}"))
            )
        }
    }
}
