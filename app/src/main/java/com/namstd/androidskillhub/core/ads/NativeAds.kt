package com.namstd.androidskillhub.core.ads

import android.app.Activity
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.template.AdChoicesCorner
import com.namstd.androidskillhub.core.ads.template.NativeAdTemplate
import com.namstd.androidskillhub.core.ads.template.NativeSkeleton
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ads.template.NativeTemplateSpec
import com.namstd.androidskillhub.core.ads.template.NativeStyleApplier
import com.namstd.androidskillhub.core.config.PlacementConfig
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * Các chỗ hiện native trong app, mỗi chỗ ứng với một ad unit ([unit] - ID trong `ad_ids.native`, `template` của placement
 * chứa unit đó). Cache dùng chung theo ID ([AdIdPool]): ad đã load của ID A dùng được ở mọi chỗ có A trong `ad_ids`.
 * Mỗi chỗ đang chờ có lượt load riêng, nên hai chỗ cùng ID hiện cùng lúc (vd. các trang Onboarding) không giành ad.
 */
enum class NativePlacement(val unit: AdUnit) {
    /** Native trên màn Splash (`native_splash`). */
    SPLASH(AdUnit.NATIVE_SPLASH),
    /** Màn Language lần đầu cài app (luồng Splash → Language → Onboarding). */
    LANGUAGE(AdUnit.NATIVE_LANGUAGE),

    /** Màn Language mở từ Cài đặt - placement `native_language_2`. */
    LANGUAGE_SETTING(AdUnit.NATIVE_LANGUAGE_2),

    /** Dải native nhỏ ngay dưới tiêu đề màn Language (cả lần đầu lẫn mở từ Cài đặt) - vị trí `native_language_small`. */
    LANGUAGE_SMALL(AdUnit.NATIVE_LANGUAGE_SMALL),

    /**
     * Native ở chân trang nội dung Onboarding 1–4: chung placement `native_onboarding` (enable, template, cùng bộ ID),
     * nhưng mỗi trang hiện một native riêng.
     */
    ONBOARDING_1(AdUnit.NATIVE_ONBOARDING),
    ONBOARDING_2(AdUnit.NATIVE_ONBOARDING),
    ONBOARDING_3(AdUnit.NATIVE_ONBOARDING),
    ONBOARDING_4(AdUnit.NATIVE_ONBOARDING),

    /** Trang native toàn màn hình trong Onboarding (`native_full_onboarding`). */
    ONBOARDING_AD(AdUnit.NATIVE_FULL_ONBOARDING),

    /** Native xen trong feed màn chính (`native_home_feed`). */
    HOME_FEED(AdUnit.NATIVE_HOME_FEED),

    /** Native toàn màn hình hiện sau inter của từng vị trí (ad_type inter_then_native, `ad_ids.native` của placement inter). */
    AFTER_INTER_SPLASH(AdUnit.NATIVE_INTER_SPLASH),
    AFTER_INTER_LANGUAGE(AdUnit.NATIVE_INTER_LANGUAGE),
    AFTER_INTER_ONBOARDING(AdUnit.NATIVE_INTER_AFTER_ONBOARDING),
    AFTER_INTER_HOME(AdUnit.NATIVE_INTER_HOME),
    AFTER_INTER_RESUME(AdUnit.NATIVE_ON_RESUME),

    /** Native collapsible ở ô banner màn chính (`banner_home` ad_type native_as_banner_collap) - xem [NativeCollapseAd]. */
    NATIVE_COLLAPSIBLE(AdUnit.NATIVE_HOME),

    /** Native hiển thị như một slot banner thường (`banner_home` ad_type native_as_banner) - xem [BannerAds]. */
    BANNER_NATIVE(AdUnit.NATIVE_HOME),

    /** Native trên màn Welcome Back (`on_resume` ad_type native) - xem WelcomeBackActivity. */
    WELCOME_BACK(AdUnit.NATIVE_ON_RESUME);

    /**
     * Chỗ này có bật theo cấu hình placement ([config]) không. Native Splash / Language / Onboarding / Welcome back /
     * feed: placement bật; các chỗ còn lại do placement cha quyết định (ad_type của inter / banner, trang native full)
     * nên luôn được phép ở đây.
     */
    fun isEnabled(config: PlacementConfig): Boolean = when (this) {
        SPLASH, LANGUAGE, LANGUAGE_SETTING, LANGUAGE_SMALL, ONBOARDING_1, ONBOARDING_2, ONBOARDING_3, ONBOARDING_4,
        WELCOME_BACK, HOME_FEED -> config.enable
        else -> true
    }
}

