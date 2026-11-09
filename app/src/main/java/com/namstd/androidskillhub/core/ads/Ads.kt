package com.namstd.androidskillhub.core.ads

import android.app.Activity
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.namstd.androidskillhub.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lifecycle consent/init và config dùng chung cho mọi định dạng ad. Logic load/show riêng của từng
 * định dạng nằm trong file riêng (BannerAds, NativeAds, InterstitialAds, RewardAds,
 * AppOpenAds) - object này chỉ điều phối khởi động/dọn dẹp.
 */
object Ads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readyCallbacks = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private var initializationStarted = false
    private var sdkInitializationScheduled = false
    private var ready = false
    private var consentInformation: ConsentInformation? = null

    @Volatile
    var adsEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) clearAll()
        }

    val isReady: Boolean get() = ready
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation?.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun initialize(
        activity: Activity,
        onReady: (Boolean) -> Unit = {},
    ) {
        if (!adsEnabled) {
            onReady(false); return
        }
        if (ready) {
            onReady(true); return
        }
        readyCallbacks += onReady
        if (initializationStarted) return
        initializationStarted = true
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = consent
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                initializeIfAllowed(activity, consent)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initializeIfAllowed(activity, consent)
                    if (!consent.canRequestAds()) finishInitialization(false)
                }
            },
            {
                if (consent.canRequestAds()) initializeSdk(activity) else finishInitialization(false)
            },
        )
    }

    private fun initializeIfAllowed(activity: Activity, consent: ConsentInformation) {
        if (consent.canRequestAds()) initializeSdk(activity)
    }

    @Synchronized
    private fun initializeSdk(activity: Activity) {
        if (ready || MobileAds.isInitialized) {
            finishInitialization(true)
            return
        }
        if (sdkInitializationScheduled) return
        sdkInitializationScheduled = true
        scope.launch {
            MobileAds.initialize(
                activity.applicationContext,
                InitializationConfig.Builder(activity.getString(R.string.admob_app_id)).build(),
            ) { activity.runOnUiThread { finishInitialization(true) } }
        }
    }

    @Synchronized
    private fun finishInitialization(success: Boolean) {
        if (success && !ready) {
            ready = true
        }
        val callbacks = readyCallbacks.toList()
        readyCallbacks.clear()
        callbacks.forEach { it(success) }
    }

    fun showPrivacyOptions(
        activity: Activity,
        onReady: (Boolean) -> Unit = {},
        onDismissed: (String?) -> Unit = {},
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (consentInformation?.canRequestAds() == true) {
                readyCallbacks += onReady
                initializeSdk(activity)
            } else {
                onReady(false)
            }
            onDismissed(error?.message)
        }
    }

    fun onReturnToForeground(activity: Activity) {
        if (!adsEnabled) return
        AppOpenAds.onReturnToForeground(activity)
    }

    private fun clearAll() {
        InterstitialAds.clear()
        RewardAds.clear()
        AppOpenAds.clear()
        NativeAds.clear()
    }
}
