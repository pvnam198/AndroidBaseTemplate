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
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.billing.Premium
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.PlacementFormat
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
                    when (AdPolicy.format(AdKey.ON_RESUME)) {
                        PlacementFormat.AOA -> Ads.onReturnToForeground(activity)
                        PlacementFormat.WELCOME_BACK -> WelcomeBackActivity.launch(activity)
                        else -> Unit // tắt: không hiện gì khi quay lại app
                    }
                }
            }
        } else hasEnteredForeground = true
    }

    override fun onStop(owner: LifecycleOwner) {
        // Hâm nóng native cho welcome back khi app xuống nền, để sẵn lúc quay lại.
        if (AdPolicy.format(AdKey.ON_RESUME) == PlacementFormat.WELCOME_BACK) NativeAds.preload(NativePlacement.WELCOME_BACK)
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
