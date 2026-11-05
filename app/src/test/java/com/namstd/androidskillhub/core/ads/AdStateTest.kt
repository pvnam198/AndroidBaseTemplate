package com.namstd.androidskillhub.core.ads

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdStateTest {
    @After
    fun tearDown() {
        FullScreenGate.clearForTest()
        AdGapGate.clearForTest()
    }

    @Test
    fun everySheetAdUnitBelongsToAPlacementAndHasAGoogleTestId() {
        assertTrue(AdFormat.entries.all { it.testId.startsWith("ca-app-pub-3940256099942544/") })
        assertEquals(AdUnit.entries.size, AdUnit.entries.map { it.name }.toSet().size)
        assertTrue(NativePlacement.entries.all { it.unit.format == AdFormat.NATIVE })
        assertTrue(InterstitialPlacement.entries.all { it.interUnit.format == AdFormat.INTERSTITIAL && it.interUnit.key == it.adKey })
    }

    @Test
    fun fullScreenGateRejectsOverlapAndOnlyOwnerCanRelease() {
        val first = Any()
        val second = Any()
        assertTrue(FullScreenGate.acquire(first))
        assertFalse(FullScreenGate.acquire(second))
        FullScreenGate.release(second)
        assertTrue(FullScreenGate.isBusy())
        FullScreenGate.release(first)
        assertFalse(FullScreenGate.isBusy())
        assertTrue(FullScreenGate.acquire(second))
    }

    @Test
    fun rewardIsDeliveredExactlyOnce() {
        val delivered = mutableListOf<AdReward>()
        val once = RewardOnce(delivered::add)
        once.send(AdReward("coin", 5))
        once.send(AdReward("coin", 5))
        assertEquals(listOf(AdReward("coin", 5)), delivered)
    }

    @Test
    fun zeroGapAlwaysAllowsShowing() {
        AdGapGate.recordInterstitialShown(now = 1_000)
        assertTrue(AdGapGate.canShowInterstitial(interInterGapMs = 0, interOpenGapMs = 0, now = 1_000))
        AdGapGate.recordAppOpenShown(now = 1_000)
        assertTrue(AdGapGate.canShowAppOpen(openOpenGapMs = 0, interOpenGapMs = 0, now = 1_000))
    }

    @Test
    fun interInterGapBlocksUntilElapsed() {
        AdGapGate.recordInterstitialShown(now = 1_000)
        assertFalse(AdGapGate.canShowInterstitial(interInterGapMs = 5_000, interOpenGapMs = 0, now = 4_000))
        assertTrue(AdGapGate.canShowInterstitial(interInterGapMs = 5_000, interOpenGapMs = 0, now = 6_000))
    }

    @Test
    fun openOpenGapBlocksUntilElapsed() {
        AdGapGate.recordAppOpenShown(now = 1_000)
        assertFalse(AdGapGate.canShowAppOpen(openOpenGapMs = 5_000, interOpenGapMs = 0, now = 4_000))
        assertTrue(AdGapGate.canShowAppOpen(openOpenGapMs = 5_000, interOpenGapMs = 0, now = 6_000))
    }

    @Test
    fun interOpenGapBlocksBothCrossDirections() {
        AdGapGate.recordInterstitialShown(now = 1_000)
        assertFalse(AdGapGate.canShowAppOpen(openOpenGapMs = 0, interOpenGapMs = 5_000, now = 4_000))
        assertTrue(AdGapGate.canShowAppOpen(openOpenGapMs = 0, interOpenGapMs = 5_000, now = 6_000))

        AdGapGate.clearForTest()
        AdGapGate.recordAppOpenShown(now = 1_000)
        assertFalse(AdGapGate.canShowInterstitial(interInterGapMs = 0, interOpenGapMs = 5_000, now = 4_000))
        assertTrue(AdGapGate.canShowInterstitial(interInterGapMs = 0, interOpenGapMs = 5_000, now = 6_000))
    }
}
