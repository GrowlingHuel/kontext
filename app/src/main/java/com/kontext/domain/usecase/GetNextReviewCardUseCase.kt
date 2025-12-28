package com.kontext.domain.usecase

import com.kontext.data.local.SessionManager
import com.kontext.data.local.entity.VocabCard
import com.kontext.data.repository.VocabRepository
import javax.inject.Inject

class GetNextReviewCardUseCase @Inject constructor(
    private val repository: VocabRepository,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(): List<VocabCard> {
        val userId = sessionManager.getCurrentUserId()
        // In a real app we might have complex logic here to mix new cards + reviews
        // For now, just passthrough to repo
        return repository.getCardsForReview()
    }
}
