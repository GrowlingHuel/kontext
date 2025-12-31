package com.kontext.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "vocab_cards",
    indices = [
        Index(value = ["user_id", "next_review_timestamp"]),
        Index(value = ["user_id", "language_code"])
    ]
)
data class VocabCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // Language-agnostic field names (v2 schema)
    @ColumnInfo(name = "target_language_term")
    val targetLanguageTerm: String,
    
    @ColumnInfo(name = "native_language_term")
    val nativeLanguageTerm: String,
    
    @ColumnInfo(name = "example_sentence_target")
    val exampleSentenceTarget: String,
    
    @ColumnInfo(name = "example_sentence_native")
    val exampleSentenceNative: String,
    
    @ColumnInfo(name = "language_code")
    val languageCode: String, // "de", "es", "fr"
    
    // Spaced repetition fields
    @ColumnInfo(name = "mastery_level")
    val masteryLevel: Int = 0,
    
    @ColumnInfo(name = "next_review_timestamp")
    val nextReviewTimestamp: Long,
    
    @ColumnInfo(name = "audio_path")
    val audioPath: String? = null,
    
    // Multi-tenancy
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    // Tracking fields
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long? = null,
    
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0
) {
    // Helper properties for backward compatibility during migration
    @Deprecated("Use targetLanguageTerm", ReplaceWith("targetLanguageTerm"))
    val germanTerm: String get() = targetLanguageTerm
    
    @Deprecated("Use nativeLanguageTerm", ReplaceWith("nativeLanguageTerm"))
    val englishTerm: String get() = nativeLanguageTerm
    
    @Deprecated("Use exampleSentenceTarget", ReplaceWith("exampleSentenceTarget"))
    val exampleSentenceGerman: String get() = exampleSentenceTarget
    
    @Deprecated("Use exampleSentenceNative", ReplaceWith("exampleSentenceNative"))
    val exampleSentenceEnglish: String get() = exampleSentenceNative
}
