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

    /**
     * Quảng cáo khi bấm Tiếp tục ở màn Welcome Back (`on_resume` ad_type khác aoa), theo `ad_type` của on_resume: inter /
     * native toàn màn (native, native_as_inter) / inter rồi native (inter_then_native) - xem WelcomeBackActivity.
     */
    RESUME(AdKey.ON_RESUME, AdUnit.INTER_ON_RESUME, NativePlacement.AFTER_INTER_RESUME);

    val key: String get() = adKey.key
}

/** Interstitial, cache theo ID dùng chung giữa các placement ([FullScreenAdCache]). */
object InterstitialAds {
    private val cache = FullScreenAdCache<InterstitialAd>("AdsInter", TTL_MS, destroy = InterstitialAd::destroy) { _, id, loaded, failed ->
        InterstitialAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(ad: InterstitialAd) = loaded(ad)
            override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
        })
    }

    /** Đã có ad (của một ID bất kỳ trong `ad_ids.inter` của [unit]) nằm sẵn để hiện ngay chưa. */
    fun isReady(unit: AdUnit): Boolean = cache.isReady(unit)

    /**
     * Tải sẵn interstitial cho [placement] nếu `ad_type` của vị trí đó cần inter (inter / inter_then_native) - gọi khi
     * màn có vị trí này mở ra, để lúc cần hiện thì ad đã nằm sẵn (cache theo ID: vị trí khác cùng ID cũng dùng được).
     */
    fun preload(placement: InterstitialPlacement) {
        val plan = fullScreenPlan(placement)
        if (plan == FullScreenAdPlan.INTERSTITIAL || plan == FullScreenAdPlan.INTER_PLUS_NATIVE) load(placement.interUnit)
    }

    fun load(unit: AdUnit, onStatus: (String) -> Unit = {}) {
        if (!Ads.isReady || !Ads.adsEnabled) return
        cache.load(unit, onStatus)
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
        if (!cache.isReady(unit)) { onStatus("Interstitial: not ready"); load(unit, onStatus); return false }
        // Kiểm tra trước khi lấy ad ra khỏi cache, để ad không bị mất khi đang có mục full-screen khác.
        if (FullScreenGate.isBusy()) { onStatus("Another full-screen item is showing"); return false }
        val current = cache.take(unit) ?: return false
        if (!FullScreenGate.acquire(current)) { onStatus("Another full-screen item is showing"); return false }
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

    fun clear() = cache.clear()

    /** Theo khuyến nghị của AdMob: ad tải quá 1 giờ thì bỏ. */
    private const val TTL_MS = 60L * 60 * 1000
}
