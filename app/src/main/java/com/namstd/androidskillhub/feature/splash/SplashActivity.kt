package com.namstd.androidskillhub.feature.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.AdUnitIds
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.AppOpenAds
import com.namstd.androidskillhub.core.ads.FullScreenAdPlan
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.InterstitialState
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.SplashFallback
import com.namstd.androidskillhub.core.ads.resolveFullScreenPlan
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivitySplashBinding
import com.namstd.androidskillhub.feature.language.LanguageActivity
import com.namstd.androidskillhub.feature.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    companion object {
        private val MIN_DELAY_MS = 3_000L.milliseconds
        private val MAX_DELAY_MS = 8_000L.milliseconds
        private val CHECK_INTERVAL_MS = 2_00L.milliseconds
    }

    override fun inflateBinding(inflater: LayoutInflater): ActivitySplashBinding =
        ActivitySplashBinding.inflate(inflater)

    override fun initViews() {
        if (prefs.isPremium) {
            binding.bannerContainer.isVisible = false
            lifecycleScope.launch {
                delay(MIN_DELAY_MS)
                waitForRemoteConfig()
                showInterstitial(InterstitialPlacement.SPLASH, ::navigateToMain)
            }
            return
        }

        Ads.initialize(this, AdUnitIds.fromConfig()) { consentGranted ->
            runOnUiThread {
                if (consentGranted) {
                    loadBanner(binding.bannerContainer, inApp = false)
                    NativeAds.preload(NativePlacement.LANGUAGE)
                    // Interstitial ở Language/Onboarding cũng cần load sẵn ad từ trước, kể cả khi
                    // bản thân splash không hiện ad nào (ads_splash=0/1) - nếu không cái đầu tiên sẽ bị bỏ qua.
                    val inAppPlan = resolveFullScreenPlan(InterstitialPlacement.LANGUAGE, AdPolicy.enable)
                    if (inAppPlan == FullScreenAdPlan.INTERSTITIAL || inAppPlan == FullScreenAdPlan.INTER_PLUS_NATIVE) {
                        InterstitialAds.load()
                    }
                    when (resolveFullScreenPlan(InterstitialPlacement.SPLASH, AdPolicy.enable)) {
                        FullScreenAdPlan.APP_OPEN -> {
                            AppOpenAds.load()
                            lifecycleScope.launch { waitForAppOpenThenNavigate() }
                        }
                        FullScreenAdPlan.OFF -> lifecycleScope.launch {
                            delay(MIN_DELAY_MS)
                            waitForRemoteConfig()
                            navigateToMain()
                        }
                        else -> {
                            InterstitialAds.load()
                            lifecycleScope.launch { waitForAdThenNavigate() }
                        }
                    }
                } else {
                    // Tắt quảng cáo (Ads.adsEnabled = false) hoặc consent bị từ chối: không có ad nào để chờ, nhưng
                    // splash vẫn phải đi tiếp - nếu không user sẽ kẹt ở đây mãi.
                    binding.bannerContainer.isVisible = false
                    lifecycleScope.launch {
                        delay(MIN_DELAY_MS)
                        waitForRemoteConfig()
                        navigateToMain()
                    }
                }
            }
        }
    }

    private suspend fun waitForAdThenNavigate() {
        delay(MIN_DELAY_MS)
        var waitedMs = MIN_DELAY_MS
        while (InterstitialAds.state.value != InterstitialState.READY && waitedMs < MAX_DELAY_MS) {
            delay(CHECK_INTERVAL_MS)
            waitedMs += CHECK_INTERVAL_MS
        }
        waitForRemoteConfig(waitedMs)
        if (InterstitialAds.state.value != InterstitialState.READY) SplashFallback.arm()
        showInterstitial(InterstitialPlacement.SPLASH, ::navigateToMain)
    }

    /** Luồng App Open ở Splash (enable.ads_splash=1): chờ AOA trong giới hạn thời gian, hiện nó, rồi chuyển màn. */
    private suspend fun waitForAppOpenThenNavigate() {
        delay(MIN_DELAY_MS)
        var waitedMs = MIN_DELAY_MS
        while (!AppOpenAds.isLoaded() && waitedMs < MAX_DELAY_MS) {
            delay(CHECK_INTERVAL_MS)
            waitedMs += CHECK_INTERVAL_MS
        }
        waitForRemoteConfig(waitedMs)
        if (!AppOpenAds.show(this) { navigateToMain() }) {
            SplashFallback.arm()
            navigateToMain()
        }
    }

    /** Chờ (trong giới hạn delay dùng chung) cho Remote Config ổn định trước khi Home load banner của nó. */
    private suspend fun waitForRemoteConfig(waitedMs: Duration = MIN_DELAY_MS) {
        var elapsed = waitedMs
        while (!RemoteConfig.isReady.value && elapsed < MAX_DELAY_MS) {
            delay(CHECK_INTERVAL_MS)
            elapsed += CHECK_INTERVAL_MS
        }
    }

    private fun navigateToMain() {
        if (isFinishing) return
        val next = if (prefs.setupCompleted) MainActivity::class.java else LanguageActivity::class.java
        // splash_fallback chỉ bù ad Splash trên luồng Language/Intro - Home không có placement như vậy.
        if (prefs.setupCompleted) SplashFallback.clear()
        startActivity(Intent(this, next))
        finish()
    }

}
