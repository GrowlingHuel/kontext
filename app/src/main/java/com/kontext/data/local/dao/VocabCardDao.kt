package com.kontext.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kontext.data.local.entity.VocabCard

@Dao
interface VocabCardDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<VocabCard>): List<Long>
    
    @Query("SELECT COUNT(*) FROM vocab_cards")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM vocab_cards WHERE user_id = :userId")
    suspend fun getCountForUser(userId: String): Int
    
    @Query("SELECT * FROM vocab_cards WHERE user_id = :userId AND next_review_timestamp <= :now")
    suspend fun getCardsDueForUser(userId: String, now: Long): List<VocabCard>

    @Query("SELECT * FROM vocab_cards WHERE user_id = :userId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomCards(userId: String, limit: Int): List<VocabCard>

    @androidx.room.Update
    suspend fun update(card: VocabCard)

    @Query("DELETE FROM vocab_cards")
    suspend fun deleteAll()

    // Updated query to use new column names
    @Query("SELECT native_language_term FROM vocab_cards WHERE LOWER(target_language_term) LIKE '%' || :targetTerm || '%' LIMIT 1")
    suspend fun findNativeForTarget(targetTerm: String): String?
    
    // Deprecated method for backward compatibility - delegates to new method
    @Deprecated("Use findNativeForTarget", ReplaceWith("findNativeForTarget(germanTerm)"))
    suspend fun findEnglishForGerman(germanTerm: String): String? = findNativeForTarget(germanTerm)
}
