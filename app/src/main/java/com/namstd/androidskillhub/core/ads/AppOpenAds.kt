package com.namstd.androidskillhub.core.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.namstd.androidskillhub.core.config.RemoteConfig

object AppOpenAds {
    private var ad: AppOpenAd? = null
    private var loadedAt = 0L
    private var loading = false

    fun load() {
        if (!Ads.isReady || loading || isValid()) return
        ad?.destroy()
        ad = null
        loading = true
        WaterfallLoader<AppOpenAd>(Ads.ids.appOpen).load(
            attempt = { id, loaded, failed ->
                AppOpenAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<AppOpenAd> {
                    override fun onAdLoaded(ad: AppOpenAd) = loaded(ad)
                    override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                })
            },
            onLoaded = { loadedAd, _ -> loading = false; ad = loadedAd; loadedAt = System.currentTimeMillis() },
            onFailed = { loading = false },
        )
    }

    fun onReturnToForeground(activity: Activity) {
        if (!Ads.adsEnabled) return
        if (!isValid()) { ad?.destroy(); ad = null; load(); return }
        if (!AdGapGate.canShowAppOpen(RemoteConfig.openOpenGapMs, RemoteConfig.interOpenGapMs)) return
        val current = ad ?: return
        if (!FullScreenGate.acquire(current)) return
        ad = null
        current.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() = runOnMainThread { finish(current) }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finish(current) }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity)
    }

    /** Đã có App Open ad hợp lệ được load và sẵn sàng hiển thị ngay hay chưa. */
    fun isLoaded(): Boolean = isValid()

    /**
     * Hiển thị App Open ad đã load (cho splash lần mở đầu, qua `enable.ads_splash=1`), rồi chạy
     * [onComplete]. Cố ý bỏ qua [AdGapGate] - splash là lần mở đầu, chưa có full-screen ad nào trước đó.
     * Trả về false (không hiện ad) nếu ads tắt, chưa có ad sẵn sàng, hoặc đang có một mục full-screen khác.
     */
    fun show(activity: Activity, onComplete: () -> Unit): Boolean {
        if (!Ads.adsEnabled || !isValid()) return false
        val current = ad ?: return false
        if (!FullScreenGate.acquire(current)) return false
        ad = null
        current.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() = runOnMainThread { finishShow(current, onComplete) }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finishShow(current, onComplete) }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity)
        return true
    }

    private fun finishShow(shown: AppOpenAd, onComplete: () -> Unit) {
        finish(shown)
        onComplete()
    }

    private fun finish(shown: AppOpenAd) {
        FullScreenGate.release(shown)
        AdGapGate.recordAppOpenShown()
        shown.destroy()
        load()
    }

    private fun isValid(): Boolean = ad != null && System.currentTimeMillis() - loadedAt < TTL_MS

    fun clear() {
        ad?.destroy()
        ad = null
        loading = false
    }

    private const val TTL_MS = 4L * 60 * 60 * 1000
}
