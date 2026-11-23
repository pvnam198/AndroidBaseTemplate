package com.namstd.androidskillhub.core.ads

import android.app.Activity
import android.util.Log
import com.namstd.androidskillhub.BuildConfig
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

/**
 * App Open ad: [AdUnit.AOA_SPLASH] cho màn Splash (`ads_splash` ad_type aoa) và [AdUnit.ON_RESUME] cho lúc quay lại app
 * (`on_resume` ad_type aoa). Cache theo ID dùng chung ([FullScreenAdCache]): hai unit cùng ID thì dùng chung ad chờ.
 */
object AppOpenAds {
    private val cache = FullScreenAdCache<AppOpenAd>("AdsAppOpen", TTL_MS, destroy = AppOpenAd::destroy) { _, id, loaded, failed ->
        AppOpenAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<AppOpenAd> {
            override fun onAdLoaded(ad: AppOpenAd) = loaded(ad)
            override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
        })
    }

    fun load(unit: AdUnit) {
        if (!Ads.isReady) return
        cache.load(unit)
    }

    /** Quay lại app với `on_resume` ad_type aoa: hiện App Open của [AdUnit.ON_RESUME] nếu đã có sẵn, không thì load cho lần sau. */
    fun onReturnToForeground(activity: Activity) {
        if (!Ads.adsEnabled) return
        val unit = AdUnit.ON_RESUME
        if (!cache.isReady(unit)) { load(unit); return }
        val gaps = AdPolicy.openGaps
        if (!AdGapGate.canShowAppOpen(gaps.openGapMs, gaps.openInterGapMs)) {
            if (BuildConfig.DEBUG) Log.d("AdsGap", "on_resume App Open: bỏ qua, chưa đủ khoảng cách $gaps")
            return
        }
        if (FullScreenGate.isBusy()) return
        val current = cache.take(unit) ?: return
        if (!FullScreenGate.acquire(current)) return
        if (BuildConfig.DEBUG) Log.d("AdsGap", "on_resume App Open: hiện")
        current.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() = runOnMainThread { finish(unit, current) }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finish(unit, current) }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity)
    }

    /** Đã có App Open ad hợp lệ của [unit] được load và sẵn sàng hiển thị ngay hay chưa. */
    fun isLoaded(unit: AdUnit): Boolean = cache.isReady(unit)

    /**
     * Hiển thị App Open ad đã load của [unit] (cho splash lần mở đầu, `ads_splash` ad_type aoa), rồi chạy
     * [onComplete]. Cố ý bỏ qua [AdGapGate] - splash là lần mở đầu, chưa có full-screen ad nào trước đó.
     * Trả về false (không hiện ad) nếu ads tắt, chưa có ad sẵn sàng, hoặc đang có một mục full-screen khác.
     */
    fun show(activity: Activity, unit: AdUnit, onComplete: () -> Unit): Boolean {
        if (!Ads.adsEnabled || !cache.isReady(unit) || FullScreenGate.isBusy()) return false
        val current = cache.take(unit) ?: return false
        if (!FullScreenGate.acquire(current)) return false
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

    fun clear() = cache.clear()

    private const val TTL_MS = 4L * 60 * 60 * 1000
}
