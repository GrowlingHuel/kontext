package com.kontext.domain.algorithm

import javax.inject.Inject
import javax.inject.Singleton

data class ReviewResult(
    val newLevel: Int,
    val nextReviewTimestamp: Long,
    val intervalDays: Int
)

interface SpacedRepetitionEngine {
    fun calculateNextReview(
        currentLevel: Int,
        rating: Int,
        lastReview: Long
    ): ReviewResult
}
