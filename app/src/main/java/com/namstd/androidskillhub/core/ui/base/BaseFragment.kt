package com.namstd.androidskillhub.core.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.namstd.androidskillhub.core.ads.AdHandle
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.BannerAds
import com.namstd.androidskillhub.core.ads.BannerPlacement
import com.namstd.androidskillhub.core.ads.BannerSlotHandle
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.RewardAds
import com.namstd.androidskillhub.core.ads.RewardPlacement
import com.namstd.androidskillhub.core.ads.RewardResult
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.preferences.AppPreferences

abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    private var bannerHandle: BannerSlotHandle? = null
    private var nativeHandle: AdHandle? = null
    private var bannerContainer: ViewGroup? = null
    private var nativeContainer: FrameLayout? = null

    protected val binding: VB
        get() = checkNotNull(_binding) {
            "ViewBinding is only available between onCreateView and onDestroyView"
        }

    protected val prefs: AppPreferences get() = AppPreferences.getInstance(requireContext())

    /**
     * Load banner của [placement] vào [container] và tự huỷ nó khi view bị huỷ. Loại banner và timer reload
     * theo `format` / `refresh_ms` của placement đó; [autoReload] = false cho màn tự reload bằng tay qua
     * [reloadBanner]. Với user premium thì không load gì và [container] bị ẩn.
     */
    protected fun loadBanner(container: ViewGroup, placement: BannerPlacement, autoReload: Boolean? = null) {
        bannerContainer = container
        if (isPremiumUser()) {
            container.isVisible = false
            return
        }
        container.post {
            if (_binding != null && !isPremiumUser()) {
                bannerHandle = BannerAds.load(requireActivity(), container, placement, autoReload)
            }
        }
    }

    /** Reload slot banner ngay (vd. khi đổi tab) - bỏ qua nếu nó đang load. */
    protected fun reloadBanner() {
        bannerHandle?.reload()
    }

    /**
     * Load native ad cho [placement] vào [container] và tự huỷ nó khi
     * view bị huỷ. Xem [NativeAds.subscribe] về [preloadOnShow]. Với user premium thì không
     * load gì và [container] bị ẩn.
     */
    protected fun loadNative(container: FrameLayout, placement: NativePlacement, preloadOnShow: Boolean = false) {
        nativeContainer = container
        if (isPremiumUser() || !AdPolicy.nativeEnabled(placement)) {
            container.isVisible = false
            return
        }
        container.post {
            if (_binding != null && !isPremiumUser()) {
                nativeHandle = NativeAds.subscribe(requireActivity(), placement, container, preloadOnShow)
            }
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

    /** Uỷ quyền cho [BaseActivity] đang chứa fragment - chỉ một implementation cho toàn app, xem ở đó. */
    protected fun showInterstitial(placement: InterstitialPlacement, onComplete: () -> Unit) {
        (requireActivity() as BaseActivity<*>).showInterstitial(placement, onComplete)
    }

    /** Uỷ quyền cho [BaseActivity.showReward] - xem ở đó. */
    protected fun showReward(placement: RewardPlacement, onResult: (RewardResult) -> Unit) {
        (requireActivity() as BaseActivity<*>).showReward(placement, onResult)
    }

    protected fun preloadReward(placement: RewardPlacement) = RewardAds.preload(placement)

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    protected open fun initConfig(savedInstanceState: Bundle?) = Unit

    protected open fun initViews() = Unit

    protected open fun initListeners() = Unit

    protected open fun observeData() = Unit

    protected open fun releaseResources() = Unit

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initConfig(savedInstanceState)
        initViews()
        initListeners()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        onPremiumUser(isPremiumUser())
    }

    final override fun onDestroyView() {
        try {
            releaseResources()
        } finally {
            bannerHandle?.destroy()
            bannerHandle = null
            nativeHandle?.destroy()
            nativeHandle = null
            bannerContainer = null
            nativeContainer = null
            _binding = null
            super.onDestroyView()
        }
    }
}
