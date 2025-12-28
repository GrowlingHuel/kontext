package com.kontext.domain.algorithm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SM2AlgorithmTest {

    private val algorithm = SM2Algorithm()

    @Test
    fun `first repetition with good rating should set interval to 1 day`() {
        val result = algorithm.calculateNextReview(0, 3, 0)
        assertEquals(1, result.newLevel)
        assertEquals(1, result.intervalDays)
    }

    @Test
    fun `second repetition with good rating should set interval to 6 days`() {
        val result = algorithm.calculateNextReview(1, 3, 0)
        assertEquals(2, result.newLevel)
        assertEquals(6, result.intervalDays)
    }

    @Test
    fun `third repetition with good rating should increase interval approx 2_5x`() {
        // level 2 (6 days) -> level 3
        val result = algorithm.calculateNextReview(2, 3, 0)
        assertEquals(3, result.newLevel)
        assertEquals(15, result.intervalDays) // 6 * 2.5 = 15
    }

    @Test
    fun `incorrect rating should reset progress`() {
        val result = algorithm.calculateNextReview(5, 1, 0) // Level 5, Rating 'Again'
        assertEquals(0, result.newLevel)
        assertEquals(0, result.intervalDays)
    }

    @Test
    fun `easy rating should allow progress`() {
        val result = algorithm.calculateNextReview(2, 5, 0)
        assertEquals(3, result.newLevel)
        assertTrue(result.intervalDays >= 15)
    }
}
