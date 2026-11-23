package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdCountdownTest {

    @Test
    fun wholeSeconds_countEachSecond() {
        assertEquals(listOf(5 to 1000L, 4 to 1000L, 3 to 1000L, 2 to 1000L, 1 to 1000L), countdownSteps(5000))
    }

    @Test
    fun fractionalSeconds_firstNumberHoldsRemainder() {
        assertEquals(listOf(3 to 500L, 2 to 1000L, 1 to 1000L), countdownSteps(2500))
    }

    @Test
    fun underOneSecond_showsOne() {
        assertEquals(listOf(1 to 3L), countdownSteps(3))
    }

    @Test
    fun zeroOrNegative_noSteps() {
        assertEquals(emptyList<Pair<Int, Long>>(), countdownSteps(0))
        assertEquals(emptyList<Pair<Int, Long>>(), countdownSteps(-10))
    }
}
