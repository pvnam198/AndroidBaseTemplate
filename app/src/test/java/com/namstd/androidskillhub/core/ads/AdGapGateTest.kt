package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.OpenGapConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Khoảng cách quảng cáo quay lại app (App Open / màn Welcome Back) với mặc định bộ full của base: open_gap_ms = 30 s,
 * open_inter_gap_ms = 15 s; inter_gap_ms = 10 s. Đồng hồ giả (ms).
 */
class AdGapGateTest {
    private val gaps = OpenGapConfig(openGapMs = 30_000L, openInterGapMs = 15_000L)
    private val interGap = 10_000L

    private fun canOpen(now: Long) = AdGapGate.canShowAppOpen(gaps.openGapMs, gaps.openInterGapMs, now)
    private fun canInter(now: Long, skipInterGap: Boolean = false) =
        AdGapGate.canShowInterstitial(interGap, gaps.openInterGapMs, skipInterGap, now)

    @Before
    fun reset() = AdGapGate.clearForTest()

    @Test
    fun resumeAdWaitsOpenGapAfterPreviousResumeAd() {
        val t0 = 100_000L
        assertTrue(canOpen(t0))
        AdGapGate.recordAppOpenShown(t0)
        assertFalse(canOpen(t0 + 10_000))  // quay lại sau 10 s: chưa đủ 30 s
        assertFalse(canOpen(t0 + 29_999))
        assertTrue(canOpen(t0 + 30_000))   // đủ 30 s
    }

    @Test
    fun resumeAdWaitsOpenInterGapAfterInter() {
        val t0 = 100_000L
        AdGapGate.recordInterstitialShown(t0)
        assertFalse(canOpen(t0 + 3_000))   // inter vừa hiện 3 s trước
        assertTrue(canOpen(t0 + 15_000))
    }

    @Test
    fun interWaitsOpenInterGapAfterResumeAd() {
        val t0 = 100_000L
        AdGapGate.recordAppOpenShown(t0)
        assertFalse(canInter(t0 + 4_000))
        // inter_gap_ignore chỉ miễn khoảng cách inter ↔ inter, không miễn open_inter_gap_ms.
        assertFalse(canInter(t0 + 4_000, skipInterGap = true))
        assertTrue(canInter(t0 + 15_000))
    }

    @Test
    fun zeroGapsNeverBlock() {
        AdGapGate.recordAppOpenShown(100_000L)
        AdGapGate.recordInterstitialShown(100_000L)
        assertTrue(AdGapGate.canShowAppOpen(0L, 0L, 100_000L))
        assertTrue(AdGapGate.canShowInterstitial(0L, 0L, now = 100_000L))
    }
}
