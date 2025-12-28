package com.kontext.data.local

import android.content.Context
import com.kontext.domain.model.StoryLevel
import com.kontext.domain.model.StoryChoice
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryLoader @Inject constructor() {

    fun loadStories(context: Context): List<StoryLevel> {
        val jsonString = loadJSONFromAsset(context, "stories.json") ?: return emptyList()
        val stories = mutableListOf<StoryLevel>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val level = item.optInt("level", -1)
                
                if (level != -1) {
                    val choiceA = parseChoice(item.optJSONObject("choice_a") ?: JSONObject())
                    val choiceB = parseChoice(item.optJSONObject("choice_b") ?: JSONObject())

                    stories.add(StoryLevel(level, choiceA, choiceB))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("StoryLoader", "Failed to parse JSON: ${e.message}", e)
            e.printStackTrace()
        }

        return stories
    }

    private fun parseChoice(json: JSONObject): StoryChoice {
        return StoryChoice(
            prompt = json.optString("prompt", ""),
            sentencesM = jsonArrayToList(json.optJSONArray("sentences_m")),
            sentencesF = jsonArrayToList(json.optJSONArray("sentences_f")),
            sentencesEn = jsonArrayToList(json.optJSONArray("en_sentences")),
            imagePrompt = json.optString("image_prompt", "")
        )
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        val list = mutableListOf<String>()
        if (array != null) {
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        }
        return list
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
