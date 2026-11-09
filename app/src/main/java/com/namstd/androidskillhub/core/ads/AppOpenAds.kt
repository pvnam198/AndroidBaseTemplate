package com.namstd.androidskillhub.core.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * App Open ad, cache một ad cho mỗi ad unit: [AdUnit.AOA_SPLASH] cho màn Splash (`ads_splash` = 1) và
 * [AdUnit.ON_RESUME] cho lúc quay lại app (`on_resume` = 1).
 */
object AppOpenAds {
    private class Slot {
        var ad: AppOpenAd? = null
        var loadedAt = 0L
        var loading = false

        fun isValid() = ad != null && System.currentTimeMillis() - loadedAt < TTL_MS
    }

    private val slots = mutableMapOf<AdUnit, Slot>()

    @Synchronized
    private fun slotFor(unit: AdUnit) = slots.getOrPut(unit) { Slot() }

    fun load(unit: AdUnit) {
        val slot = slotFor(unit)
        if (!Ads.isReady || slot.loading || slot.isValid()) return
        slot.ad?.destroy()
        slot.ad = null
        slot.loading = true
        WaterfallLoader<AppOpenAd>(unit.ids()).load(
            attempt = { id, loaded, failed ->
                AppOpenAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) = loaded(ad)
                    override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                })
            },
            onLoaded = { loadedAd, _ -> slot.loading = false; slot.ad = loadedAd; slot.loadedAt = System.currentTimeMillis() },
            onFailed = { slot.loading = false },
        )
    }

    /** Quay lại app với `on_resume` = 1: hiện App Open của [AdUnit.ON_RESUME] nếu đã có sẵn, không thì load cho lần sau. */
    fun onReturnToForeground(activity: Activity) {
        if (!Ads.adsEnabled) return
        val unit = AdUnit.ON_RESUME
        val slot = slotFor(unit)
        if (!slot.isValid()) { slot.ad?.destroy(); slot.ad = null; load(unit); return }
        val gaps = AdPolicy.openGaps
        if (!AdGapGate.canShowAppOpen(gaps.openGapMs, gaps.openInterGapMs)) return
        val current = slot.ad ?: return
        if (!FullScreenGate.acquire(current)) return
        slot.ad = null
        current.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() = runOnMainThread { finish(unit, current) }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finish(unit, current) }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity)
    }

    /** Đã có App Open ad hợp lệ của [unit] được load và sẵn sàng hiển thị ngay hay chưa. */
    fun isLoaded(unit: AdUnit): Boolean = slotFor(unit).isValid()

    /**
     * Hiển thị App Open ad đã load của [unit] (cho splash lần mở đầu, qua `ads_splash` = 1), rồi chạy
     * [onComplete]. Cố ý bỏ qua [AdGapGate] - splash là lần mở đầu, chưa có full-screen ad nào trước đó.
     * Trả về false (không hiện ad) nếu ads tắt, chưa có ad sẵn sàng, hoặc đang có một mục full-screen khác.
     */
    fun show(activity: Activity, unit: AdUnit, onComplete: () -> Unit): Boolean {
        val slot = slotFor(unit)
        if (!Ads.adsEnabled || !slot.isValid()) return false
        val current = slot.ad ?: return false
        if (!FullScreenGate.acquire(current)) return false
        slot.ad = null
        current.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() = runOnMainThread { finish(unit, current); onComplete() }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finish(unit, current); onComplete() }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity)
        return true
    }

    private fun finish(unit: AdUnit, shown: AppOpenAd) {
        FullScreenGate.release(shown)
        AdGapGate.recordAppOpenShown()
        shown.destroy()
        // Chỉ nạp lại App Open của lúc quay lại app; AOA Splash chỉ dùng một lần mỗi lần mở app.
        if (unit == AdUnit.ON_RESUME) load(unit)
    }

    fun clear() {
        slots.values.forEach { it.ad?.destroy(); it.ad = null; it.loading = false }
    }

    private const val TTL_MS = 4L * 60 * 60 * 1000
}
