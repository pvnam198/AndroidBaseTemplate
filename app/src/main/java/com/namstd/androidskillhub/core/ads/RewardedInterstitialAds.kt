package com.namstd.androidskillhub.core.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback

object RewardedInterstitialAds {
    private var ad: RewardedInterstitialAd? = null
    private var loading = false

    fun load(onStatus: (String) -> Unit = {}) {
        if (!Ads.isReady || loading || ad != null) return
        loading = true
        WaterfallLoader<RewardedInterstitialAd>(listOf(TEST_REWARDED_INTERSTITIAL)).load(
            attempt = { id, loaded, failed ->
                RewardedInterstitialAd.load(
                    AdRequest.Builder(id).build(),
                    object : AdLoadCallback<RewardedInterstitialAd> {
                        override fun onAdLoaded(ad: RewardedInterstitialAd) = loaded(ad)
                        override fun onAdFailedToLoad(adError: LoadAdError) =
                            failed(adError.message)
                    },
                )
            },
            onLoaded = { loadedAd, _ ->
                loading = false; ad = loadedAd; onStatus("Rewarded interstitial: ready")
            },
            onFailed = { loading = false; onStatus("Rewarded interstitial: $it") },
        )
    }

    fun show(
        activity: Activity,
        onReward: (AdReward) -> Unit,
        onStatus: (String) -> Unit = {}
    ): Boolean {
        val current =
            ad ?: run { onStatus("Rewarded interstitial: not ready"); load(onStatus); return false }
        if (!FullScreenGate.acquire(current)) {
            onStatus("Another full-screen item is showing"); return false
        }
        ad = null
        val rewardOnce = RewardOnce(onReward)
        current.adEventCallback = object : RewardedInterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() = runOnMainThread { finish(current, onStatus) }
            override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                runOnMainThread { finish(current, onStatus, fullScreenContentError.message) }
            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
        }
        current.show(activity) { item ->
            runOnMainThread { rewardOnce.send(AdReward(item.type, item.amount.toLong())) }
        }
        return true
    }

    private fun finish(
        shown: RewardedInterstitialAd,
        status: (String) -> Unit,
        error: String? = null
    ) {
        FullScreenGate.release(shown)
        shown.destroy()
        status(error?.let { "Rewarded interstitial: $it" } ?: "Rewarded interstitial: dismissed")
        load(status)
    }

    fun clear() {
        ad?.destroy()
        ad = null
        loading = false
    }
}
