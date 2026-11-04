package com.namstd.androidskillhub.core.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.databinding.AdBannerShimmerBinding
import com.namstd.androidskillhub.databinding.AdNativeCollapseBinding
import com.namstd.androidskillhub.databinding.AdNativeCollapseSmallBinding

/**
 * The "native collapsible": one [NativeAd] shown in two sizes. Once loaded it opens large, docked
 * to the bottom of the activity's content root so - like AdMob's own collapsible banner - it floats
 * over the screen instead of pushing it. Closing it moves the same ad into [container] as a
 * banner-sized strip. The ad is only ever registered to one view at a time; while it's large,
 * [container] keeps its shimmer so the slot is already sized when the small strip lands there.
 *
 * An ad that arrives while the activity isn't resumed waits for onResume before opening, so the
 * large view never pops up behind another screen. No fill hides [container].
 *
 * A reload ([BannerSlotReloader]'s timer, or the screen calling [reload]) drops the current ad - large
 * or small - and puts the slot back to its small shimmer, then opens the new ad large again. The
 * shimmer stays up at least [BannerSlotReloader.MIN_RELOAD_SHIMMER_MS]; a failed reload hides the slot
 * until the next one.
 *
 * Call [start] once; the owner destroys it via [AdHandle.destroy], which also destroys the ad.
 */
