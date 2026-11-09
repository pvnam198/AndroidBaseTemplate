package com.namstd.androidskillhub.feature.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.namstd.androidskillhub.core.ads.AdUnit
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.AppOpenAds
import com.namstd.androidskillhub.core.ads.FullScreenAdPlan
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.SplashFallback
import com.namstd.androidskillhub.core.ads.fullScreenPlan
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
        private val CHECK_INTERVAL_MS = 2_00L.milliseconds
    }

    /** Chờ tối thiểu / tối đa (`splash_min_ms` / `splash_max_ms`) - chốt một lần lúc mở Splash. */
    private val minDelay = RemoteConfig.splashMinMs.milliseconds
    private val maxDelay = RemoteConfig.splashMaxMs.milliseconds

    override fun inflateBinding(inflater: LayoutInflater): ActivitySplashBinding =
        ActivitySplashBinding.inflate(inflater)

    override fun initViews() {
        // Bấm icon khi app đang chạy nền: hệ thống có thể tạo lại Splash ở trên màn đang mở (vd. MIUI). Khi đó
        // chỉ cần đưa app lên lại - đóng Splash này, không chạy lại luồng Splash (quảng cáo, mở thêm màn chính
        // đè lên Welcome back).
        if (!isTaskRoot && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        if (prefs.isPremium) {
            lifecycleScope.launch {
                delay(minDelay)
                waitForRemoteConfig()
                showInterstitial(InterstitialPlacement.SPLASH, ::navigateToMain)
            }
            return
        }

        Ads.initialize(this) { consentGranted ->
            runOnUiThread {
                if (consentGranted) {
                    binding.nativeContainer.isVisible = true
                    loadNative(binding.nativeContainer, NativePlacement.SPLASH)
                    NativeAds.preload(NativePlacement.LANGUAGE)
                    // Interstitial ở Language cũng cần load sẵn ad từ trước, kể cả khi bản thân splash
                    // không hiện ad nào (ads_splash=0/1) - nếu không cái đầu tiên sẽ bị bỏ qua.
                    InterstitialAds.preload(InterstitialPlacement.LANGUAGE)
                    when (fullScreenPlan(InterstitialPlacement.SPLASH)) {
                        FullScreenAdPlan.APP_OPEN -> {
                            AppOpenAds.load(AdUnit.AOA_SPLASH)
                            lifecycleScope.launch { waitForAppOpenThenNavigate() }
                        }
                        FullScreenAdPlan.OFF -> lifecycleScope.launch {
                            delay(minDelay)
                            waitForRemoteConfig()
                            navigateToMain()
                        }
                        else -> {
                            InterstitialAds.load(AdUnit.INTER_SPLASH)
                            lifecycleScope.launch { waitForAdThenNavigate() }
                        }
                    }
                } else {
                    // Tắt quảng cáo (Ads.adsEnabled = false) hoặc consent bị từ chối: không có ad nào để chờ, nhưng
                    // splash vẫn phải đi tiếp - nếu không user sẽ kẹt ở đây mãi.
                    lifecycleScope.launch {
                        delay(minDelay)
                        waitForRemoteConfig()
                        navigateToMain()
                    }
                }
            }
        }
    }

    private suspend fun waitForAdThenNavigate() {
        delay(minDelay)
        var waitedMs = minDelay
        while (!InterstitialAds.isReady(AdUnit.INTER_SPLASH) && waitedMs < maxDelay) {
            delay(CHECK_INTERVAL_MS)
            waitedMs += CHECK_INTERVAL_MS
        }
        waitForRemoteConfig(waitedMs)
        if (!InterstitialAds.isReady(AdUnit.INTER_SPLASH)) SplashFallback.arm()
        showInterstitial(InterstitialPlacement.SPLASH, ::navigateToMain)
    }

    /** Luồng App Open ở Splash (ads_splash = 1): chờ AOA trong giới hạn thời gian, hiện nó, rồi chuyển màn. */
    private suspend fun waitForAppOpenThenNavigate() {
        delay(minDelay)
        var waitedMs = minDelay
        while (!AppOpenAds.isLoaded(AdUnit.AOA_SPLASH) && waitedMs < maxDelay) {
            delay(CHECK_INTERVAL_MS)
            waitedMs += CHECK_INTERVAL_MS
        }
        waitForRemoteConfig(waitedMs)
        if (!AppOpenAds.show(this, AdUnit.AOA_SPLASH) { navigateToMain() }) {
            SplashFallback.arm()
            navigateToMain()
        }
    }

    /** Chờ (trong giới hạn delay dùng chung) cho Remote Config ổn định trước khi Home load banner của nó. */
    private suspend fun waitForRemoteConfig(waitedMs: Duration = minDelay) {
        var elapsed = waitedMs
        while (!RemoteConfig.isReady.value && elapsed < maxDelay) {
            delay(CHECK_INTERVAL_MS)
            elapsed += CHECK_INTERVAL_MS
        }
    }

    private fun navigateToMain() {
        if (isFinishing) return
        val next = if (prefs.setupCompleted) MainActivity::class.java else LanguageActivity::class.java
        // splash_fallback chỉ bù ad Splash trên luồng Language/Onboarding - Home không có placement như vậy.
        if (prefs.setupCompleted) SplashFallback.clear()
        startActivity(Intent(this, next))
        finish()
    }

}
