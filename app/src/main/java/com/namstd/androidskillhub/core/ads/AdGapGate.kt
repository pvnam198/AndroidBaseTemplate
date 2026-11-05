package com.namstd.androidskillhub.core.ads

/** Thời gian chờ toàn app giữa các lần hiện full-screen ad (Interstitial / App Open). */
object AdGapGate {
    private var lastInterstitialShownAt = 0L
    private var lastAppOpenShownAt = 0L

    fun canShowInterstitial(
        interInterGapMs: Long,
        interOpenGapMs: Long,
        skipInterGap: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): Boolean =
        (skipInterGap || now - lastInterstitialShownAt >= interInterGapMs) &&
            now - lastAppOpenShownAt >= interOpenGapMs

    fun canShowAppOpen(
        openOpenGapMs: Long,
        interOpenGapMs: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean =
        now - lastAppOpenShownAt >= openOpenGapMs &&
            now - lastInterstitialShownAt >= interOpenGapMs

    fun recordInterstitialShown(now: Long = System.currentTimeMillis()) {
        lastInterstitialShownAt = now
    }

    fun recordAppOpenShown(now: Long = System.currentTimeMillis()) {
        lastAppOpenShownAt = now
    }

    internal fun clearForTest() {
        lastInterstitialShownAt = 0L
        lastAppOpenShownAt = 0L
    }
}
