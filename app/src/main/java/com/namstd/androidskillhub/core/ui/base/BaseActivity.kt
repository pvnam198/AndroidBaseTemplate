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
import com.namstd.androidskillhub.core.ads.BannerPlacement
import com.namstd.androidskillhub.core.ads.BannerSlotHandle
import com.namstd.androidskillhub.core.ads.FullScreenAdPlan
import com.namstd.androidskillhub.core.ads.FullScreenGate
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.SplashFallback
import com.namstd.androidskillhub.core.ads.fullScreenPlan
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
     * Load banner của [placement] vào [container] và tự huỷ nó trong [onDestroy]. Loại banner và timer
     * reload theo `enable` / `refresh_rate` của vị trí đó; [autoReload] = false cho màn tự reload bằng tay
     * (resume, đổi tab...) qua [reloadBanner]. Với user premium thì không load gì và [container] bị ẩn.
     */
    protected fun loadBanner(container: ViewGroup, placement: BannerPlacement, autoReload: Boolean? = null) {
        bannerContainer = container
        if (isPremiumUser()) {
            container.isVisible = false
            return
        }
        container.post {
            if (!isPremiumUser()) bannerHandle = BannerAds.load(this, container, placement, autoReload)
        }
    }

    /** Reload slot banner ngay (vd. khi đổi tab) - bỏ qua nếu nó đang load. */
    protected fun reloadBanner() {
        bannerHandle?.reload()
    }

    /**
     * Load native ad cho [placement] vào [container] và tự huỷ nó trong
     * [onDestroy]. Xem [NativeAds.subscribe] về [preloadOnShow]. Với user premium, hoặc khi `enable` của
     * vị trí tắt chỗ này ([AdPolicy.nativeEnabled]), thì không load gì và [container] bị ẩn.
     */
    protected fun loadNative(container: FrameLayout, placement: NativePlacement, preloadOnShow: Boolean = false) {
        nativeContainer = container
        if (isPremiumUser() || !AdPolicy.nativeEnabled(placement)) {
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
     * Chạy quảng cáo full-screen cho [placement] theo `enable` của vị trí đó (xem [fullScreenPlan]),
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
        val plan = fullScreenPlan(placement)
        if (plan == FullScreenAdPlan.OFF || plan == FullScreenAdPlan.APP_OPEN) { onComplete(); return }
        if (!passesInterstitialStepGate(placement)) { onComplete(); return }
        when (plan) {
            FullScreenAdPlan.INTERSTITIAL ->
                if (!InterstitialAds.show(this, placement, onComplete = onComplete)) onComplete()
            FullScreenAdPlan.NATIVE_ONLY -> {
                if (!Ads.adsEnabled || FullScreenGate.isBusy()) { onComplete(); return }
                // Không có interstitial phía trước để che thời gian load, nên đợi (có giới hạn) ad #1 trước -
                // nếu không, break sẽ poll một slot rỗng và đóng trước khi kịp hiện gì.
                NativeAds.awaitReady(this, placement.nativePlacement, NATIVE_ONLY_WAIT_MS) {
                    runNativeAdBreak(placement, onComplete)
                }
            }
            FullScreenAdPlan.INTER_PLUS_NATIVE -> {
                if (!Ads.adsEnabled || FullScreenGate.isBusy()) { onComplete(); return }
                NativeAds.preload(placement.nativePlacement)
                val afterInterstitial = { runNativeAdBreak(placement, onComplete) }
                if (!InterstitialAds.show(this, placement, onComplete = afterInterstitial)) afterInterstitial()
            }
            else -> onComplete()
        }
    }

    /**
     * Tần suất interstitial theo lần yêu cầu (`step_count` của vị trí): tăng bộ đếm riêng của vị trí (lưu qua các
     * lần mở app) và chỉ cho hiện ở lần chia hết cho step_count; các lần khác bỏ qua (gọi onComplete luôn).
     * step_count = 1 = mọi lần.
     */
    private fun passesInterstitialStepGate(placement: InterstitialPlacement): Boolean {
        val count = prefs.interstitialStepCounter(placement.key) + 1
        prefs.setInterstitialStepCounter(placement.key, count)
        return count % AdPolicy.config(placement.adKey).stepCount == 0
    }

    private fun runNativeAdBreak(placement: InterstitialPlacement, onComplete: () -> Unit) {
        if (isFinishing || isDestroyed) return
        supportFragmentManager.setFragmentResultListener(
            PostInterstitialNativeAdDialogFragment.REQUEST_KEY, this,
        ) { _, _ -> onComplete() }
        PostInterstitialNativeAdDialogFragment.newInstance(placement)
            .show(supportFragmentManager, PostInterstitialNativeAdDialogFragment.TAG)
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

/** Thời gian một native-only break (`enable` = 2 của vị trí inter) đợi ad đầu tiên trước khi bỏ cuộc và đi tiếp. */
private const val NATIVE_ONLY_WAIT_MS = 5_000L
