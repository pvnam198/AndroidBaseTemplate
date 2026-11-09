package com.namstd.androidskillhub.core.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import com.namstd.androidskillhub.core.config.AdKey

/**
 * Loại quảng cáo thưởng = cột `type` của placement trên sheet (không có `format`):
 * - [REWARD]: user tự bấm xem (nút "Xem quảng cáo để nhận…"), xem hết mới nhận thưởng.
 * - [REWARD_INTER]: tự hiện lúc chuyển màn như inter, trước đó có màn đếm ngược cho user bỏ qua (bắt buộc theo
 *   chính sách rewarded interstitial của AdMob).
 */
enum class RewardKind { REWARD, REWARD_INTER }

/** Kết quả một lần gọi quảng cáo thưởng. */
enum class RewardResult {
    /** Xem đủ, user đã nhận thưởng. */
    REWARDED,

    /** User đóng quảng cáo trước khi đủ, hoặc bấm Bỏ qua ở màn đếm ngược của reward_inter. */
    SKIPPED,

    /**
     * Không hiện được: placement tắt, quảng cáo bị tắt (Premium, `ads_profile` 0), chưa tải xong / không có fill, hoặc
     * đang có một quảng cáo toàn màn hình khác. App tự quyết: vẫn cho thưởng (vd. user Premium) hay báo thử lại sau.
     */
    UNAVAILABLE,
}

/**
 * Chỗ gọi quảng cáo thưởng trong app - mỗi cái một placement (`ad_placements`) + một ad unit (`ad_ids`). Base chỉ có
 * hai placement mẫu; app con thêm placement theo màn của mình: thêm [AdKey], [AdUnit] (format
 * [AdFormat.REWARDED] / [AdFormat.REWARDED_INTERSTITIAL]) và một dòng ở đây.
 */
enum class RewardPlacement(val adKey: AdKey, val unit: AdUnit) {
    REWARD_SAMPLE(AdKey.REWARD_SAMPLE, AdUnit.REWARD_SAMPLE),
    REWARD_INTER_SAMPLE(AdKey.REWARD_INTER_SAMPLE, AdUnit.REWARD_INTER_SAMPLE),
    ;

    val kind: RewardKind
        get() = if (unit.format == AdFormat.REWARDED_INTERSTITIAL) RewardKind.REWARD_INTER else RewardKind.REWARD
}

/**
 * Tải / hiện quảng cáo thưởng, mỗi ad unit giữ một ad. Màn hình gọi qua `BaseActivity.showReward` (lo luôn màn đếm
 * ngược của reward_inter), không gọi trực tiếp object này.
 */
object RewardAds {
    /** Một ad đã tải - rewarded hoặc rewarded interstitial, hai SDK class không có chung kiểu. */
    private sealed class Loaded {
        abstract fun show(activity: Activity, onEarned: () -> Unit, onClosed: () -> Unit)
        abstract fun destroy()

        class Rewarded(val ad: RewardedAd) : Loaded() {
            override fun show(activity: Activity, onEarned: () -> Unit, onClosed: () -> Unit) {
                ad.adEventCallback = object : RewardedAdEventCallback {
                    override fun onAdDismissedFullScreenContent() = runOnMainThread(onClosed)
                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                        runOnMainThread(onClosed)
                    override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
                }
                ad.show(activity) { runOnMainThread(onEarned) }
            }

            override fun destroy() = ad.destroy()
        }

        class RewardedInter(val ad: RewardedInterstitialAd) : Loaded() {
            override fun show(activity: Activity, onEarned: () -> Unit, onClosed: () -> Unit) {
                ad.adEventCallback = object : RewardedInterstitialAdEventCallback {
                    override fun onAdDismissedFullScreenContent() = runOnMainThread(onClosed)
                    override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                        runOnMainThread(onClosed)
                    override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
                }
                ad.show(activity) { runOnMainThread(onEarned) }
            }

            override fun destroy() = ad.destroy()
        }
    }

    private class Slot {
        var ad: Loaded? = null
        var loading = false
    }

    private val slots = mutableMapOf<AdUnit, Slot>()

    @Synchronized
    private fun slotFor(unit: AdUnit) = slots.getOrPut(unit) { Slot() }

    /** Placement có được chạy không: quảng cáo bật (không Premium, profile khác OFF) và `enable` của placement. */
    fun isEnabled(placement: RewardPlacement): Boolean =
        Ads.adsEnabled && AdPolicy.profile != AdProfile.OFF && AdPolicy.config(placement.adKey).enable

    fun isReady(placement: RewardPlacement): Boolean = slotFor(placement.unit).ad != null

    /** Tải trước để lúc user bấm là có ngay. Gọi lại nhiều lần không sao. */
    fun preload(placement: RewardPlacement) {
        if (!isEnabled(placement)) return
        load(placement.unit)
    }

    private fun load(unit: AdUnit) {
        val slot = slotFor(unit)
        if (!Ads.isReady || slot.loading || slot.ad != null) return
        slot.loading = true
        val onLoaded = { ad: Loaded -> slot.loading = false; slot.ad = ad }
        val onFailed = { _: String -> slot.loading = false }
        if (unit.format == AdFormat.REWARDED_INTERSTITIAL) {
            WaterfallLoader<RewardedInterstitialAd>(unit.ids()).load(
                attempt = { id, loaded, failed ->
                    RewardedInterstitialAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<RewardedInterstitialAd> {
                        override fun onAdLoaded(ad: RewardedInterstitialAd) = loaded(ad)
                        override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                    })
                },
                onLoaded = { ad, _ -> onLoaded(Loaded.RewardedInter(ad)) },
                onFailed = onFailed,
            )
        } else {
            WaterfallLoader<RewardedAd>(unit.ids()).load(
                attempt = { id, loaded, failed ->
                    RewardedAd.load(AdRequest.Builder(id).build(), object : AdLoadCallback<RewardedAd> {
                        override fun onAdLoaded(ad: RewardedAd) = loaded(ad)
                        override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                    })
                },
                onLoaded = { ad, _ -> onLoaded(Loaded.Rewarded(ad)) },
                onFailed = onFailed,
            )
        }
    }

    /**
     * Hiện ad đã tải của [placement] ngay (màn đếm ngược của reward_inter do `BaseActivity.showReward` lo trước đó).
     * [onResult] chạy đúng một lần, sau khi quảng cáo đóng. Xong thì tải sẵn ad kế tiếp.
     */
    fun show(activity: Activity, placement: RewardPlacement, onResult: (RewardResult) -> Unit) {
        val slot = slotFor(placement.unit)
        val current = slot.ad
        if (!isEnabled(placement) || current == null || !FullScreenGate.acquire(current)) {
            if (current == null) preload(placement)
            onResult(RewardResult.UNAVAILABLE)
            return
        }
        slot.ad = null
        var earned = false
        var done = false
        current.show(
            activity,
            onEarned = { earned = true },
            onClosed = {
                if (!done) {
                    done = true
                    FullScreenGate.release(current)
                    current.destroy()
                    onResult(if (earned) RewardResult.REWARDED else RewardResult.SKIPPED)
                    preload(placement)
                }
            },
        )
    }

    fun clear() {
        slots.values.forEach { it.ad?.destroy(); it.ad = null; it.loading = false }
    }
}
