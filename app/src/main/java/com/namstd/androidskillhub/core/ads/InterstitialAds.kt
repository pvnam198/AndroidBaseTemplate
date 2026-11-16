package com.namstd.androidskillhub.core.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.namstd.androidskillhub.core.config.AdKey

/**
 * Các điểm bung interstitial, mỗi điểm là một placement: [adKey] là key cấu hình (`ad_type`, `step`, `wait_ms`…),
 * [interUnit] là ad unit interstitial, [nativePlacement] là native toàn màn hình hiện sau inter. [key] khớp tên trong
 * `inter_gap_ignore` và giá trị của `splash_fallback`.
 */
enum class InterstitialPlacement(val adKey: AdKey, val interUnit: AdUnit, val nativePlacement: NativePlacement) {
    SPLASH(AdKey.ADS_SPLASH, AdUnit.INTER_SPLASH, NativePlacement.AFTER_INTER_SPLASH),
    LANGUAGE(AdKey.INTER_LANGUAGE, AdUnit.INTER_LANGUAGE, NativePlacement.AFTER_INTER_LANGUAGE),
    /** Khi xong Onboarding (`inter_after_onboarding`). */
    ONBOARDING(AdKey.INTER_AFTER_ONBOARDING, AdUnit.INTER_AFTER_ONBOARDING, NativePlacement.AFTER_INTER_ONBOARDING),

    /** Inter ở màn chính (app mẫu: bấm Cài đặt) - key `inter_home`, cũng là inter của bộ basic mẫu. */
    HOME(AdKey.INTER_HOME, AdUnit.INTER_HOME, NativePlacement.AFTER_INTER_HOME),

    /** Inter khi quay lại app từ nền (`on_resume` ad_type inter / native_as_inter / inter_then_native) - xem App. */
    RESUME(AdKey.ON_RESUME, AdUnit.INTER_ON_RESUME, NativePlacement.AFTER_INTER_RESUME);

    val key: String get() = adKey.key
}

/** Interstitial, cache một ad cho mỗi ad unit (vì mỗi unit có ID riêng). */
object InterstitialAds {
    private class Slot {
        var ad: InterstitialAd? = null
        var loading = false
    }

    private val slots = mutableMapOf<AdUnit, Slot>()

    @Synchronized
    private fun slotFor(unit: AdUnit) = slots.getOrPut(unit) { Slot() }

    /** Đã có ad của [unit] nằm sẵn để hiện ngay chưa. */
    fun isReady(unit: AdUnit): Boolean = slotFor(unit).ad != null

    /**
     * Tải sẵn interstitial cho [placement] nếu `ad_type` của vị trí đó cần inter (inter / inter_then_native) - gọi khi màn có vị
     * trí này mở ra, để lúc cần hiện thì ad đã nằm sẵn (mỗi unit có ô cache riêng, không dùng chung với vị trí khác).
     */
    fun preload(placement: InterstitialPlacement) {
        val plan = fullScreenPlan(placement)
        if (plan == FullScreenAdPlan.INTERSTITIAL || plan == FullScreenAdPlan.INTER_PLUS_NATIVE) load(placement.interUnit)
    }

    fun load(unit: AdUnit, onStatus: (String) -> Unit = {}) {
        val slot = slotFor(unit)
        if (!Ads.isReady || !Ads.adsEnabled || slot.loading || slot.ad != null) return
        slot.loading = true
        WaterfallLoader<InterstitialAd>(unit.ids()).load(
            attempt = { id, loaded, failed ->
                InterstitialAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) = loaded(ad)
                    override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                })
            },
            onLoaded = { loadedAd, _ ->
                slot.loading = false
                slot.ad = loadedAd
                onStatus("Interstitial ${unit.name}: ready")
            },
            onFailed = {
                slot.loading = false
                onStatus("Interstitial ${unit.name}: $it")
            },
        )
    }

    fun show(
        activity: Activity,
        placement: InterstitialPlacement,
        ignoreGap: Boolean = false,
        onStatus: (String) -> Unit = {},
        onComplete: () -> Unit = {},
    ): Boolean {
        if (!Ads.adsEnabled) return false
        val delay = AdPolicy.interDelay
        if (!ignoreGap && !AdGapGate.canShowInterstitial(
                delay.gapMs,
                interOpenGapMs = AdPolicy.openGaps.openInterGapMs,
                skipInterGap = placement.key in delay.ignore,
            )
        ) {
            onStatus("Interstitial: gap not elapsed")
            return false
        }
        val unit = placement.interUnit
        val slot = slotFor(unit)
        val current = slot.ad ?: run { onStatus("Interstitial: not ready"); load(unit, onStatus); return false }
        if (!FullScreenGate.acquire(current)) { onStatus("Another full-screen item is showing"); return false }
        slot.ad = null
        current.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() =
                runOnMainThread { finish(unit, current, onStatus, onComplete = onComplete) }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finish(unit, current, onStatus, fullScreenContentError.message, onComplete) }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity)
        return true
    }

    private fun finish(
        unit: AdUnit,
        shown: InterstitialAd,
        status: (String) -> Unit,
        error: String? = null,
        onComplete: () -> Unit = {},
    ) {
        FullScreenGate.release(shown)
        AdGapGate.recordInterstitialShown()
        shown.destroy()
        status(error?.let { "Interstitial: $it" } ?: "Interstitial: dismissed")
        onComplete()
        load(unit, status)
    }

    fun clear() {
        slots.values.forEach { it.ad?.destroy(); it.ad = null; it.loading = false }
    }
}
