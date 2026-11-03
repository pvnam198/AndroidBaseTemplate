package com.namstd.androidskillhub.core.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.template.NativeAdTemplate
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.databinding.AdNativeShimmerBinding

enum class NativePlacement {
    LANGUAGE, ONBOARDING_1, ONBOARDING_2, ONBOARDING_3, ONBOARDING_AD, HOME_FEED, POST_INTERSTITIAL,

    /** Shared app-wide slot for [NativeCollapseAd] - one placement for every collapsible call site. */
    NATIVE_COLLAPSIBLE,

    /** Native ad rendered as a plain banner slot (enable.banner=3) - see [BannerAds]. */
    BANNER_NATIVE,

    /** Native ad on the welcome-back screen (enable.onresume=2) - see WelcomeBackActivity. */
    WELCOME_BACK,
}

/**
 * Per-placement pool of one [NativeAd]: a subscriber [take]s the ad to show it in its own view.
 * If the ad finishes loading only after the subscriber's gone, it's [cache]d for the next taker
 * instead of wasted - but once an ad has actually been shown, it's done for good; nobody caches
 * it back. An ad is never handed to two subscribers at the same time, since a [NativeAd] can only
 * be registered to one [NativeAdView] at once.
 */
private class NativeAdSlot {
    private class Entry(val ad: NativeAd, val loadedAt: Long)

    private var freeAd: Entry? = null
    private var loading = false
    private var pendingCallback: ((NativeAd) -> Unit)? = null
    private var pendingFailure: ((String) -> Unit)? = null
    private var lastLoadFailed = false

    /** Removes and returns the free cached ad if one exists and isn't older than [ttlMs]. */
    @Synchronized
    fun take(ttlMs: Long): NativeAd? {
        val entry = freeAd ?: return null
        freeAd = null
        if (System.currentTimeMillis() - entry.loadedAt >= ttlMs) {
            entry.ad.destroy()
            return null
        }
        return entry.ad
    }

    /** Caches [ad] as the slot's free entry so the next subscriber can reuse it instead of reloading. */
    @Synchronized
    fun cache(ad: NativeAd) {
        freeAd = Entry(ad, System.currentTimeMillis())
    }

    /**
     * Registers [callback] for the next ad to finish loading, or, if null, just ensures a load is
     * running so the result can be preloaded into [freeAd] for whoever asks next. [onFailed] is
     * told if that load fails. Returns whether to actually start a network load.
     */
    @Synchronized
    fun requestLoad(callback: ((NativeAd) -> Unit)?, onFailed: ((String) -> Unit)?): Boolean {
        if (callback != null) {
            pendingCallback = callback
            pendingFailure = onFailed
        }
        if (loading) return false
        loading = true
        lastLoadFailed = false
        return true
    }

    /** True when the last load came up empty and no new one is running - nothing is coming for now. */
    @Synchronized
    fun failedAndIdle(): Boolean = lastLoadFailed && !loading

    /** Whether a cached ad is sitting free and not yet expired, without consuming it. */
    @Synchronized
    fun hasFresh(ttlMs: Long): Boolean {
        val entry = freeAd ?: return false
        return System.currentTimeMillis() - entry.loadedAt < ttlMs
    }

    /** Un-registers [callback] if it's still the one waiting, so a late result isn't delivered to a gone view. */
    @Synchronized
    fun cancelLoad(callback: (NativeAd) -> Unit) {
        if (pendingCallback === callback) {
            pendingCallback = null
            pendingFailure = null
        }
    }

    /** Returns the callback to notify, or null if nobody is waiting (in which case [ad] is cached as free). */
    @Synchronized
    fun onAdLoaded(ad: NativeAd): ((NativeAd) -> Unit)? {
        loading = false
        val callback = pendingCallback
        pendingCallback = null
        pendingFailure = null
        if (callback == null) freeAd = Entry(ad, System.currentTimeMillis())
        return callback
    }

    /** Returns the failure callback to notify, if the subscriber waiting on this load gave one. */
    @Synchronized
    fun onLoadFailed(): ((String) -> Unit)? {
        loading = false
        lastLoadFailed = true
        val failure = pendingFailure
        pendingCallback = null
        pendingFailure = null
        return failure
    }

    @Synchronized
    fun clear() {
        freeAd?.ad?.destroy()
        freeAd = null
        loading = false
        pendingCallback = null
        pendingFailure = null
    }
}

/** Native ads, cached per placement so a screen doesn't reload one every time it's shown. */
object NativeAds {
    private val slots = mutableMapOf<NativePlacement, NativeAdSlot>()

    @Synchronized
    private fun slotFor(placement: NativePlacement) = slots.getOrPut(placement) { NativeAdSlot() }

