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
import com.namstd.androidskillhub.core.config.PlacementConfig
import com.namstd.androidskillhub.databinding.AdNativeShimmerBinding

/**
 * Các chỗ hiện native trong app, mỗi chỗ ứng với một ad unit trong sheet ([unit] - lấy ID và `native_template` từ
 * vị trí của unit đó). Mỗi chỗ có slot cache riêng, nên hai chỗ dùng chung một unit (vd [HOME_FEED] và
 * [BANNER_NATIVE] cùng là `NATIVE_HOME`) vẫn không giành ad của nhau.
 */
enum class NativePlacement(val unit: AdUnit) {
    /** Native trên màn Splash (`native_splash`). */
    SPLASH(AdUnit.NATIVE_SPLASH),
    LANGUAGE(AdUnit.NATIVE_LANGUAGE),

    /** Native ở chân các trang nội dung Intro; `native_intro` liệt kê trang có native (`24` = trang 2 và 4). */
    ONBOARDING_1(AdUnit.NATIVE_INTRO_1),
    ONBOARDING_2(AdUnit.NATIVE_INTRO_2),
    ONBOARDING_3(AdUnit.NATIVE_INTRO_3),
    ONBOARDING_4(AdUnit.NATIVE_INTRO_4),

    /** Trang native toàn màn hình trong Intro (`native_intro_full`). */
    ONBOARDING_AD(AdUnit.NATIVE_INTRO_FULL),

    /** Native xen trong feed màn chính - sheet không có dòng riêng, dùng ID của `NATIVE_HOME`. */
    HOME_FEED(AdUnit.NATIVE_HOME),

    /** Native toàn màn hình hiện sau inter của từng vị trí (`NATIVE_INTER_*`). */
    AFTER_INTER_SPLASH(AdUnit.NATIVE_INTER_SPLASH),
    AFTER_INTER_LANGUAGE(AdUnit.NATIVE_INTER_LANGUAGE),
    AFTER_INTER_INTRO(AdUnit.NATIVE_INTER_INTRO),
    AFTER_INTER_HOME(AdUnit.NATIVE_INTER_HOME),

    /** Native collapsible ở ô banner màn chính (`banner_home` = 4) - xem [NativeCollapseAd]. */
    NATIVE_COLLAPSIBLE(AdUnit.NATIVE_COLLAP_HOME),

    /** Native hiển thị như một slot banner thường (`banner_home` = 3) - xem [BannerAds]. */
    BANNER_NATIVE(AdUnit.NATIVE_HOME),

    /** Native trên màn welcome-back (`on_resume` = 2) - xem WelcomeBackActivity. */
    WELCOME_BACK(AdUnit.NATIVE_WELCOME_BACK);

    /**
     * Chỗ này có bật theo `enable` của vị trí ([config]) không. Native Intro đọc theo chữ số (trang có native);
     * native Splash / Language / Welcome back cần `enable` > 0; các chỗ còn lại do vị trí cha quyết định
     * (inter, banner, trang native full) nên luôn được phép ở đây.
     */
    fun isEnabled(config: PlacementConfig): Boolean = when (this) {
        ONBOARDING_1 -> 1 in config.enableDigits
        ONBOARDING_2 -> 2 in config.enableDigits
        ONBOARDING_3 -> 3 in config.enableDigits
        ONBOARDING_4 -> 4 in config.enableDigits
        SPLASH, LANGUAGE, WELCOME_BACK -> config.enableCode > 0
        else -> true
    }
}

/**
 * Pool một [NativeAd] cho mỗi placement: subscriber [take] ad để hiển thị trong view của mình.
 * Nếu ad load xong khi subscriber đã đi mất, ad được [cache] lại cho người lấy tiếp theo thay vì
 * bỏ phí - nhưng một ad đã thực sự hiển thị thì coi như xong hẳn; không ai cache lại nó nữa.
 * Một ad không bao giờ được giao cho hai subscriber cùng lúc, vì mỗi [NativeAd] chỉ có thể
 * register vào một [NativeAdView] tại một thời điểm.
 */
private class NativeAdSlot {
    private class Entry(val ad: NativeAd, val loadedAt: Long)

    private var freeAd: Entry? = null
    private var loading = false
    private var pendingCallback: ((NativeAd) -> Unit)? = null
    private var pendingFailure: ((String) -> Unit)? = null
    private var lastLoadFailed = false

    /** Lấy ra và trả về ad đang cache rảnh nếu có và chưa cũ hơn [ttlMs]. */
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

    /** Cache [ad] làm entry rảnh của slot để subscriber tiếp theo dùng lại thay vì reload. */
    @Synchronized
    fun cache(ad: NativeAd) {
        freeAd = Entry(ad, System.currentTimeMillis())
    }

    /**
     * Đăng ký [callback] cho ad tiếp theo load xong, hoặc nếu null thì chỉ đảm bảo đang có một lượt
     * load để kết quả được preload vào [freeAd] cho người hỏi tiếp theo. [onFailed] được báo nếu
     * lượt load đó thất bại. Trả về việc có thực sự bắt đầu một lượt load mạng hay không.
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

    /** True khi lượt load gần nhất không có ad và không có lượt mới nào đang chạy - tạm thời sẽ không có ad. */
    @Synchronized
    fun failedAndIdle(): Boolean = lastLoadFailed && !loading

    /** Có ad cache đang rảnh và chưa hết hạn hay không, mà không lấy nó ra. */
    @Synchronized
    fun hasFresh(ttlMs: Long): Boolean {
        val entry = freeAd ?: return false
        return System.currentTimeMillis() - entry.loadedAt < ttlMs
    }

