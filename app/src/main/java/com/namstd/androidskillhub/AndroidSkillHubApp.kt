package com.namstd.androidskillhub

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.namstd.androidskillhub.core.ads.AdAudience
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.ads.AppOpenAds
import com.namstd.androidskillhub.core.ads.AdUnit
import com.namstd.androidskillhub.core.ads.FullScreenAdPlan
import com.namstd.androidskillhub.core.ads.InterstitialAds
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.fullScreenPlan
import com.namstd.androidskillhub.core.billing.Premium
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.AdType
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.preferences.AppPreferences
import com.namstd.androidskillhub.feature.welcome.WelcomeBackActivity
import java.lang.ref.WeakReference

class AndroidSkillHubApp : Application(), Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {
    private var currentActivity = WeakReference<Activity>(null)
    private var hasEnteredForeground = false

    override fun onCreate() {
        super<Application>.onCreate()
        val environment = if (BuildConfig.DEBUG) AdjustConfig.ENVIRONMENT_SANDBOX else AdjustConfig.ENVIRONMENT_PRODUCTION
        val adjustConfig = AdjustConfig(this, BuildConfig.ADJUST_APP_TOKEN, environment)
        adjustConfig.setOnAttributionChangedListener { AdAudience.onAttribution(it) }
        Adjust.initSdk(adjustConfig)
        AdAudience.initialize(this)
        AdPolicy.initialize(this)
        val preferences = AppPreferences.getInstance(this)
        preferences.applyLocale()
        Ads.adsEnabled = !preferences.isPremium
        // Đồng bộ Premium với Google Play (gói hết hạn / hoàn tiền thì bật lại quảng cáo từ lần mở sau).
        Premium.initialize(this)
        RemoteConfig.initialize(this)
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (hasEnteredForeground) {
            currentActivity.get()?.let { activity ->
                val preferences = AppPreferences.getInstance(this@AndroidSkillHubApp)
                if (preferences.setupCompleted) {
                    // on_resume: aoa = App Open; ad_type khác = màn Welcome Back (native chân màn + quảng cáo khi bấm
                    // Tiếp tục theo ad_type). Cả hai chịu open_gap_ms / open_inter_gap_ms.
                    when (AdPolicy.adType(AdKey.ON_RESUME)) {
                        null -> Unit // tắt: không hiện gì khi quay lại app
                        AdType.AOA -> Ads.onReturnToForeground(activity)
                        else -> WelcomeBackActivity.launch(activity)
                    }
                }
            }
        } else hasEnteredForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        // App xuống nền: tải sẵn quảng cáo cho lần quay lại (không tải sẵn thì lần quay lại đầu tiên không có gì để hiện).
        if (!Ads.adsEnabled) return
        when (AdPolicy.adType(AdKey.ON_RESUME)) {
            null -> Unit
            AdType.AOA -> AppOpenAds.load(AdUnit.ON_RESUME)
            else -> {
                // Welcome Back: native chân màn (`native_welcome_back`) + quảng cáo khi bấm Tiếp tục theo ad_type on_resume.
                if (AdPolicy.nativeEnabled(NativePlacement.WELCOME_BACK)) NativeAds.preload(NativePlacement.WELCOME_BACK)
                InterstitialAds.preload(InterstitialPlacement.RESUME)
                val plan = fullScreenPlan(InterstitialPlacement.RESUME)
                if (plan == FullScreenAdPlan.NATIVE_ONLY || plan == FullScreenAdPlan.INTER_PLUS_NATIVE) {
                    NativeAds.preload(NativePlacement.AFTER_INTER_RESUME)
                }
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity.get() === activity) currentActivity.clear()
    }
}