    /** Starts [placement]'s waterfall now, ahead of any screen needing it, if not already loading or cached. */
    fun preload(placement: NativePlacement) {
        if (!Ads.adsEnabled || !Ads.isReady) return
        val slot = slotFor(placement)
        if (slot.hasFresh(TTL_MS)) return
        startLoad(placement, slot, onReady = null, onFailed = null)
    }

    /**
     * Whether no ad for [placement] is on its way: ads are off, the SDK isn't ready (consent denied),
     * or the last load failed and nothing new is loading. For views that [poll], so they can hide
     * instead of shimmering forever.
     */
    fun isUnavailable(placement: NativePlacement): Boolean =
        !Ads.adsEnabled || !Ads.isReady || slotFor(placement).failedAndIdle()

    /**
     * Takes the preloaded ad for [placement], if one is ready, for the caller to display and
     * destroy on its own (no container/shimmer management, unlike [subscribe]) - for one-shot
     * full-screen placements that need the ad to already be sitting there with no load wait.
     * Immediately kicks off a reload so the slot is refilled for next time. Returns null if
     * nothing was preloaded in time; treat that the same as a no-fill.
     */
    fun poll(placement: NativePlacement): NativeAd? {
        if (!Ads.adsEnabled || !Ads.isReady) return null
        val ad = slotFor(placement).take(TTL_MS)
        preload(placement)
        return ad
    }

    /**
     * Calls [onDone] once [placement] has a fresh ad sitting ready for [poll] - right away if one is
     * already cached, otherwise after loading one - or after [timeoutMs] / a failed load, whichever
     * comes first. For one-shot full-screen placements with nothing in front of them to hide the
     * load (e.g. a native-only break), so [poll] doesn't come up empty. Always calls [onDone] exactly once.
     */
    fun awaitReady(activity: Activity, placement: NativePlacement, timeoutMs: Long, onDone: () -> Unit) {
        val slot = slotFor(placement)
        if (!Ads.adsEnabled || !Ads.isReady || slot.hasFresh(TTL_MS)) { onDone(); return }
        var finished = false
        val finish = { if (!finished) { finished = true; onDone() } }
        val handle = request(activity, placement, onFailed = { finish() }) { ad -> slot.cache(ad); finish() }
        Handler(Looper.getMainLooper()).postDelayed({ handle.destroy(); finish() }, timeoutMs)
    }

    /**
     * @param preloadOnShow Whether to kick off a real reload for [placement] the moment an ad is
     * bound to [container] - use this for slots that get shown again and again (e.g. a screen the
     * user revisits) so a fresh ad is ready by the time this one is dismissed. Leave false for one-shot placements (e.g. onboarding
     * pages shown exactly once) where preloading a replacement would never get used.
     */
    fun subscribe(
        activity: Activity,
        placement: NativePlacement,
        container: FrameLayout,
        preloadOnShow: Boolean = false,
        render: (Activity, NativeAd) -> NativeAdView = ::renderDefaultCard,
    ): AdHandle {
        if (!Ads.adsEnabled || !Ads.isReady) {
            container.visibility = View.GONE
            return AdHandle {}
        }
        var currentView: NativeAdView? = null
        var attachedAd: NativeAd? = null
        var shimmer: AdNativeShimmerBinding? = showShimmer(activity, container)

        val request = request(
            activity, placement,
            onFailed = {
                // No ad to show - hide the slot instead of leaving a shimmer spinning forever.
                shimmer?.let { it.root.stopShimmer(); container.removeView(it.root) }
                shimmer = null
                container.visibility = View.GONE
            },
        ) { ad ->
            shimmer?.let { it.root.stopShimmer(); container.removeView(it.root) }
            shimmer = null
            val view = render(activity, ad)
            container.removeAllViews()
            view.alpha = 0f
            container.addView(view)
            view.animate().alpha(1f).setDuration(FADE_IN_MS).start()
            currentView = view
            attachedAd = ad
            // This ad is now in use - kick off a real load for the next one right away so a
            // replacement is warm by the time this one is dismissed.
            if (preloadOnShow) preload(placement)
        }

        return AdHandle {
            request.destroy()
            activity.runOnUiThread {
                currentView?.destroy()
                currentView = null
                shimmer?.let { it.root.stopShimmer(); container.removeView(it.root) }
                shimmer = null
                container.removeAllViews()
            }
            // This ad has already been shown - it's done, not worth caching for reuse. A
            // future subscriber just loads its own (fresh, if preloadOnShow already got one going).
            attachedAd?.destroy()
            attachedAd = null
        }
    }

