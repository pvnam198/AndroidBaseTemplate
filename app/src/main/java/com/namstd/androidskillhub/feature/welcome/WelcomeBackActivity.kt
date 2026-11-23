package com.namstd.androidskillhub.feature.welcome

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.AdGapGate
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.FullScreenAdPlan
import com.namstd.androidskillhub.core.ads.FullScreenGate
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.fullScreenPlan
import com.namstd.androidskillhub.core.ads.runAdCountdown
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityWelcomeBackBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Màn "Welcome back" khi quay lại app nếu `on_resume` có ad_type khác `aoa` (aoa = App Open). Base chưa có màn riêng
 * cho `custom_ui = true` nên mọi trường hợp đều dùng màn này. Mở qua [launch].
 * - Native ở chân màn: placement `native_welcome_back` (enable / ID / template); tắt hay không có ad thì màn vẫn hiện,
 *   chỉ không có native. Native không đẩy Tiếp tục ra khỏi màn (`AdFitFrame`, tối đa 60% chiều cao).
 * - Tiếp tục: đếm ngược `wait_ms` của `native_welcome_back` rồi mới bấm được; bấm thì chạy quảng cáo theo `ad_type` của
 *   `on_resume` (inter / native toàn màn / inter rồi native - xem [fullScreenPlan]), xong thì đóng màn.
 * - Không có native lẫn quảng cáo cho Tiếp tục thì không mở màn.
 */
class WelcomeBackActivity : BaseActivity<ActivityWelcomeBackBinding>() {
    private var countdown: Job? = null
    private var gateHeld = false
    private var canContinue = false

    override fun inflateBinding(inflater: LayoutInflater): ActivityWelcomeBackBinding =
        ActivityWelcomeBackBinding.inflate(inflater)

    override fun initViews() {
        if (isPremiumUser() || !FullScreenGate.acquire(this)) { finish(); return }
        gateHeld = true
        // Native tối đa 60% chiều cao nội dung: quảng cáo to (mẫu Full / media cao) không đẩy Tiếp tục ra khỏi màn.
        binding.content.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            binding.nativeContainer.maxHeightPx = ((v.height - v.paddingTop - v.paddingBottom) * NATIVE_MAX_SHARE).toInt()
        }
        // Native lỗi / không có fill: nếu cũng không có quảng cáo cho Tiếp tục thì đóng luôn, không để màn trống.
        loadNative(binding.nativeContainer, NativePlacement.WELCOME_BACK, onUnavailable = {
            if (!hasContinueAd() && !isFinishing) finish()
        })
        startCountdown()
    }

    override fun initListeners() {
        binding.btnContinue.setOnClickListener { onContinue() }
        // Chặn Back trong lúc đếm ngược để user không bỏ qua native trước khi hết wait_ms.
        onBackPressedDispatcher.addCallback(this) { onContinue() }
    }

    /**
     * Tiếp tục: chạy quảng cáo của on_resume theo ad_type (đè lên màn này) rồi đóng màn. Đây là một phần của lượt quảng
     * cáo quay lại app (đã qua `open_gap_ms`) nên không xét `inter_gap_ms` / `open_inter_gap_ms` nữa.
     */
    private fun onContinue() {
        if (!canContinue) return
        canContinue = false
        binding.btnContinue.isEnabled = false
        releaseGate() // quảng cáo toàn màn cần FullScreenGate
        showInterstitial(InterstitialPlacement.RESUME, ignoreGap = true) { if (!isFinishing) finish() }
    }

    /** Tiếp tục chỉ bấm được khi hết `wait_ms` của native_welcome_back; trong lúc chờ hiện số giây còn lại. */
    private fun startCountdown() {
        // Không có native thì không bắt chờ.
        val total = if (AdPolicy.nativeEnabled(NativePlacement.WELCOME_BACK)) AdPolicy.config(AdKey.NATIVE_WELCOME_BACK).waitMs else 0L
        binding.btnContinue.isEnabled = false
        countdown = lifecycleScope.launch {
            runAdCountdown(total) { second ->
                binding.btnContinue.text = getString(R.string.welcome_back_continue_countdown, second)
            }
            binding.btnContinue.setText(R.string.welcome_back_continue)
            binding.btnContinue.isEnabled = true
            canContinue = true
        }
    }

    override fun releaseResources() {
        countdown?.cancel()
        countdown = null
        releaseGate()
        showing = false
    }

    private fun releaseGate() {
        if (gateHeld) {
            FullScreenGate.release(this)
            gateHeld = false
        }
    }

    companion object {
        private const val NATIVE_MAX_SHARE = 0.6f

        @Volatile private var showing = false

        /** `ad_type` của on_resume có quảng cáo cho nút Tiếp tục không (inter / native toàn màn / inter rồi native). */
        private fun hasContinueAd() = fullScreenPlan(InterstitialPlacement.RESUME).let {
            it == FullScreenAdPlan.INTERSTITIAL || it == FullScreenAdPlan.NATIVE_ONLY || it == FullScreenAdPlan.INTER_PLUS_NATIVE
        }

        /**
         * Mở màn Welcome Back trên [activity], trừ khi: đang hiện 1 cái / có quảng cáo full-screen khác đang bận / quảng
         * cáo tắt / không có native lẫn quảng cáo cho Tiếp tục / chưa đủ `open_gap_ms` / `open_inter_gap_ms` (màn này tính
         * như quảng cáo quay lại app, giống App Open - log debug tag `AdsGap`).
         */
        fun launch(activity: Activity) {
            if (showing || FullScreenGate.isBusy() || !Ads.adsEnabled) return
            val hasNative = AdPolicy.nativeEnabled(NativePlacement.WELCOME_BACK) &&
                !NativeAds.isUnavailable(NativePlacement.WELCOME_BACK)
            if (!hasNative && !hasContinueAd()) return
            val gaps = AdPolicy.openGaps
            if (!AdGapGate.canShowAppOpen(gaps.openGapMs, gaps.openInterGapMs)) {
                if (BuildConfig.DEBUG) Log.d("AdsGap", "on_resume Welcome Back: bỏ qua, chưa đủ khoảng cách $gaps")
                return
            }
            if (BuildConfig.DEBUG) Log.d("AdsGap", "on_resume Welcome Back: hiện")
            AdGapGate.recordAppOpenShown()
            showing = true
            activity.startActivity(Intent(activity, WelcomeBackActivity::class.java))
        }
    }
}
