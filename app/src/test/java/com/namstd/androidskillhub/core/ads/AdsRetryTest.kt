package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdsRetryTest {

    @Test
    fun delays_doubleThenStop() {
        assertEquals(2_000L, retryDelay(0))
        assertEquals(4_000L, retryDelay(1))
        assertEquals(8_000L, retryDelay(2))
        assertNull(retryDelay(3))
    }
}
