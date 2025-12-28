package com.kontext.data.repository

import com.kontext.data.local.entity.VocabCard
import kotlinx.coroutines.flow.Flow

interface VocabRepository {
    suspend fun getCount(): Int
    suspend fun getCardsForReview(): List<VocabCard>
    suspend fun getRandomCards(limit: Int): List<VocabCard>
    suspend fun updateCard(card: VocabCard)
    suspend fun findEnglishForGerman(germanTerm: String): String?
}
