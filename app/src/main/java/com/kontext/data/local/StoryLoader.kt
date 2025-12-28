package com.kontext.data.local

import android.content.Context
import com.kontext.domain.model.StoryDefinition
import com.kontext.domain.model.UserGender
import org.json.JSONArray
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryLoader @Inject constructor() {

    fun getStories(context: Context, userGender: UserGender, userName: String): List<StoryDefinition> {
        val jsonString = loadJSONFromAsset(context, "stories.json") ?: return emptyList()
        val stories = mutableListOf<StoryDefinition>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val batchId = item.getInt("batch_id")
                
                // Parse Target Words
                val wordsArray = item.optJSONArray("target_words")
                val targetWords = mutableListOf<String>()
                if (wordsArray != null) {
                    for (j in 0 until wordsArray.length()) {
                        targetWords.add(wordsArray.getString(j))
                    }
                }

                // Select Story Version based on Gender
                val storyKey = if (userGender == UserGender.MALE) "story_M" else "story_F"
                val storyObj = item.optJSONObject(storyKey)

                if (storyObj != null) {
                    var rawDe = storyObj.optString("de", "")
                    var rawEn = storyObj.optString("en", "")

                    // Replace {{HERO}} with userName
                    val cleanName = if (userName.isBlank()) "Hero" else userName
                    
                    val finalDe = rawDe.replace("{{HERO}}", cleanName)
                    val finalEn = rawEn.replace("{{HERO}}", cleanName)

                    // Image Path (referencing asset)
                    val imagePath = "file:///android_asset/story_$batchId.jpg"

                    stories.add(
                        StoryDefinition(
                            id = batchId,
                            targetWords = targetWords,
                            imagePath = imagePath,
                            germanText = finalDe,
                            englishText = finalEn
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return stories
    }

    private fun loadJSONFromAsset(context: Context, fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (ex: IOException) {
            ex.printStackTrace()
            null
        }
    }
}
