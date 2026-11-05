package com.namstd.androidskillhub.core.ui.base

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.viewbinding.ViewBinding
import com.namstd.androidskillhub.core.ads.AdHandle
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.BannerAds
import com.namstd.androidskillhub.core.ads.BannerSlotHandle
import com.namstd.androidskillhub.core.ads.FullScreenAdPlan
import com.namstd.androidskillhub.core.ads.FullScreenGate
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.SplashFallback
import com.namstd.androidskillhub.core.ads.resolveFullScreenPlan
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.preferences.AppPreferences
import com.namstd.androidskillhub.feature.ads.PostInterstitialNativeAdDialogFragment

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    protected lateinit var binding: VB
        private set

    protected val prefs: AppPreferences get() = AppPreferences.getInstance(this)

    private var bannerHandle: BannerSlotHandle? = null
    private var nativeHandle: AdHandle? = null
    private var bannerContainer: ViewGroup? = null
    private var nativeContainer: FrameLayout? = null

    /**
     * Load banner vào [container] và tự huỷ nó trong [onDestroy].
     * [autoReload] / [reloadGapMs] đặt bộ hẹn giờ reload, mặc định lấy theo Remote Config
     * ([RemoteConfig.bannerSlotAutoReload] / [RemoteConfig.refreshRateMs]); màn hình có thể
     * truyền giá trị riêng. Mọi kiểu reload khác (resume, đổi tab...) là code riêng của màn hình
     * gọi [reloadBanner]. Với user premium thì không load gì và [container] bị ẩn.
     * [inApp] = false cho Splash/Language/Onboarding - xem [AdPolicy.bannerAllowed].
     */
    protected fun loadBanner(
        container: ViewGroup,
        autoReload: Boolean = RemoteConfig.bannerSlotAutoReload,
        reloadGapMs: Long = RemoteConfig.refreshRateMs,
        inApp: Boolean = true,
    ) {
        bannerContainer = container
        if (isPremiumUser() || !AdPolicy.bannerAllowed(inApp)) {
            container.isVisible = false
            return
        }
        container.post {
            if (!isPremiumUser()) bannerHandle = BannerAds.load(this, container, autoReload, reloadGapMs)
        }
    }

    /** Reload slot banner ngay (vd. khi đổi tab) - bỏ qua nếu nó đang load. */
    protected fun reloadBanner() {
        bannerHandle?.reload()
    }

    /**
     * Load native ad cho [placement] vào [container] và tự huỷ nó trong
     * [onDestroy]. Xem [NativeAds.subscribe] về [preloadOnShow]. Với user premium thì không load gì
     * và [container] bị ẩn.
     */
    protected fun loadNative(container: FrameLayout, placement: NativePlacement, preloadOnShow: Boolean = false) {
        nativeContainer = container
        if (isPremiumUser()) {
            container.isVisible = false
            return
        }
        container.post {
            if (!isPremiumUser()) nativeHandle = NativeAds.subscribe(this, placement, container, preloadOnShow)
        }
    }

    /**
     * Chạy ở mỗi lần onResume, kèm cờ user có premium hay không - giống base của app tham chiếu - để
     * màn hình đang mở hoặc nằm trong back stack lúc user nâng cấp bỏ quảng cáo ngay khi user quay
     * lại. Mặc định huỷ các ad [loadBanner] / [loadNative] của màn này và ẩn container của chúng;
     * màn hình có quảng cáo riêng (feed, dialog...) override hàm này để ẩn cả chúng, có gọi super.
     */
    protected open fun onPremiumUser(premium: Boolean) {
        if (premium) hideAds()
    }

    protected fun isPremiumUser(): Boolean = prefs.isPremium

    private fun hideAds() {
        bannerHandle?.destroy()
        bannerHandle = null
        nativeHandle?.destroy()
        nativeHandle = null
        bannerContainer?.isVisible = false
        nativeContainer?.isVisible = false
    }

    /**
     * Chạy quảng cáo full-screen cho [placement] theo [AdPolicy.enable] (xem [resolveFullScreenPlan]),
     * rồi gọi [onComplete]. Loại: plain interstitial / interstitial + native break / native-only / tắt.
     * (APP_OPEN chỉ ở SPLASH và do SplashActivity lo; ở đây coi như tắt.)
     * Chỉ một implementation cho toàn app - [BaseFragment.showInterstitial] uỷ quyền về đây.
     */
    internal fun showInterstitial(placement: InterstitialPlacement, onComplete: () -> Unit) {
        if (SplashFallback.take(placement)) {
            // Splash bị lỡ quảng cáo: placement này bù lại bằng một plain interstitial,
            // bất kể plan, step_count hay gap của chính nó nói gì.
            if (!InterstitialAds.show(this, placement, ignoreGap = true, onComplete = onComplete)) onComplete()
            return
        }
        val plan = resolveFullScreenPlan(placement, AdPolicy.enable)
        if (plan == FullScreenAdPlan.OFF || plan == FullScreenAdPlan.APP_OPEN) { onComplete(); return }
        if (!passesInterstitialStepGate()) { onComplete(); return }
        when (plan) {
            FullScreenAdPlan.INTERSTITIAL ->
                if (!InterstitialAds.show(this, placement, onComplete = onComplete)) onComplete()
            FullScreenAdPlan.NATIVE_ONLY -> {
                if (!Ads.adsEnabled || FullScreenGate.isBusy()) { onComplete(); return }
                // Không có interstitial phía trước để che thời gian load, nên đợi (có giới hạn) ad #1 trước -
                // nếu không, break sẽ poll một slot rỗng và đóng trước khi kịp hiện gì.
                NativeAds.awaitReady(this, NativePlacement.POST_INTERSTITIAL, NATIVE_ONLY_WAIT_MS) {
                    runNativeAdBreak(onComplete)
                }
            }
            FullScreenAdPlan.INTER_PLUS_NATIVE -> {
                if (!Ads.adsEnabled || FullScreenGate.isBusy()) { onComplete(); return }
                NativeAds.preload(NativePlacement.POST_INTERSTITIAL)
                val afterInterstitial = { runNativeAdBreak(onComplete) }
                if (!InterstitialAds.show(this, placement, onComplete = afterInterstitial)) afterInterstitial()
            }
            else -> onComplete()
        }
    }

    /**
     * Tần suất interstitial theo lần yêu cầu ([AdPolicy.stepCount]): tăng bộ đếm (lưu qua các lần mở app)
     * và chỉ cho hiện ở lần chia hết cho stepCount; các lần khác bỏ qua (gọi onComplete luôn). stepCount=1 = mọi lần.
     */
    private fun passesInterstitialStepGate(): Boolean {
        val count = prefs.interstitialStepCounter + 1
        prefs.interstitialStepCounter = count
        return count % AdPolicy.stepCount == 0
    }

    private fun runNativeAdBreak(onComplete: () -> Unit) {
        if (isFinishing || isDestroyed) return
        supportFragmentManager.setFragmentResultListener(
            PostInterstitialNativeAdDialogFragment.REQUEST_KEY, this,
        ) { _, _ -> onComplete() }
        PostInterstitialNativeAdDialogFragment().show(supportFragmentManager, PostInterstitialNativeAdDialogFragment.TAG)
    }

    protected abstract fun inflateBinding(inflater: LayoutInflater): VB

    protected open fun initConfig(savedInstanceState: Bundle?) = Unit

    protected open fun initViews() = Unit

    protected open fun initListeners() = Unit

    protected open fun observeData() = Unit

    protected open fun releaseResources() = Unit

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        applySystemBarPadding(binding.root)
        initConfig(savedInstanceState)
        initViews()
        initListeners()
        observeData()
    }

    /** Thêm padding cho root view để nội dung không bị vẽ dưới status/navigation bar (edge-to-edge bị ép từ SDK 35). */
    private fun applySystemBarPadding(root: View) {
        val initialPadding = Rect(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = initialPadding.left + bars.left,
                top = initialPadding.top + bars.top,
                right = initialPadding.right + bars.right,
                bottom = initialPadding.bottom + bars.bottom,
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        onPremiumUser(isPremiumUser())
    }

    final override fun onDestroy() {
        try {
            releaseResources()
        } finally {
            bannerHandle?.destroy()
            bannerHandle = null
            nativeHandle?.destroy()
            nativeHandle = null
            bannerContainer = null
            nativeContainer = null
            super.onDestroy()
        }
    }
}

/** Thời gian một native-only break (enable.inter=2) đợi ad đầu tiên trước khi bỏ cuộc và đi tiếp. */
private const val NATIVE_ONLY_WAIT_MS = 5_000L
