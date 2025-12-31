package com.kontext.domain.model

import java.util.Locale

/**
 * Supported target languages for learning.
 * Each language has a code (ISO 639-1), display name, and Locale for TTS.
 */
enum class Language(
    val code: String,
    val displayName: String,
    val locale: Locale
) {
    GERMAN("de", "German", Locale.GERMAN),
    SPANISH("es", "Spanish", Locale("es", "ES")),
    FRENCH("fr", "French", Locale.FRENCH);
    
    companion object {
        /**
         * Find a language by its ISO code.
         * @return Language if found, null otherwise
         */
        fun fromCode(code: String): Language? = values().find { it.code == code }
    }
}

/**
 * CEFR (Common European Framework of Reference) language proficiency levels.
 * Each level defines the vocabulary size used for content generation.
 */
enum class CEFRLevel(
    val displayName: String,
    val topWordCount: Int
) {
    A1("Beginner", 500),
    A2("Elementary", 1000),
    B1("Intermediate", 2000),
    B2("Upper Intermediate", 3000),
    C1("Advanced", 5000)
}
