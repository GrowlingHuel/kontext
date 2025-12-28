package com.kontext.data.repository

data class StorySentence(
    val de: String,
    val en: String
)

data class StoryResponse(
    val imageDescription: String,
    val sentences: List<StorySentence>
)

interface StoryRepository {
    suspend fun generateStory(vocabList: List<String>): StoryResponse
}
