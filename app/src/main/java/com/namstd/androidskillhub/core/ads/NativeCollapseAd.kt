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
 * "Native collapsible": một [NativeAd] hiển thị ở hai kích thước. Khi load xong nó mở dạng lớn, gắn
 * vào đáy content root của activity để - giống collapsible banner của chính AdMob - nó nổi đè lên
 * màn hình thay vì đẩy nội dung lên. Đóng nó thì chính ad đó được chuyển vào [container] thành một
 * dải cỡ banner. Ad chỉ register vào một view tại một thời điểm; trong lúc đang lớn, [container]
 * giữ shimmer để slot đã có sẵn kích thước khi dải nhỏ được đặt vào.
 *
 * Ad đến trong lúc activity chưa resume sẽ chờ tới onResume mới mở, để view lớn không bao giờ bật
 * lên phía sau một màn khác. Không có fill thì ẩn [container].
 *
 * Một lần reload (timer của [BannerSlotReloader], hoặc màn hình gọi [reload]) bỏ ad hiện tại - lớn
 * hay nhỏ - và đưa slot về shimmer nhỏ, rồi mở ad mới ở dạng lớn lần nữa. Shimmer được giữ ít nhất
 * [BannerSlotReloader.MIN_RELOAD_SHIMMER_MS]; reload thất bại thì ẩn slot cho tới lần reload kế tiếp.
 *
 * Gọi [start] một lần; owner destroy nó qua [AdHandle.destroy], việc này cũng destroy luôn ad.
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
    /** Ad đã load đang chờ mở dạng lớn - chờ hết shimmer tối thiểu của reload, hoặc chờ onResume. */
    private var pendingAd: NativeAd? = null
    private var destroyed = false
    private val handler = Handler(Looper.getMainLooper())
    private val reloader = BannerSlotReloader(activity, autoReload, reloadGapMs, ::reloadNow)
    /** Thời điểm shimmer của lượt load hiện tại được hiện lên, nếu nó phải giữ một thời gian tối thiểu (chỉ khi reload). */
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

    /** Mở [pendingAd] dạng lớn khi đã hết shimmer tối thiểu và activity đang resume. */
    private fun revealPending() {
        val ad = pendingAd ?: return
        handler.removeCallbacks(revealRunnable)
        val wait = minShimmerUntil - SystemClock.elapsedRealtime()
        if (wait > 0) {
            handler.postDelayed(revealRunnable, wait)
            return
        }
        if (!isResumed()) return // lifecycleObserver.onResume sẽ gọi lại vào đây.
        pendingAd = null
        showLarge(ad)
    }

    /** Từ đây ad đã lên màn hình - nên khoảng cách reload bắt đầu tính từ đây, không phải từ lúc load. */
    private fun showLarge(ad: NativeAd) {
        currentAd = ad
        val view = renderLarge(ad)
        overlay.removeAllViews()
        overlay.addView(view)
        attachOverlay()
        currentView = view
        state = State.LARGE
        onStatus("Native collapsible: large")
        // Ad này giờ đã được dùng - chuẩn bị sẵn ad kế tiếp cho màn (hoặc lần reload) tiếp theo cần tới.
        NativeAds.preload(placement)
        reloader.onShown()
    }

    override fun reload() = reloader.reloadNow()

    /** Reload: bỏ ad hiện tại, quay về shimmer nhỏ, và mở ad kế tiếp dạng lớn khi nó về. */
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

    /** Chuyển ad hiện tại từ overlay lớn vào [container] - giữ nguyên chính ad đó, không reload. */
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

    /** Không có ad để hiện - giải phóng hẳn slot thay vì để lại một dải trống. */
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
     * Gắn [overlay] vào đáy content root của [activity], nằm ngoài luồng layout riêng của mọi màn,
     * để nó nổi đè lên nội dung hiện tại thay vì đẩy nội dung lên. Tự chừa inset cho system bar phía
     * dưới vì nó nằm ngoài phần padding mà BaseActivity áp cho root của màn hình.
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
        // Window đã dispatch insets từ lâu trước khi overlay này được thêm vào, nên listener ở trên sẽ
        // không chạy cho tới lần thay đổi kế tiếp - set padding theo insets hiện tại ngay, rồi yêu cầu
        // dispatch lại để các thay đổi sau (xoay màn hình, đổi nav cử chỉ/3 nút) vẫn tới được nó.
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

    /** Card đầy đủ (icon + headline + media + body + CTA) với nút đóng ở góc để thu nó về [renderSmall]. */
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
            // collap_config: một số lần bấm X sẽ mở ad thay vì thu nhỏ - xem [CollapClickGate].
            if (gate.opensAd(++closeTapCount)) {
                onStatus("Native collapsible: X press $closeTapCount opens the ad")
                ad.recordCustomClickGesture()
            } else {
                showSmall()
            }
        }
        return view
    }

    /** Dải cỡ banner (icon + headline + body + CTA), không có media. */
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
        /** Số lần bấm X trên card collapsible lớn trong phiên app này - dùng chung cho mọi ad và màn (collap_config). */
        var closeTapCount = 0
    }
}
