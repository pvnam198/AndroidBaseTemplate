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
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.PlacementFormat
import com.namstd.androidskillhub.databinding.AdBannerShimmerBinding

/**
 * Các ô banner trong app, mỗi ô là một placement `banner_*`: [key] là key cấu hình (`format`, `refresh_ms`,
 * `open_ad_on_clicks`, `template`), [bannerUnit] là ad unit banner. Kiểu native trong ô banner (format native /
 * native_collapsible) chỉ có ở màn chính, vì chỉ `banner_home` có các unit `native_home` / `native_collap_home`.
 */
enum class BannerPlacement(val key: AdKey, val bannerUnit: AdUnit, val supportsNative: Boolean) {
    HOME(AdKey.BANNER_HOME, AdUnit.BANNER_HOME, supportsNative = true),
    SETTING(AdKey.BANNER_SETTING, AdUnit.BANNER_SETTING, supportsNative = false),
}

object BannerAds {
    /**
     * Load một banner vào [container] cho [placement]. Loại do `format` của placement quyết định: tắt = ẩn ô,
     * banner = AdMob banner thường, banner_collapsible = AdMob collapsible, native = native thường, native_collapsible
     * = native collapsible ([NativeCollapseAd]: 1 native ad to rồi thu nhỏ thành dải). Placement không có native
     * ([BannerPlacement.supportsNative] = false) thì native / native_collapsible dùng banner thường. Chỉ điều hướng - load thật ở
     * [loadAdMobBanner]/[NativeCollapseAd].
     *
     * [autoReload] null = theo `refresh_ms` của placement (> 0 là tự reload sau bấy nhiêu ms); màn nào tự reload
     * bằng tay (vd. khi chuyển tab) thì truyền false. [BannerSlotHandle.reload] của handle trả về cho phép reload
     * khi cần - xem [BannerSlotReloader].
     */
    fun load(
        activity: Activity,
        container: ViewGroup,
        placement: BannerPlacement,
        autoReload: Boolean? = null,
        onStatus: (String) -> Unit = {},
    ): BannerSlotHandle {
        val config = AdPolicy.config(placement.key)
        val reloadGapMs = config.refreshMs
        val reload = autoReload ?: (reloadGapMs > 0L)
        val format = if (!config.enable) null else when (config.format) {
            // Placement không có native thì native / native_collapsible dùng banner thường.
            PlacementFormat.NATIVE, PlacementFormat.NATIVE_COLLAPSIBLE ->
                if (placement.supportsNative) config.format else PlacementFormat.BANNER
            else -> config.format
        }
        return when (format) {
            null -> {
                container.visibility = View.GONE
                BannerSlotHandle.NONE
            }
            PlacementFormat.NATIVE_COLLAPSIBLE -> NativeCollapseAd(
                activity, container,
                collapGate = CollapClickGate(config.openAdOnClicks),
                autoReload = reload, reloadGapMs = reloadGapMs, onStatus = onStatus,
            ).start()
            PlacementFormat.NATIVE -> NativeBannerSlot(activity, container as FrameLayout).start()
            PlacementFormat.BANNER_COLLAPSIBLE ->
                loadAdMobBanner(activity, container, placement.bannerUnit, collapsible = true, reload, reloadGapMs, onStatus)
            else -> loadAdMobBanner(activity, container, placement.bannerUnit, collapsible = false, reload, reloadGapMs, onStatus)
        }
    }

    /** Load một AdMob banner thật vào [container] - banner thường, hoặc bản collapsible của AdMob khi [collapsible]. */
    private fun loadAdMobBanner(
        activity: Activity,
        container: ViewGroup,
        unit: AdUnit,
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
        return AdMobBannerAd(activity, container, unit, collapsible, autoReload, reloadGapMs, onStatus).also { it.load(isReload = false) }
    }
}

/**
 * Một slot AdMob banner trong [container]. Mỗi lượt load - lần đầu và mọi lần auto reload (xem
 * [BannerSlotReloader]) - bỏ banner trước và đưa slot về shimmer nhỏ ngay, rồi thay banner mới vào
 * khi load xong; với [collapsible] thì banner mới cũng được request dạng collapsible, nên nó mở ở
 * trạng thái expand. Khi reload, shimmer được giữ ít nhất [BannerSlotReloader.MIN_RELOAD_SHIMMER_MS],
 * để việc thay banner không bao giờ trông như bị nháy. Banner load xong trong lúc activity chưa resume
 * sẽ chờ onResume, để khoảng cách reload - bắt đầu tính khi banner được hiển thị - chỉ đếm thời gian
 * banner thực sự nằm trên màn hình. Load thất bại thì ẩn [container] cho tới khi lượt load sau hiện
 * lại shimmer. Chỉ chạy trên main thread, trừ các
 * callback của SDK, vốn quay lại qua [Activity.runOnUiThread].
 */
private class AdMobBannerAd(
    private val activity: Activity,
    private val container: ViewGroup,
    private val unit: AdUnit,
    private val collapsible: Boolean,
    autoReload: Boolean,
    reloadGapMs: Long,
    private val onStatus: (String) -> Unit,
) : BannerSlotHandle {
    private val handler = Handler(Looper.getMainLooper())
    private val reloader = BannerSlotReloader(activity, autoReload, reloadGapMs) { load(isReload = true) }
    private var current: AdView? = null
    /** Banner đã load đang chờ hiển thị - chờ hết shimmer tối thiểu của reload, hoặc chờ onResume. */
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
    /** Tăng lên sau mỗi lượt load; callback của lượt load cũ thấy giá trị đã đổi thì tự bỏ qua. */
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
        val ids = if (collapsible) unit.ids(TEST_BANNER_COLLAPSIBLE) else unit.ids()
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
                    // Không có banner để hiện - giải phóng slot thay vì để lại một dải trống.
                    clearShimmer()
                    container.visibility = View.GONE
                    reloader.onFailed()
                }
            },
        )
    }

    /** Hiển thị [pendingView] khi đã hết shimmer tối thiểu và activity đang resume. */
    private fun revealPending() {
        val adView = pendingView ?: return
        handler.removeCallbacks(revealRunnable)
        val wait = minShimmerUntil - SystemClock.elapsedRealtime()
        if (wait > 0) {
            handler.postDelayed(revealRunnable, wait)
            return
        }
        if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == false) return // onResume sẽ gọi lại vào đây.
        pendingView = null
        show(adView)
    }

    /** Từ đây banner đã lên màn hình - nên khoảng cách reload bắt đầu tính từ đây, không phải từ lúc load. */
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

    /** Hiện skeleton shimmer trong [container] để slot banner không bao giờ đột ngột hiện ra từ chỗ trống. */
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
 * Native ad hiển thị trong slot banner (`banner_home` = 3). Dùng [NativeAds.subscribe] (shimmer +
 * fade-in + ẩn khi no-fill đều xử lý ở đó). [reload] subscribe lại một native mới khi cần (vd. chuyển
 * tab); biến thể này không có sẵn timer reload.
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