/**
 * Native ad. Cache theo ID thật trong `ad_ids` ([AdIdPool]), dùng chung giữa các placement: placement chỉ quyết định
 * bật/tắt, template và chuỗi ID ưu tiên ([chain]); ad load cho chỗ này mà chưa dùng thì chỗ khác cùng ID lấy được.
 */
object NativeAds {
    private val pool = AdIdPool<NativeAd>(TTL_MS, destroy = NativeAd::destroy)

    /** Chuỗi ID (waterfall, ưu tiên trước) của [placement] - ID thật trong config, cũng là khoá cache. */
    private fun chain(placement: NativePlacement) = placement.unit.configIds()

    /** Tải sẵn một ad cho [placement] (cất vào pool theo ID) nếu chưa có ad rảnh / lượt load nào cho chuỗi ID đó. */
    fun preload(placement: NativePlacement) {
        if (!AdPolicy.nativeAllowed || !Ads.isReady) return
        val chain = chain(placement)
        if (chain.isEmpty() || !pool.needsLoad(chain, preload = true)) return
        startLoad(placement)
    }

    /** Đã có ad mới (của một ID bất kỳ trong chuỗi của [placement]) nằm sẵn để hiện ngay chưa. */
    fun isReady(placement: NativePlacement): Boolean =
        AdPolicy.nativeAllowed && Ads.isReady && pool.hasFresh(chain(placement))

    /**
     * Có phải sẽ không có ad nào cho [placement] hay không: ads đang tắt, SDK chưa sẵn sàng (consent bị
     * từ chối), hoặc lượt load gần nhất thất bại và không có lượt mới nào đang chạy. Dành cho các view
     * dùng [poll], để chúng ẩn đi thay vì shimmer mãi.
     */
    fun isUnavailable(placement: NativePlacement): Boolean =
        !AdPolicy.nativeAllowed || !Ads.isReady || chain(placement).isEmpty() || pool.failedAndIdle(chain(placement))

    /**
     * Lấy ad đã preload cho [placement], nếu đã sẵn sàng, để caller tự hiển thị và tự destroy
     * (không quản lý container/shimmer, khác với [subscribe]) - dành cho các placement full-screen
     * chỉ hiện một lần, cần ad nằm sẵn đó mà không phải chờ load. Ngay sau đó chạy reload để pool
     * được nạp lại cho lần sau. Trả về null nếu không preload kịp; xử lý như trường hợp no-fill.
     */
    fun poll(placement: NativePlacement): NativeAd? {
        if (!AdPolicy.nativeAllowed || !Ads.isReady) return null
        val loaded = pool.take(chain(placement))
        if (BuildConfig.DEBUG && loaded != null) Log.d(TAG, "${placement.name}: lấy ad sẵn của ${loaded.id.takeLast(10)}")
        preload(placement)
        return loaded?.ad
    }

    /**
     * Gọi [onDone] khi [placement] đã có một ad mới nằm sẵn cho [poll] - gọi ngay nếu đã có cache,
     * nếu không thì sau khi load xong một ad - hoặc sau [timeoutMs] / khi load thất bại, tùy cái nào
     * đến trước. Dành cho các placement full-screen chỉ hiện một lần mà không có gì phía trước che
     * lúc load (vd. một break chỉ có native), để [poll] không về tay trắng. Luôn gọi [onDone] đúng một lần.
     */
    fun awaitReady(activity: Activity, placement: NativePlacement, timeoutMs: Long, onDone: () -> Unit) {
        if (!AdPolicy.nativeAllowed || !Ads.isReady || isReady(placement)) { onDone(); return }
        var finished = false
        val finish = { if (!finished) { finished = true; onDone() } }
        val handle = requestLoaded(activity, placement, onFailed = { finish() }) { loaded ->
            pool.cache(loaded.id, loaded.ad)
            finish()
        }
        Handler(Looper.getMainLooper()).postDelayed({ handle.destroy(); finish() }, timeoutMs)
    }

