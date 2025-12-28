package com.kontext.domain.model

data class StoryLevel(
    val level: Int,
    val choiceA: StoryChoice,
    val choiceB: StoryChoice
)

data class StoryChoice(
    val prompt: String,
    val sentencesM: List<String>,
    val sentencesF: List<String>,
    val sentencesEn: List<String>,
    val imagePrompt: String
)
