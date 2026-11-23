package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdIdPoolTest {
    private var clock = 0L
    private val destroyed = mutableListOf<String>()
    private val pool = AdIdPool<String>(ttlMs = 1000, now = { clock }, destroy = { destroyed += it })

    private val a = listOf("A")
    private val ab = listOf("A", "B")

    /** Chạy một lượt load của [chain] ra [ad] từ [id] và giao (nếu có người chờ). */
    private fun load(chain: List<String>, id: String, ad: String) {
        pool.startLoad(chain)
        pool.onLoaded(chain, id, ad)?.let { (waiter, loaded) -> waiter.onReady(loaded) }
    }

    @Test
    fun adLoadedForOnePlacementIsUsableByAnotherWithTheSameId() {
        load(listOf("A", "C"), "A", "ad1")
        assertFalse(pool.hasFresh(listOf("B")))
        assertEquals("ad1", pool.take(a)?.ad)
        assertNull(pool.take(a))
    }

    @Test
    fun lowerPriorityIdIsUsedWhenOnlyItIsAvailable() {
        load(listOf("B"), "B", "adB")
        val loaded = pool.take(ab)
        assertEquals("adB", loaded?.ad)
        assertEquals("B", loaded?.id)
    }

    @Test
    fun everyWaiterGetsItsOwnLoadAndAdsAreDeliveredFifo() {
        val got = mutableListOf<String>()
        pool.addWaiter(a, { got += "w1:" + it.ad }, {})
        assertTrue(pool.needsLoad(a))
        pool.startLoad(a)
        pool.addWaiter(a, { got += "w2:" + it.ad }, {})
        assertTrue(pool.needsLoad(a))
        pool.startLoad(a)
        assertFalse(pool.needsLoad(a))

        pool.onLoaded(a, "A", "ad1")?.let { (w, l) -> w.onReady(l) }
        pool.onLoaded(a, "A", "ad2")?.let { (w, l) -> w.onReady(l) }
        assertEquals(listOf("w1:ad1", "w2:ad2"), got)
    }

    @Test
    fun cancelledWaiterLeavesTheAdInThePool() {
        val waiter = pool.addWaiter(a, { error("không được giao") }, {})
        pool.startLoad(a)
        pool.removeWaiter(waiter)
        assertNull(pool.onLoaded(a, "A", "ad1"))
        assertEquals("ad1", pool.take(a)?.ad)
    }

    @Test
    fun adGoesToAWaiterOfAnotherPlacementThatWantsTheSameId() {
        var got: String? = null
        pool.addWaiter(a, { got = it.ad }, {})
        load(listOf("C", "A"), "A", "ad1") // lượt load của placement khác (chuỗi C, A) ra ad của A
        assertEquals("ad1", got)
        assertFalse(pool.hasFresh(a))
    }

    @Test
    fun expiredAdsAreDestroyedAndNotReturned() {
        load(a, "A", "old")
        clock = 1000
        assertFalse(pool.hasFresh(a))
        assertNull(pool.take(a))
        assertEquals(listOf("old"), destroyed)
    }

    @Test
    fun failureMarksTheChainUnavailableUntilANewLoadStarts() {
        var failed = false
        pool.addWaiter(a, {}, { failed = true })
        pool.startLoad(a)
        pool.onFailed(a)?.onFailed?.invoke()
        assertTrue(failed)
        assertTrue(pool.failedAndIdle(a))
        pool.startLoad(a)
        assertFalse(pool.failedAndIdle(a))
    }

    @Test
    fun preloadAddsOneSpareLoadOnlyWhenNothingIsCached() {
        assertTrue(pool.needsLoad(a, preload = true))
        pool.startLoad(a)
        assertFalse(pool.needsLoad(a, preload = true))
        pool.onLoaded(a, "A", "ad1")
        assertFalse(pool.needsLoad(a, preload = true))
    }

    @Test
    fun eachIdKeepsOnlyOneSpareAd() {
        load(a, "A", "ad1")
        clock = 10
        load(a, "A", "ad2")
        assertEquals(listOf("ad1"), destroyed) // giữ ad mới hơn
        assertEquals("ad2", pool.take(a)?.ad)
        assertNull(pool.take(a))
    }

    @Test
    fun preloadWaitsForARunningLoadThatTouchesTheSameId() {
        pool.startLoad(listOf("C", "A")) // placement khác đang load chuỗi có A
        assertFalse(pool.needsLoad(a, preload = true))
        assertFalse(pool.needsLoad(ab, preload = true))
        assertTrue(pool.needsLoad(listOf("B"), preload = true))
        pool.onLoaded(listOf("C", "A"), "C", "adC") // xong (ra ad của C) - không còn lượt nào chạm A
        assertTrue(pool.needsLoad(a, preload = true))
    }

    @Test
    fun clearDestroysCachedAds() {
        load(a, "A", "ad1")
        pool.clear()
        assertEquals(listOf("ad1"), destroyed)
        assertNull(pool.take(a))
    }
}
