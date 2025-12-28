package com.kontext.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vocab_cards")
data class VocabCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "german_term")
    val germanTerm: String,
    
    @ColumnInfo(name = "english_term")
    val englishTerm: String,
    
    @ColumnInfo(name = "example_sentence_german")
    val exampleSentenceGerman: String,
    
    @ColumnInfo(name = "example_sentence_english")
    val exampleSentenceEnglish: String,
    
    @ColumnInfo(name = "mastery_level")
    val masteryLevel: Int = 0, // 0-5 scale
    
    @ColumnInfo(name = "next_review_timestamp")
    val nextReviewTimestamp: Long,
    
    @ColumnInfo(name = "audio_path")
    val audioPath: String? = null,
    
    @ColumnInfo(name = "user_id")
    val userId: String, // Supabase user ID
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long? = null,
    
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0
)