internal class NativeCollapseAd(
    private val activity: Activity,
    private val container: ViewGroup,
    private val placement: NativePlacement = NativePlacement.NATIVE_COLLAPSIBLE,
    autoReload: Boolean,
    reloadGapMs: Long,
    private val onStatus: (String) -> Unit = {},
) : BannerSlotHandle {
    enum class State { IDLE, LOADING, LARGE, SMALL, HIDDEN }

    var state: State = State.IDLE
        private set

    private val lifecycle: Lifecycle? = (activity as? LifecycleOwner)?.lifecycle
    private val overlay = FrameLayout(activity)
    private var request: AdHandle? = null
    private var shimmer: AdBannerShimmerBinding? = null
    private var currentView: NativeAdView? = null
    private var currentAd: NativeAd? = null
    /** A loaded ad waiting to open large - for the reload's minimum shimmer, or for onResume. */
    private var pendingAd: NativeAd? = null
    private var destroyed = false
    private val handler = Handler(Looper.getMainLooper())
    private val reloader = BannerSlotReloader(activity, autoReload, reloadGapMs, ::reloadNow)
    /** When the current load's shimmer went up, if it has to stay a minimum time (reloads only). */
    private var minShimmerUntil = 0L

    private val revealRunnable = Runnable { revealPending() }

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            revealPending()
        }
    }

    fun start(): NativeCollapseAd {
        if (destroyed || state != State.IDLE) return this
        if (!Ads.isReady || !AdPolicy.nativeAllowed) {
            onStatus("Native collapsible: SDK not ready")
            hide()
            return this
        }
        state = State.LOADING
        container.visibility = View.VISIBLE
        showShimmer()
        lifecycle?.addObserver(lifecycleObserver)
        requestAd()
        return this
    }

    private fun requestAd() {
        onStatus("Native collapsible: loading")
        reloader.onLoadStarted()
        request?.destroy()
        request = NativeAds.request(
            activity, placement,
            onFailed = { error ->
                request = null
                onStatus("Native collapsible: $error")
                hide()
                reloader.onFailed()
            },
            onReady = { ad ->
                request = null
                onAdLoaded(ad)
            },
        )
    }

    private fun onAdLoaded(ad: NativeAd) {
        if (destroyed) {
            ad.destroy()
            return
        }
        pendingAd?.destroy()
        pendingAd = ad
        revealPending()
    }

    /** Opens [pendingAd] large once the minimum shimmer is over and the activity is resumed. */
    private fun revealPending() {
        val ad = pendingAd ?: return
        handler.removeCallbacks(revealRunnable)
        val wait = minShimmerUntil - SystemClock.elapsedRealtime()
        if (wait > 0) {
            handler.postDelayed(revealRunnable, wait)
            return
        }
        if (!isResumed()) return // lifecycleObserver.onResume calls back in.
        pendingAd = null
        showLarge(ad)
    }

    /** The ad is on screen from here - so this, not the load, is where the reload gap starts. */
    private fun showLarge(ad: NativeAd) {
        currentAd = ad
        val view = renderLarge(ad)
        overlay.removeAllViews()
        overlay.addView(view)
        attachOverlay()
        currentView = view
        state = State.LARGE
        onStatus("Native collapsible: large")
        // This ad is now in use - get the next one warm for the next screen (or reload) that asks.
        NativeAds.preload(placement)
        reloader.onShown()
    }

    override fun reload() = reloader.reloadNow()

    /** Reload: drop the current ad, back to the small shimmer, and open the next ad large once it's in. */
    private fun reloadNow() {
        if (destroyed) return
        request?.destroy()
        request = null
        handler.removeCallbacks(revealRunnable)
        pendingAd?.destroy()
        pendingAd = null
        detachOverlay()
        overlay.removeAllViews()
        currentView?.destroy()
        currentView = null
        currentAd?.destroy()
        currentAd = null
        state = State.LOADING
        container.visibility = View.VISIBLE
        showShimmer()
        minShimmerUntil = SystemClock.elapsedRealtime() + BannerSlotReloader.MIN_RELOAD_SHIMMER_MS
        requestAd()
    }

    private fun isResumed(): Boolean =
        lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false

    /** Moves the current ad from the large overlay into [container] - the ad itself is kept, not reloaded. */
    private fun showSmall() {
        val ad = currentAd ?: return hide()
        detachOverlay()
        overlay.removeAllViews()
        clearShimmer()
        val view = renderSmall(ad)
        container.removeAllViews()
        container.addView(view)
        currentView = view
        state = State.SMALL
        onStatus("Native collapsible: small")
    }

    /** No ad to show - frees the slot entirely instead of leaving an empty strip. */
    private fun hide() {
        request?.destroy()
        request = null
        detachOverlay()
        overlay.removeAllViews()
        clearShimmer()
        container.removeAllViews()
        container.visibility = View.GONE
        state = State.HIDDEN
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        runOnMainThread {
            reloader.cancel()
            handler.removeCallbacks(revealRunnable)
            lifecycle?.removeObserver(lifecycleObserver)
            request?.destroy()
            request = null
            detachOverlay()
            overlay.removeAllViews()
            clearShimmer()
            currentView?.destroy()
            currentView = null
            container.removeAllViews()
            currentAd?.destroy()
            currentAd = null
            pendingAd?.destroy()
            pendingAd = null
            state = State.HIDDEN
        }
    }

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

    /**
     * Docks [overlay] to the bottom of [activity]'s content root, outside any screen's own layout
     * flow, so it floats over the current content instead of pushing it. Insets itself off the
     * bottom system bar since it sits outside the padding BaseActivity applies to the screen's root.
     */
    private fun attachOverlay() {
        if (overlay.parent != null) return
        overlay.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        )
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { view, insets ->
            applyBottomInset(view, insets)
            insets
        }
        // The window dispatched its insets long before this overlay was added, so the listener above
        // wouldn't fire until the next change - pad from the current insets now, then ask for a fresh
        // dispatch so later changes (rotation, gesture/3-button nav switch) still reach it.
        val decorView = activity.window.decorView
        ViewCompat.getRootWindowInsets(decorView)?.let { applyBottomInset(overlay, it) }
        decorView.findViewById<ViewGroup>(android.R.id.content).addView(overlay)
        ViewCompat.requestApplyInsets(overlay)
    }

    private fun applyBottomInset(view: View, insets: WindowInsetsCompat) {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
    }

    private fun detachOverlay() {
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    /** Full card (icon + headline + media + body + CTA) with a corner close button that collapses it to [renderSmall]. */
    private fun renderLarge(ad: NativeAd): NativeAdView {
        val binding = AdNativeCollapseBinding.inflate(LayoutInflater.from(activity))
        val view = binding.root
        view.headlineView = binding.adHeadline
        view.bodyView = binding.adBody
        view.advertiserView = binding.adAdvertiser
        view.iconView = binding.adIcon
        view.callToActionView = binding.adCallToAction
        binding.adHeadline.text = ad.headline
        binding.adBody.text = ad.body
        binding.adAdvertiser.text = ad.advertiser
        binding.adCallToAction.text = ad.callToAction
        binding.adIcon.setImageDrawable(ad.icon?.drawable)
        binding.adIcon.visibility = visibility(ad.icon)
        binding.adBody.visibility = visibility(ad.body)
        binding.adAdvertiser.visibility = visibility(ad.advertiser)
        binding.adCallToAction.visibility = visibility(ad.callToAction)
        val gate = RemoteConfig.collapConfig
        if (gate.isEnabled) ad.enableCustomClickGesture()
        view.registerNativeAd(ad, binding.adMedia)
        binding.adClose.setOnClickListener {
            // collap_config: some X presses open the ad instead of collapsing - see [CollapClickGate].
            if (gate.opensAd(++closeTapCount)) {
                onStatus("Native collapsible: X press $closeTapCount opens the ad")
                ad.recordCustomClickGesture()
            } else {
                showSmall()
            }
        }
        return view
    }

    /** Banner-sized strip (icon + headline + body + CTA), no media. */
    private fun renderSmall(ad: NativeAd): NativeAdView {
        val binding = AdNativeCollapseSmallBinding.inflate(LayoutInflater.from(activity), container, false)
        val view = binding.root
        view.headlineView = binding.adHeadline
        view.bodyView = binding.adBody
        view.iconView = binding.adIcon
        view.callToActionView = binding.adCallToAction
        binding.adHeadline.text = ad.headline
        binding.adBody.text = ad.body
        binding.adCallToAction.text = ad.callToAction
        binding.adIcon.setImageDrawable(ad.icon?.drawable)
        binding.adIcon.visibility = visibility(ad.icon)
        binding.adBody.visibility = visibility(ad.body)
        binding.adCallToAction.visibility = visibility(ad.callToAction)
        view.registerNativeAd(ad, null)
        return view
    }

    private fun visibility(value: Any?) = if (value == null) View.GONE else View.VISIBLE

    private companion object {
        /** X presses on large collapsible cards this app session - shared across ads and screens (collap_config). */
        var closeTapCount = 0
    }
}
