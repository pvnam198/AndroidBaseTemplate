package com.namstd.androidskillhub.core.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.namstd.androidskillhub.core.config.RemoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class InterstitialState { IDLE, LOADING, READY, FAILED }

/**
 * Các điểm bung interstitial có tên, để đối chiếu với danh sách `ignore` trong
 * [AdPolicy.interDelay] (inter nằm trong ignore được miễn gap giữa các inter). [key] khớp token trên config.
 */
enum class InterstitialPlacement(val key: String) {
    SPLASH("ads_splash"),
    LANGUAGE("inter_language"),
    INTRO("inter_intro"),

    /** Inter ở các màn in-app (sau onboarding) - `enable.inter_inapp`, và là inter của [AdProfile.BASIC]. */
    IN_APP("inter_inapp"),
}

object InterstitialAds {
    private val mutableState = MutableStateFlow(InterstitialState.IDLE)
    val state: StateFlow<InterstitialState> = mutableState

    private var ad: InterstitialAd? = null
    private var loading = false

    fun load(onStatus: (String) -> Unit = {}) {
        if (!Ads.isReady || !Ads.adsEnabled || loading || ad != null) return
        loading = true
        mutableState.value = InterstitialState.LOADING
        WaterfallLoader<InterstitialAd>(Ads.ids.interstitial).load(
            attempt = { id, loaded, failed ->
                InterstitialAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) = loaded(ad)
                    override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                })
            },
            onLoaded = { loadedAd, _ ->
                loading = false
                ad = loadedAd
                mutableState.value = InterstitialState.READY
                onStatus("Interstitial: ready")
            },
            onFailed = {
                loading = false
                mutableState.value = InterstitialState.FAILED
                onStatus("Interstitial: $it")
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
                RemoteConfig.interOpenGapMs,
                skipInterGap = placement.key in delay.ignore,
            )
        ) {
            onStatus("Interstitial: gap not elapsed")
            return false
        }
        val current = ad ?: run { onStatus("Interstitial: not ready"); load(onStatus); return false }
        if (!FullScreenGate.acquire(current)) { onStatus("Another full-screen item is showing"); return false }
        ad = null
        mutableState.value = InterstitialState.IDLE
        current.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() =
                runOnMainThread { finish(current, onStatus, onComplete = onComplete) }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finish(current, onStatus, fullScreenContentError.message, onComplete) }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity)
        return true
    }

    private fun finish(shown: InterstitialAd, status: (String) -> Unit, error: String? = null, onComplete: () -> Unit = {}) {
        FullScreenGate.release(shown)
        AdGapGate.recordInterstitialShown()
        shown.destroy()
        status(error?.let { "Interstitial: $it" } ?: "Interstitial: dismissed")
        onComplete()
        load(status)
    }

    fun clear() {
        ad?.destroy()
        ad = null
        loading = false
        mutableState.value = InterstitialState.IDLE
    }
}
