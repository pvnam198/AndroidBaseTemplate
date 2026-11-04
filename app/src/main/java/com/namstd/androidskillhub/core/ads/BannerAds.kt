package com.namstd.androidskillhub.core.ads

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.databinding.AdBannerShimmerBinding

object BannerAds {
    /**
     * Loads a banner into [container]. Loại do [AdPolicy.enable] (`banner`) quyết định:
     * `0` = tắt (ẩn ô), `1` = AdMob banner thường, `2` = AdMob collapsible, `3` = native thường
     * (làm thật ở Phase 2, tạm fallback banner thường), `4` = native collapsible ([NativeCollapseAd]:
     * 1 native ad to rồi thu nhỏ thành dải). Pure routing - load thật ở [loadAdMobBanner]/[NativeCollapseAd].
     *
     * [autoReload] / [reloadGapMs] set the slot's reload timer (off, or 0, for none) - supplied by the
     * caller, never read from config here; the returned
     * handle's [BannerSlotHandle.reload] reloads it on demand, e.g. on a tab switch - see [BannerSlotReloader].
     */
    fun load(
        activity: Activity,
        container: ViewGroup,
        autoReload: Boolean,
        reloadGapMs: Long,
        onStatus: (String) -> Unit = {},
    ): BannerSlotHandle = when (AdPolicy.enable.banner) {
        0 -> {
            container.visibility = View.GONE
            BannerSlotHandle.NONE
        }
        4 -> NativeCollapseAd(activity, container, autoReload = autoReload, reloadGapMs = reloadGapMs, onStatus = onStatus).start()
        3 -> NativeBannerSlot(activity, container as FrameLayout).start()
        2 -> loadAdMobBanner(activity, container, collapsible = true, autoReload, reloadGapMs, onStatus)
        else -> loadAdMobBanner(activity, container, collapsible = false, autoReload, reloadGapMs, onStatus)
    }

    /** Loads a real AdMob banner into [container] - plain, or AdMob's own collapsible variant when [collapsible]. */
    private fun loadAdMobBanner(
        activity: Activity,
        container: ViewGroup,
        collapsible: Boolean,
        autoReload: Boolean,
        reloadGapMs: Long,
        onStatus: (String) -> Unit,
    ): BannerSlotHandle {
        if (!Ads.isReady || !Ads.adsEnabled) {
            onStatus("Banner: SDK not ready")
            container.visibility = View.GONE
            return BannerSlotHandle.NONE
        }
        return AdMobBannerAd(activity, container, collapsible, autoReload, reloadGapMs, onStatus).also { it.load(isReload = false) }
    }
}

/**
 * One AdMob banner slot in [container]. Each load - the first one and every auto reload (see
 * [BannerSlotReloader]) - drops the previous banner and puts the slot back to its small shimmer
 * straight away, then swaps in the new banner once it's loaded; for [collapsible] that new banner
 * is requested collapsible again, so it opens expanded. On a reload the shimmer stays up at least
 * [BannerSlotReloader.MIN_RELOAD_SHIMMER_MS], so the swap never looks like a flicker. A banner loaded
 * while the activity isn't resumed waits for onResume, so the reload gap - which starts when the
 * banner is shown - only ever counts time it was actually on screen. A failed load hides [container]
 * until the next load puts its shimmer back. Main thread only, except the
 * SDK callbacks, which hop back via [Activity.runOnUiThread].
 */