    /**
     * @param preloadOnShow Có chạy reload thật cho [placement] ngay lúc một ad được gắn vào [container]
     * hay không - dùng cho các slot được hiện đi hiện lại (vd. một màn người dùng quay lại nhiều lần)
     * để có sẵn ad mới khi ad này bị đóng. Để false cho các placement chỉ hiện một lần (vd. các trang
     * onboarding chỉ hiện đúng một lần) vì preload ad thay thế sẽ không bao giờ được dùng.
     */
    fun subscribe(
        activity: Activity,
        placement: NativePlacement,
        container: FrameLayout,
        preloadOnShow: Boolean = false,
        /** Chạy (main thread) khi không có ad để hiện: tắt quảng cáo / SDK chưa sẵn sàng / waterfall không ra ad. */
        onUnavailable: () -> Unit = {},
        render: (Activity, NativeAd) -> NativeAdView = { a, ad -> renderTemplate(a, ad, templateFor(placement)) },
    ): AdHandle {
        if (!AdPolicy.nativeAllowed || !Ads.isReady) {
            container.visibility = View.GONE
            onUnavailable()
            return AdHandle {}
        }
        var currentView: NativeAdView? = null
        var attachedAd: NativeAd? = null
        var shimmer: ShimmerFrameLayout? = showShimmer(activity, container, templateFor(placement))

        val request = request(
            activity, placement,
            onFailed = {
                // Không có ad để hiện - ẩn slot đi thay vì để shimmer chạy mãi.
                shimmer?.let { it.stopShimmer(); container.removeView(it) }
                shimmer = null
                container.visibility = View.GONE
                onUnavailable()
            },
        ) { ad ->
            shimmer?.let { it.stopShimmer(); container.removeView(it) }
            shimmer = null
            val view = render(activity, ad)
            container.removeAllViews()
            view.alpha = 0f
            container.addView(view)
            view.animate().alpha(1f).setDuration(FADE_IN_MS).start()
            currentView = view
            attachedAd = ad
            // Ad này giờ đã được dùng - chạy load thật cho ad kế tiếp ngay để có sẵn ad
            // thay thế khi ad này bị đóng.
            if (preloadOnShow) preload(placement)
        }

        return AdHandle {
            request.destroy()
            activity.runOnUiThread {
                currentView?.destroy()
                currentView = null
                shimmer?.let { it.stopShimmer(); container.removeView(it) }
                shimmer = null
                container.removeAllViews()
            }
            // Ad này đã hiển thị rồi - coi như xong, không đáng cache để dùng lại. Subscriber
            // sau sẽ tự load ad của mình (ad mới, nếu preloadOnShow đã chạy load từ trước).
            attachedAd?.destroy()
            attachedAd = null
        }
    }

    /**
     * Giao một ad cho [placement] tới [onReady] trên UI thread - ad đang cache nếu còn mới, nếu không
     * thì một ad vừa load - hoặc báo [onFailed] nếu waterfall không ra ad nào. Ad được giao thuộc
     * về caller, caller phải tự destroy nó. Destroy handle trả về sẽ dừng việc giao; ad nào vẫn đến
     * sau đó sẽ được cất vào pool cho chỗ khác cùng ID. Là khối nền bên dưới [subscribe], dành cho các
     * caller tự quản lý view của mình.
     */
    internal fun request(
        activity: Activity,
        placement: NativePlacement,
        onFailed: (String) -> Unit = {},
        onReady: (NativeAd) -> Unit,
    ): AdHandle = requestLoaded(activity, placement, onFailed) { onReady(it.ad) }

    /**
     * Như [request] nhưng giao kèm ID nguồn. Có ad rảnh của chuỗi ID thì giao ngay; không thì xếp hàng chờ và chạy một
     * lượt load riêng. Huỷ handle: bỏ chờ; ad đến sau được cất vào pool cho chỗ khác cùng ID.
     */
    private fun requestLoaded(
        activity: Activity,
        placement: NativePlacement,
        onFailed: (String) -> Unit,
        onReady: (AdIdPool.Loaded<NativeAd>) -> Unit,
    ): AdHandle {
        val chain = chain(placement)
        pool.take(chain)?.let { loaded ->
            if (BuildConfig.DEBUG) Log.d(TAG, "${placement.name}: lấy ad sẵn của ${loaded.id.takeLast(10)}")
            activity.runOnUiThread { onReady(loaded) }
            return AdHandle {}
        }
        if (chain.isEmpty()) {
            activity.runOnUiThread { onFailed("No ad unit IDs configured") }
            return AdHandle {}
        }
        var active = true
        val waiter = pool.addWaiter(
            chain,
            onReady = { loaded ->
                activity.runOnUiThread {
                    if (active) onReady(loaded) else pool.cache(loaded.id, loaded.ad)
                }
            },
            onFailed = { activity.runOnUiThread { if (active) onFailed("No fill from configured ad units") } },
        )
        if (pool.needsLoad(chain)) startLoad(placement)
        return AdHandle {
            active = false
            pool.removeWaiter(waiter)
        }
    }

