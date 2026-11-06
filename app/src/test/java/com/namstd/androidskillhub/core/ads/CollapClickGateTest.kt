package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapClickGateTest {
    private fun opening(clicks: Set<Int>, upTo: Int = 9) = (1..upTo).filter { CollapClickGate(clicks).opensAd(it) }

    @Test
    fun listedClicksOpenTheAd() {
        assertEquals(listOf(2, 4), opening(setOf(2, 4)))
        assertEquals(listOf(3, 5, 7), opening(setOf(3, 5, 7)))
    }

    @Test
    fun emptyIsOff() {
        assertFalse(CollapClickGate(emptySet()).isEnabled)
        assertEquals(emptyList<Int>(), opening(emptySet()))
        assertFalse(CollapClickGate(setOf(0, -1)).isEnabled)
        assertTrue(CollapClickGate(setOf(1)).isEnabled)
    }
}
