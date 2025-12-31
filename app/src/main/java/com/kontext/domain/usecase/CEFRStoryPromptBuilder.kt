package com.kontext.domain.usecase

import com.kontext.domain.model.LanguageConfig
import javax.inject.Inject

/**
 * CEFR-based story prompt builder for AI content generation.
 * Generates prompts that enforce vocabulary restrictions based on CEFR level.
 */
class CEFRStoryPromptBuilder @Inject constructor() : StoryPromptBuilder {
    
    override fun buildPrompt(vocabWords: List<String>, config: LanguageConfig): String {
        val languageName = config.targetLanguage.displayName
        val level = config.level.displayName
        val topN = config.level.topWordCount
        val wordsJoined = vocabWords.sorted().joinToString(", ")
        
        return """
            You are a conservative $level $languageName teacher. 
            95% of your story MUST use the top $topN most common $languageName words. 
            You may use exactly TWO 'imagination' words per story, but they must be clear from context. 
            Do NOT use bizarre, rare, or complex compound words. 
            Keep sentences under 10 words each.
            
            Write a story using these target words: [$wordsJoined].
            
            Format the response as a valid JSON object with no markdown formatting. 
            The JSON should have this structure:
            { 
                "image_description": "(A vivid English prompt for an AI image generator describing the scene)", 
                "sentences": [ 
                    {"de": "$languageName sentence", "en": "English translation"}, 
                    ... 
                ] 
            }
        """.trimIndent()
    }
}