    /** Shimmer giữ chỗ trong lúc load, dựng từ đúng mẫu [spec] sẽ hiện (cùng kích thước) - xem [NativeSkeleton]. */
    private fun showShimmer(activity: Activity, container: FrameLayout, spec: NativeTemplateSpec): ShimmerFrameLayout {
        val shimmer = NativeSkeleton.create(activity, spec)
        container.removeAllViews()
        container.addView(shimmer)
        shimmer.startShimmer()
        return shimmer
    }

    /** Chạy một lượt waterfall cho chuỗi ID của [placement]; kết quả giao cho người chờ hợp ID hoặc cất vào pool. */
    private fun startLoad(placement: NativePlacement) {
        val unit = placement.unit
        val chain = unit.configIds()
        pool.startLoad(chain)
        WaterfallLoader<NativeAd>(chain).load(
            attempt = { id, loaded, failed ->
                // Biểu tượng AdChoices ("i") ở góc trên trái: góc trên phải là nút X / Next / thu gọn của app (mẫu Full,
                // Collap, native sau inter, trang native full Onboarding). SDK tự vẽ icon theo góc này cho mọi mẫu.
                val request = NativeAdRequest.Builder(unit.requestId(id), listOf(NativeAd.NativeAdType.NATIVE))
                    .setAdChoicesPlacement(AdChoicesPlacement.TOP_LEFT)
                    .build()
                NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                    override fun onNativeAdLoaded(nativeAd: NativeAd) {
                        AdAudience.onNativeLoaded(nativeAd.headline)
                        nativeAd.adEventCallback = object : NativeAdEventCallback {
                            override fun onAdPaid(adValue: AdValue) = AdjustRevenueLogger.log(adValue)
                        }
                        loaded(nativeAd)
                    }
                    override fun onAdFailedToLoad(adError: LoadAdError) = failed(adError.message)
                })
            },
            onLoaded = { ad, id ->
                val delivery = pool.onLoaded(chain, id, ad)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "${placement.name}: load xong ${id.takeLast(10)} → " + (delivery?.let { "giao cho người đang chờ" } ?: "cất pool"))
                }
                delivery?.let { (waiter, loaded) -> waiter.onReady(loaded) }
            },
            onFailed = { error ->
                if (BuildConfig.DEBUG) Log.d(TAG, "${placement.name}: load lỗi ($error)")
                pool.onFailed(chain)?.onFailed?.invoke()
            },
        )
    }

    /**
     * Mẫu của [placement]: theo `template` của placement chứa unit đó - mẫu có sẵn hoặc mẫu tự đặt trong
     * `templates` (trống → mẫu mặc định), kèm style - xem [NativeAdTemplate.specFor].
     */
    fun templateFor(placement: NativePlacement): NativeTemplateSpec =
        NativeAdTemplate.specFor(placement.unit.key, AdPolicy.config(placement.unit.key), RemoteConfig.templates())

    /** Inflate mẫu [spec], gắn [ad] vào đó rồi áp style - cho bất kỳ màn nào muốn dùng một mẫu cụ thể. */
    fun renderTemplate(activity: Activity, ad: NativeAd, spec: NativeTemplateSpec): NativeAdView {
        val view = LayoutInflater.from(activity).inflate(spec.template.layout, null) as NativeAdView
        AdChoicesCorner.reserve(view)
        populateView(view, ad)
        NativeStyleApplier.apply(view, spec.style)
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
        val media: MediaView? = view.findViewById(R.id.ad_media)

        view.headlineView = headline
        body?.let { view.bodyView = it }
        icon?.let { view.iconView = it }
        cta?.let { view.callToActionView = it }
        advertiser?.let { view.advertiserView = it }
        stars?.let { view.starRatingView = it }

        headline.text = ad.headline
        body?.text = ad.body
        (cta as? TextView)?.text = ad.callToAction
        icon?.setImageDrawable(ad.icon?.drawable)
        advertiser?.text = ad.advertiser
        stars?.rating = ad.starRating?.toFloat() ?: 0f
        body?.visibility = visibility(ad.body)
        cta?.visibility = visibility(ad.callToAction)
        icon?.visibility = visibility(ad.icon)
        advertiser?.visibility = visibility(ad.advertiser)
        stars?.visibility = visibility(ad.starRating)
        view.registerNativeAd(ad, media)
    }

    private fun visibility(value: Any?) = if (value == null) View.GONE else View.VISIBLE

    fun clear() = pool.clear()

    private const val TTL_MS = 60L * 60 * 1000
    private const val TAG = "AdsNative"
    private const val FADE_IN_MS = 200L
}
