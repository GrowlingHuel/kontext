package com.kontext.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stories",
    indices = [Index(value = ["vocab_signature"], unique = true)]
)
data class StoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "vocab_signature")
    val vocabSignature: String,

    @ColumnInfo(name = "json_content")
    val jsonContent: String,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
