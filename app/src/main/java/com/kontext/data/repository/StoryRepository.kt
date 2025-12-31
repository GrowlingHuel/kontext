package com.kontext.data.repository

import android.graphics.Bitmap
import com.kontext.util.Result

data class StorySentence(
    val de: String,
    val en: String
)

data class StoryResponse(
    val imageDescription: String,
    val sentences: List<StorySentence>,
    val image: Bitmap? = null
)

interface StoryRepository {
    suspend fun generateStory(vocabList: List<String>): Result<StoryResponse>
}
