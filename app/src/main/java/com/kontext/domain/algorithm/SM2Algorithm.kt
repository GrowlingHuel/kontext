package com.kontext.domain.algorithm

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Singleton
class SM2Algorithm @Inject constructor() : SpacedRepetitionEngine {

    /**
     * Calculates the next review interval using a simplified SM-2 algorithm.
     * 
     * @param currentLevel The current 'n' (repetition number/mastery). 
     * @param rating User rating (1=Hard, 3=Good, 5=Easy). SM-2 uses 0-5.
     * @param lastReview Timestamp of last review.
     */
    override fun calculateNextReview(
        currentLevel: Int,
        rating: Int,
        lastReview: Long
    ): ReviewResult {
        // Map simplified 1-5 rating to SM-2's quality "q"
        // In our UI: 1=Again, 3=Hard, 5=Good (Just mapping for example)
        // Let's assume passed rating is 0-5.
        
        // SM-2: I(1)=1, I(2)=6, I(n) = I(n-1) * EF
        // We will approximate 'currentLevel' as the iteration count 'n'
        
        val newLevel: Int
        val intervalDays: Int
        
        if (rating < 3) {
            // Correct response quality < 3 means incorrect/forget
            newLevel = 0 
            intervalDays = 0 // Review again immediately (or next session)
        } else {
            newLevel = currentLevel + 1
            intervalDays = when (newLevel) {
                1 -> 1
                2 -> 6
                else -> {
                    // Primitive exponential growth for simplicity, 
                    // ideally we'd track EF per card but for this MVP 2.5 is standard
                    val factor = 2.5
                    val prevInterval = if (currentLevel > 2) (currentLevel - 1) * factor else 6.0
                    (prevInterval * factor).roundToInt()
                }
            }
        }
        
        // Next review time
        val nextReview = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(intervalDays.toLong())
        
        return ReviewResult(
            newLevel = newLevel,
            nextReviewTimestamp = nextReview,
            intervalDays = intervalDays
        )
    }
}
