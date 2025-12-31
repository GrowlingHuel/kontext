package com.kontext.data.repository

import com.kontext.data.local.SessionManager
import com.kontext.data.local.dao.VocabCardDao
import com.kontext.data.local.entity.VocabCard
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocabRepositoryImpl @Inject constructor(
    private val dao: VocabCardDao,
    private val sessionManager: SessionManager
) : VocabRepository {
    
    override suspend fun getCount(): Int {
        return dao.getCount()
    }
    
    override suspend fun getCardsForReview(): List<VocabCard> {
        val userId = sessionManager.getCurrentUserId()
        val now = System.currentTimeMillis()
        return dao.getCardsDueForUser(userId, now)
    }

    override suspend fun getRandomCards(limit: Int): List<VocabCard> {
        val userId = sessionManager.getCurrentUserId()
        return dao.getRandomCards(userId, limit)
    }

    override suspend fun updateCard(card: VocabCard) {
        dao.update(card)
    }

    override suspend fun findEnglishForGerman(germanTerm: String): String? {
        return dao.findNativeForTarget(germanTerm)
    }
}
