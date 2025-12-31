package com.kontext.domain.usecase

import com.kontext.domain.model.LanguageConfig

/**
 * Interface for building AI story generation prompts.
 * Implementations should construct appropriate prompts based on the target language and level.
 */
interface StoryPromptBuilder {
    fun buildPrompt(vocabWords: List<String>, config: LanguageConfig): String
}