    /** Hủy đăng ký [callback] nếu nó vẫn là callback đang chờ, để kết quả đến muộn không giao cho view đã mất. */
    @Synchronized
    fun cancelLoad(callback: (NativeAd) -> Unit) {
        if (pendingCallback === callback) {
            pendingCallback = null
            pendingFailure = null
        }
    }

    /** Trả về callback cần báo, hoặc null nếu không ai chờ (khi đó [ad] được cache làm ad rảnh). */
    @Synchronized
    fun onAdLoaded(ad: NativeAd): ((NativeAd) -> Unit)? {
        loading = false
        val callback = pendingCallback
        pendingCallback = null
        pendingFailure = null
        if (callback == null) freeAd = Entry(ad, System.currentTimeMillis())
        return callback
    }

    /** Trả về callback lỗi cần báo, nếu subscriber đang chờ lượt load này có truyền vào. */
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

/** Native ad, cache theo placement để một màn hình không phải reload ad mỗi lần được hiển thị. */
object NativeAds {
    private val slots = mutableMapOf<NativePlacement, NativeAdSlot>()

    @Synchronized
    private fun slotFor(placement: NativePlacement) = slots.getOrPut(placement) { NativeAdSlot() }

    /** Chạy waterfall của [placement] ngay, trước khi màn nào cần tới, nếu chưa đang load hoặc chưa có cache. */
    fun preload(placement: NativePlacement) {
        if (!AdPolicy.nativeAllowed || !Ads.isReady) return
        val slot = slotFor(placement)
        if (slot.hasFresh(TTL_MS)) return
        startLoad(placement, slot, onReady = null, onFailed = null)
    }

    /**
     * Có phải sẽ không có ad nào cho [placement] hay không: ads đang tắt, SDK chưa sẵn sàng (consent bị
     * từ chối), hoặc lượt load gần nhất thất bại và không có lượt mới nào đang chạy. Dành cho các view
     * dùng [poll], để chúng ẩn đi thay vì shimmer mãi.
     */
    fun isUnavailable(placement: NativePlacement): Boolean =
        !AdPolicy.nativeAllowed || !Ads.isReady || slotFor(placement).failedAndIdle()

    /**
     * Lấy ad đã preload cho [placement], nếu đã sẵn sàng, để caller tự hiển thị và tự destroy
     * (không quản lý container/shimmer, khác với [subscribe]) - dành cho các placement full-screen
     * chỉ hiện một lần, cần ad nằm sẵn đó mà không phải chờ load. Ngay sau đó chạy reload để slot
     * được nạp lại cho lần sau. Trả về null nếu không preload kịp; xử lý như trường hợp no-fill.
     */
    fun poll(placement: NativePlacement): NativeAd? {
        if (!AdPolicy.nativeAllowed || !Ads.isReady) return null
        val ad = slotFor(placement).take(TTL_MS)
        preload(placement)
        return ad
    }

    /**
     * Gọi [onDone] khi [placement] đã có một ad mới nằm sẵn cho [poll] - gọi ngay nếu đã có cache,
     * nếu không thì sau khi load xong một ad - hoặc sau [timeoutMs] / khi load thất bại, tùy cái nào
     * đến trước. Dành cho các placement full-screen chỉ hiện một lần mà không có gì phía trước che
     * lúc load (vd. một break chỉ có native), để [poll] không về tay trắng. Luôn gọi [onDone] đúng một lần.
     */
    fun awaitReady(activity: Activity, placement: NativePlacement, timeoutMs: Long, onDone: () -> Unit) {
        val slot = slotFor(placement)
        if (!AdPolicy.nativeAllowed || !Ads.isReady || slot.hasFresh(TTL_MS)) { onDone(); return }
        var finished = false
        val finish = { if (!finished) { finished = true; onDone() } }
        val handle = request(activity, placement, onFailed = { finish() }) { ad -> slot.cache(ad); finish() }
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
        render: (Activity, NativeAd) -> NativeAdView = { a, ad -> renderTemplate(a, ad, templateFor(placement)) },
    ): AdHandle {
        if (!AdPolicy.nativeAllowed || !Ads.isReady) {
            container.visibility = View.GONE
            return AdHandle {}
        }
        var currentView: NativeAdView? = null
        var attachedAd: NativeAd? = null
        var shimmer: AdNativeShimmerBinding? = showShimmer(activity, container)

        val request = request(
            activity, placement,
            onFailed = {
                // Không có ad để hiện - ẩn slot đi thay vì để shimmer chạy mãi.
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
            // Ad này giờ đã được dùng - chạy load thật cho ad kế tiếp ngay để có sẵn ad
            // thay thế khi ad này bị đóng.
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
     * sau đó sẽ được cache cho người lấy tiếp theo. Là khối nền bên dưới [subscribe], dành cho các
     * caller tự quản lý view của mình.
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

    /** Hiện skeleton shimmer trong [container] để slot ad không bao giờ đột ngột hiện ra từ chỗ trống. */
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
        WaterfallLoader<NativeAd>(placement.unit.ids()).load(
            attempt = { id, loaded, failed ->
                val request = NativeAdRequest.Builder(id, listOf(NativeAd.NativeAdType.NATIVE)).build()
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
            onLoaded = { ad, _ -> slot.onAdLoaded(ad)?.invoke(ad) },
            onFailed = { error -> slot.onLoadFailed()?.invoke(error) },
        )
    }

    /** Template của [placement]: cột `native_template` của vị trí chứa unit đó; trống thì dùng mẫu mặc định. */
    fun templateFor(placement: NativePlacement): NativeAdTemplate =
        NativeAdTemplate.fromKey(AdPolicy.config(placement.unit.key).nativeTemplate)

    /** Inflate [template] và gắn [ad] vào đó - cho bất kỳ màn nào muốn dùng một template cụ thể. */
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
