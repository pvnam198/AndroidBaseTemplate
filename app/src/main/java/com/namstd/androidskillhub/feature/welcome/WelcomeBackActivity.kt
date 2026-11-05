package com.namstd.androidskillhub.feature.welcome

import android.app.Activity
import android.content.Intent
import android.os.CountDownTimer
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.FullScreenGate
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityWelcomeBackBinding
import kotlin.math.ceil

/**
 * Màn "Welcome back" khi quay lại app nếu `enable.onresume=2` (thay cho App Open ad).
 * Hiện 1 native ad + đếm ngược `wait_time` của `native_welcome_back` rồi mới cho đóng. Mở qua [launch].
 */
class WelcomeBackActivity : BaseActivity<ActivityWelcomeBackBinding>() {
    private var countdown: CountDownTimer? = null
    private var gateHeld = false

    override fun inflateBinding(inflater: LayoutInflater): ActivityWelcomeBackBinding =
        ActivityWelcomeBackBinding.inflate(inflater)

    override fun initViews() {
        if (!FullScreenGate.acquire(this)) { finish(); return }
        gateHeld = true
        loadNative(binding.nativeContainer, NativePlacement.WELCOME_BACK)
        startCountdown()
    }

    override fun initListeners() {
        binding.ivClose.setOnClickListener { finish() }
    }

    private fun startCountdown() {
        val total = AdPolicy.config(AdKey.NATIVE_WELCOME_BACK).waitTimeMs
        if (total <= 0L) {
            binding.ivClose.isVisible = true
            return
        }
        binding.tvCountdown.isVisible = true
        binding.ivClose.isVisible = false
        countdown = object : CountDownTimer(total, 100L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvCountdown.text = ceil(millisUntilFinished / 1000.0).toInt().toString()
            }
            override fun onFinish() {
                binding.tvCountdown.isVisible = false
                binding.ivClose.isVisible = true
            }
        }.start()
    }

    override fun releaseResources() {
        countdown?.cancel()
        countdown = null
        if (gateHeld) {
            FullScreenGate.release(this)
            gateHeld = false
        }
        showing = false
    }

    companion object {
        @Volatile private var showing = false

        /**
         * Mở màn welcome back trên [activity], trừ khi: đang hiện 1 cái / có quảng cáo full-screen khác đang bận /
         * quảng cáo tắt / không có native nào cho [NativePlacement.WELCOME_BACK].
         */
        fun launch(activity: Activity) {
            if (showing || FullScreenGate.isBusy() || !Ads.adsEnabled) return
            if (NativeAds.isUnavailable(NativePlacement.WELCOME_BACK)) return
            showing = true
            activity.startActivity(Intent(activity, WelcomeBackActivity::class.java))
        }
    }
}
