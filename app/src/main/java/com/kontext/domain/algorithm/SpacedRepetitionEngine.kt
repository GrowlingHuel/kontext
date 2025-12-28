package com.kontext.domain.algorithm

import javax.inject.Inject

interface SpacedRepetitionEngine {
    fun calculateNextReview(
        currentLevel: Int,
        rating: Int, // 1=Again, 2=Hard, 3=Good, 4=Easy
        lastReview: Long
    ): ReviewResult
}

data class ReviewResult(
    val newLevel: Int,
    val nextReviewTimestamp: Long
)

class SM2Algorithm @Inject constructor() : SpacedRepetitionEngine {
    override fun calculateNextReview(currentLevel: Int, rating: Int, lastReview: Long): ReviewResult {
        // Rating mapping:
        // 1: Again (Reset)
        // 2: Hard (Stay or slight increase)
        // 3: Good (Level up)
        // 4: Easy (Double level up or max)

        val newLevel = when (rating) {
            1 -> 0 // Reset
            2 -> currentLevel // Stay
            3 -> (currentLevel + 1).coerceAtMost(5)
            4 -> (currentLevel + 2).coerceAtMost(5)
            else -> currentLevel
        }

        // Intervals (in days) roughly following SM-2:
        // Level 0: 0 days (< 10 min, but for simplicity 0 -> Requeue implicitly or set to now)
        // Level 1: 1 day
        // Level 2: 3 days
        // Level 3: 7 days
        // Level 4: 14 days
        // Level 5: 30 days
        val intervalDays = when(newLevel) {
            0 -> 0L // Immediately
            1 -> 1L
            2 -> 3L
            3 -> 7L
            4 -> 14L
            5 -> 30L
            else -> 0L
        }

        // If interval is 0, we might want it to reappear in the *same* session.
        // For MVP, strictly setting it to "Now + X"
        // If rating was "Again", set to Now + 1 min to avoid instant loops if sorting by time
        val nextReview = if (newLevel == 0) {
            System.currentTimeMillis() + 60_000L // 1 minute later
        } else {
            System.currentTimeMillis() + (intervalDays * 24 * 60 * 60 * 1000L)
        }

        return ReviewResult(newLevel, nextReview)
    }
}
