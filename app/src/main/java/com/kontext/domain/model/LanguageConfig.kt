package com.kontext.domain.model

import androidx.annotation.RawRes
import com.kontext.R

/**
 * Configuration for a specific language learning context.
 * This encapsulates all language-specific settings including target language,
 * proficiency level, and data source.
 */
data class LanguageConfig(
    val targetLanguage: Language,
    val nativeLanguage: Language = Language.GERMAN, // User's L1 (currently assumes English translations)
    val level: CEFRLevel = CEFRLevel.A1,
    @RawRes val csvResourceId: Int
) {
    companion object {
        /**
         * Default configuration for German learning.
         */
        fun forGerman() = LanguageConfig(
            targetLanguage = Language.GERMAN,
            nativeLanguage = Language.GERMAN, // English translation in CSV
            csvResourceId = R.raw.german_4000,
            level = CEFRLevel.A1
        )
        
        /**
         * Configuration for Spanish learning.
         * Note: CSV resource must be added to res/raw before use.
         */
        fun forSpanish() = LanguageConfig(
            targetLanguage = Language.SPANISH,
            nativeLanguage = Language.GERMAN, // English translation
            csvResourceId = 0, // Placeholder until Spanish CSV added
            level = CEFRLevel.A1
        )
        
        /**
         * Configuration for French learning.
         * Note: CSV resource must be added to res/raw before use.
         */
        fun forFrench() = LanguageConfig(
            targetLanguage = Language.FRENCH,
            nativeLanguage = Language.GERMAN, // English translation
            csvResourceId = 0, // Placeholder until French CSV added
            level = CEFRLevel.A1
        )
    }
}
