package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredWorkTest {

    @Test
    fun runsImmediately_whenReady() {
        val work = DeferredWork { true }
        var runs = 0
        work.submit("a") { runs++ }
        assertEquals(1, runs)
        assertEquals(0, work.size())
    }

    @Test
    fun waitsUntilReady_thenRunsOnce_dedupedByKey() {
        var ready = false
        val work = DeferredWork { ready }
        val runs = mutableListOf<String>()
        work.submit("native:LANGUAGE") { runs += "old" }
        work.submit("native:LANGUAGE") { runs += "new" }
        work.submit("inter:LANGUAGE") { runs += "inter" }
        work.flush()
        assertEquals(emptyList<String>(), runs)

        ready = true
        work.flush()
        work.flush()
        assertEquals(listOf("new", "inter"), runs)
    }

    @Test
    fun clear_dropsPending() {
        var ready = false
        val work = DeferredWork { ready }
        var runs = 0
        work.submit("a") { runs++ }
        work.clear()
        ready = true
        work.flush()
        assertEquals(0, runs)
    }
}
