package com.kontext.domain.model

data class StoryDefinition(
    val id: Int,
    val targetWords: List<String>,
    val imagePath: String,
    val germanText: String,
    val englishText: String
)
