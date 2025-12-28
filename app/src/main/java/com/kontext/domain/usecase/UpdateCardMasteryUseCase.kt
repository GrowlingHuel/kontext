package com.kontext.domain.usecase

import com.kontext.data.local.entity.VocabCard
import com.kontext.data.repository.VocabRepository
import com.kontext.domain.algorithm.SpacedRepetitionEngine
import javax.inject.Inject

class UpdateCardMasteryUseCase @Inject constructor(
    private val repository: VocabRepository,
    private val engine: SpacedRepetitionEngine
) {
    suspend operator fun invoke(card: VocabCard, rating: Int) {
        val result = engine.calculateNextReview(
            currentLevel = card.masteryLevel,
            rating = rating,
            lastReview = card.lastReviewedAt ?: 0L
        )

        val updatedCard = card.copy(
            masteryLevel = result.newLevel,
            nextReviewTimestamp = result.nextReviewTimestamp,
            lastReviewedAt = System.currentTimeMillis(),
            reviewCount = card.reviewCount + 1
        )

        repository.updateCard(updatedCard)
    }
}