private class AdMobBannerAd(
    private val activity: Activity,
    private val container: ViewGroup,
    private val collapsible: Boolean,
    autoReload: Boolean,
    reloadGapMs: Long,
    private val onStatus: (String) -> Unit,
) : BannerSlotHandle {
    private val handler = Handler(Looper.getMainLooper())
    private val reloader = BannerSlotReloader(activity, autoReload, reloadGapMs) { load(isReload = true) }
    private var current: AdView? = null
    /** A loaded banner waiting to be shown - for the reload's minimum shimmer, or for onResume. */
    private var pendingView: AdView? = null
    private var minShimmerUntil = 0L
    private val revealRunnable = Runnable { revealPending() }
    private val lifecycle: Lifecycle? = (activity as? LifecycleOwner)?.lifecycle
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            revealPending()
        }
    }
    private var shimmer: AdBannerShimmerBinding? = null
    /** Bumped on every load; a callback from an older load finds it changed and bows out. */
    @Volatile private var generation = 0
    @Volatile private var destroyed = false

    init {
        lifecycle?.addObserver(lifecycleObserver)
    }

    fun load(isReload: Boolean) {
        if (destroyed) return
        val gen = ++generation
        reloader.onLoadStarted()
        cancelPendingReveal()
        current?.destroy()
        current = null
        container.visibility = View.VISIBLE
        showShimmer()
        minShimmerUntil = if (isReload) SystemClock.elapsedRealtime() + BannerSlotReloader.MIN_RELOAD_SHIMMER_MS else 0L
        fun isStale() = destroyed || gen != generation

        val widthPixels = if (container.width > 0) container.width else activity.resources.displayMetrics.widthPixels
        val widthDp = (widthPixels / activity.resources.displayMetrics.density).toInt().coerceAtLeast(320)
        val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
        val ids = if (collapsible) Ads.ids.bannerCollapsible else Ads.ids.banner
        WaterfallLoader<AdView>(ids).load(
            attempt = { id, loaded, failed ->
                if (!isStale()) onStatus("Banner: loading $id")
                val adView = AdView(activity)
                val request = BannerAdRequest.Builder(id, size).apply {
                    if (collapsible) setGoogleExtrasBundle(Bundle().apply { putString("collapsible", "bottom") })
                }.build()
                adView.loadAd(
                    request,
                    object : AdLoadCallback<BannerAd> {
                        override fun onAdLoaded(ad: BannerAd) {
                            ad.adEventCallback = object : BannerAdEventCallback {
                                override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
                            }
                            loaded(adView)
                        }
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            adView.destroy()
                            if (!isStale()) failed(adError.message)
                        }
                    },
                )
            },
            onLoaded = { adView, _ ->
                activity.runOnUiThread {
                    if (isStale()) {
                        adView.destroy()
                        return@runOnUiThread
                    }
                    pendingView = adView
                    revealPending()
                }
            },
            onFailed = { error ->
                activity.runOnUiThread {
                    if (isStale()) return@runOnUiThread
                    onStatus("Banner: $error")
                    // No banner to show - free the slot instead of leaving an empty strip.
                    clearShimmer()
                    container.visibility = View.GONE
                    reloader.onFailed()
                }
            },
        )
    }

    /** Shows [pendingView] once the minimum shimmer is over and the activity is resumed. */
    private fun revealPending() {
        val adView = pendingView ?: return
        handler.removeCallbacks(revealRunnable)
        val wait = minShimmerUntil - SystemClock.elapsedRealtime()
        if (wait > 0) {
            handler.postDelayed(revealRunnable, wait)
            return
        }
        if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == false) return // onResume calls back in.
        pendingView = null
        show(adView)
    }

    /** The banner is on screen from here - so this, not the load, is where the reload gap starts. */
    private fun show(adView: AdView) {
        clearShimmer()
        current = adView
        container.removeAllViews()
        container.addView(adView)
        onStatus("Banner: ready")
        reloader.onShown()
    }

    override fun reload() = reloader.reloadNow()

    private fun cancelPendingReveal() {
        handler.removeCallbacks(revealRunnable)
        pendingView?.destroy()
        pendingView = null
    }

    /** Shows a shimmering skeleton in [container] so the banner slot never pops in from empty. */
    private fun showShimmer() {
        val binding = AdBannerShimmerBinding.inflate(LayoutInflater.from(activity))
        container.removeAllViews()
        container.addView(binding.root)
        binding.root.startShimmer()
        shimmer = binding
    }

    private fun clearShimmer() {
        shimmer?.let { it.root.stopShimmer(); container.removeView(it.root) }
        shimmer = null
    }

    override fun destroy() {
        destroyed = true
        activity.runOnUiThread {
            reloader.cancel()
            lifecycle?.removeObserver(lifecycleObserver)
            cancelPendingReveal()
            current?.destroy()
            current = null
            clearShimmer()
            container.removeAllViews()
        }
    }
}

/**
 * A native ad rendered into the banner slot (enable.banner=3). Uses [NativeAds.subscribe] (shimmer +
 * fade-in + no-fill hide handled there). [reload] re-subscribes a fresh native on demand (e.g. tab
 * switch); there is no built-in reload timer for this variant.
 */
private class NativeBannerSlot(
    private val activity: Activity,
    private val container: FrameLayout,
) : BannerSlotHandle {
    private var handle: AdHandle? = null

    fun start(): BannerSlotHandle {
        subscribe()
        return this
    }

    private fun subscribe() {
        handle = NativeAds.subscribe(activity, NativePlacement.BANNER_NATIVE, container, preloadOnShow = true)
    }

    override fun reload() {
        handle?.destroy()
        subscribe()
    }

    override fun destroy() {
        handle?.destroy()
        handle = null
    }
}