    /**
     * Delivers one ad for [placement] to [onReady] on the UI thread - the cached one if still
     * fresh, otherwise a newly loaded one - or tells [onFailed] if the waterfall comes up empty.
     * The delivered ad belongs to the caller, who must destroy it. Destroying the returned handle
     * stops delivery; an ad that still arrives afterwards is cached for the next taker instead.
     * The building block under [subscribe], for callers that manage their own views.
     */
    internal fun request(
        activity: Activity,
        placement: NativePlacement,
        onFailed: (String) -> Unit = {},
        onReady: (NativeAd) -> Unit,
    ): AdHandle {
        val slot = slotFor(placement)
        var active = true
        val deliver: (NativeAd) -> Unit = { ad ->
            activity.runOnUiThread { if (active) onReady(ad) else slot.cache(ad) }
        }
        val fail: (String) -> Unit = { error ->
            activity.runOnUiThread { if (active) onFailed(error) }
        }
        slot.take(TTL_MS)?.let(deliver) ?: startLoad(placement, slot, deliver, fail)
        return AdHandle {
            active = false
            slot.cancelLoad(deliver)
        }
    }

    /** Shows a shimmering skeleton in [container] so the ad slot never pops in from empty. */
    private fun showShimmer(activity: Activity, container: FrameLayout): AdNativeShimmerBinding {
        val binding = AdNativeShimmerBinding.inflate(LayoutInflater.from(activity))
        container.removeAllViews()
        container.addView(binding.root)
        binding.root.startShimmer()
        return binding
    }

    private fun startLoad(
        placement: NativePlacement,
        slot: NativeAdSlot,
        onReady: ((NativeAd) -> Unit)?,
        onFailed: ((String) -> Unit)?,
    ) {
        if (!slot.requestLoad(onReady, onFailed)) return
        WaterfallLoader<NativeAd>(Ads.ids.native[placement].orEmpty()).load(
            attempt = { id, loaded, failed ->
                val request = NativeAdRequest.Builder(id, listOf(NativeAd.NativeAdType.NATIVE)).build()
                NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                    override fun onNativeAdLoaded(nativeAd: NativeAd) {
                        nativeAd.adEventCallback = object : NativeAdEventCallback {
                            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
                        }
                        loaded(nativeAd)
                    }
                    override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                })
            },
            onLoaded = { ad, _ -> slot.onAdLoaded(ad)?.invoke(ad) },
            onFailed = { error -> slot.onLoadFailed()?.invoke(error) },
        )
    }

    private fun renderDefaultCard(activity: Activity, ad: NativeAd): NativeAdView =
        renderTemplate(activity, ad, RemoteConfig.nativeTemplate)

    /** Inflates [template] and binds [ad] into it - for any screen that wants a specific template. */
    fun renderTemplate(activity: Activity, ad: NativeAd, template: NativeAdTemplate): NativeAdView {
        val view = LayoutInflater.from(activity).inflate(template.layout, null) as NativeAdView
        populateView(view, ad)
        return view
    }

    /**
     * Gắn [ad] vào bất kỳ layout native card nào dùng chung id (xem [NativeAdTemplate]). Chỉ `ad_headline`
     * là bắt buộc; các view khác thiếu thì bỏ qua, và view của asset mà ad không có thì ẩn đi.
     * `ad_call_to_action` có thể là nút chữ hoặc nút icon (Tiny2) - chỉ nút chữ mới được gán text.
     */
    private fun populateView(view: NativeAdView, ad: NativeAd) {
        val headline = view.findViewById<TextView>(R.id.ad_headline)
        val icon: ImageView? = view.findViewById(R.id.ad_icon)
        val body: TextView? = view.findViewById(R.id.ad_body)
        val cta: View? = view.findViewById(R.id.ad_call_to_action)
        val advertiser: TextView? = view.findViewById(R.id.ad_advertiser)
        val stars: RatingBar? = view.findViewById(R.id.ad_stars)
        val price: TextView? = view.findViewById(R.id.ad_price)
        val media: MediaView? = view.findViewById(R.id.ad_media)

        view.headlineView = headline
        body?.let { view.bodyView = it }
        icon?.let { view.iconView = it }
        cta?.let { view.callToActionView = it }
        advertiser?.let { view.advertiserView = it }
        stars?.let { view.starRatingView = it }
        price?.let { view.priceView = it }

        headline.text = ad.headline
        body?.text = ad.body
        (cta as? TextView)?.text = ad.callToAction
        icon?.setImageDrawable(ad.icon?.drawable)
        advertiser?.text = ad.advertiser
        stars?.rating = ad.starRating?.toFloat() ?: 0f
        price?.text = ad.price
        body?.visibility = visibility(ad.body)
        cta?.visibility = visibility(ad.callToAction)
        icon?.visibility = visibility(ad.icon)
        advertiser?.visibility = visibility(ad.advertiser)
        stars?.visibility = visibility(ad.starRating)
        price?.visibility = visibility(ad.price)
        view.registerNativeAd(ad, media)
    }

    private fun visibility(value: Any?) = if (value == null) View.GONE else View.VISIBLE

    @Synchronized
    fun clear() {
        slots.values.forEach { it.clear() }
        slots.clear()
    }

    private const val TTL_MS = 60L * 60 * 1000
    private const val FADE_IN_MS = 200L
}
