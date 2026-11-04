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
                    // The Language/Onboarding interstitial needs an ad loaded up front too, even when
                    // the splash itself shows none (ads_splash=0/1) - else the first one is skipped.
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
                    // Ads off (Ads.adsEnabled = false) or consent denied: no ads to wait for, but the
                    // splash must still move on - otherwise the user is stuck here for good.
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
        showInterstitial(InterstitialPlacement.SPLASH, ::navigateToMain)
    }

    /** Splash App Open flow (enable.ads_splash=1): wait for the AOA within the budget, show it, then navigate. */
    private suspend fun waitForAppOpenThenNavigate() {
        delay(MIN_DELAY_MS)
        var waitedMs = MIN_DELAY_MS
        while (!AppOpenAds.isLoaded() && waitedMs < MAX_DELAY_MS) {
            delay(CHECK_INTERVAL_MS)
            waitedMs += CHECK_INTERVAL_MS
        }
        waitForRemoteConfig(waitedMs)
        if (!AppOpenAds.show(this) { navigateToMain() }) navigateToMain()
    }

    /** Waits (within the shared delay budget) for Remote Config to settle before Home loads its banner. */
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
        startActivity(Intent(this, next))
        finish()
    }

}
