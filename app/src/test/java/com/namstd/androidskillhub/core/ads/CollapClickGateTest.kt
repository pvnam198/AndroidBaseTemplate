package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapClickGateTest {
    private fun adPresses(raw: String, upTo: Int = 9) = (1..upTo).filter { CollapClickGate(raw).opensAd(it) }

    @Test
    fun eachDigitIsAnXPressThatOpensTheAd() {
        assertEquals(listOf(3, 5, 7), adPresses("357"))
        assertEquals(listOf(2, 4), adPresses("24"))
        assertEquals(listOf(3), adPresses("3"))
    }

    @Test
    fun separatorsAreIgnored() {
        assertEquals(adPresses("357"), adPresses("3,5,7"))
    }

    @Test
    fun zeroOrEmptyIsOff() {
        for (raw in listOf("0", "", "abc")) {
            assertFalse(raw, CollapClickGate(raw).isEnabled)
            assertEquals(raw, emptyList<Int>(), adPresses(raw))
        }
        assertTrue(CollapClickGate("1").isEnabled)
    }
}
