package com.kontext.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kontext.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE vocab_signature = :signature LIMIT 1")
    suspend fun getStoryBySignature(signature: String): StoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Query("SELECT * FROM stories ORDER BY created_at DESC")
    fun getAllStories(): Flow<List<StoryEntity>>
}
