package com.namstd.androidskillhub.core.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.lang.ref.WeakReference
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
        appContext = activity.applicationContext
        trackResumedActivity(activity)
        consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        logInit("ump: xin trạng thái đồng ý")
        requestConsent(activity, attempt = 0)
    }

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var resumedActivity: WeakReference<Activity>? = null

    /** Việc chờ Activity kế tiếp hiện lên (tới hạn thử lại mà không có màn nào đang mở). */
    private var onNextResume: ((Activity) -> Unit)? = null

    /** Activity đang hiện, để lần thử lại sau (khi Splash đã đóng) vẫn có Activity cho UMP / form đồng ý. */
    private fun trackResumedActivity(activity: Activity) {
        resumedActivity = WeakReference(activity)
        activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                resumedActivity = WeakReference(a)
                onNextResume?.let { onNextResume = null; it(a) }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    private fun liveActivity(): Activity? = resumedActivity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    private fun withLiveActivity(action: (Activity) -> Unit) {
        val activity = liveActivity()
        if (activity != null) action(activity) else onNextResume = action
    }

    /**
     * Hỏi UMP có cần form đồng ý không. Lỗi mạng mà máy chưa có trạng thái đồng ý nào (cài mới / xoá dữ liệu) thì
     * `canRequestAds()` = false: lần lỗi đầu báo false cho Splash đi tiếp, rồi thử lại sau 2s / 4s / 8s và khi mạng
     * có lại ([NetworkRegain]). Không bỏ qua bước đồng ý - chỉ gửi lại câu hỏi; user EU vẫn thấy form.
     */
    private fun requestConsent(activity: Activity, attempt: Int) {
        val consent = consentInformation ?: return
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                if (attempt > 0) logInit("ump ok sau ${attempt + 1} lần")
                initializeIfAllowed(activity, consent)
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initializeIfAllowed(activity, consent)
                    if (!consent.canRequestAds()) {
                        logInit("ump: user không đồng ý, không có quảng cáo")
                        finishInitialization(false)
                    }
                }
            },
            { error ->
                if (consent.canRequestAds()) {
                    // Đã có trạng thái đồng ý từ lần trước: khởi tạo SDK như bình thường.
                    initializeSdk(activity)
                    return@requestConsentInfoUpdate
                }
                if (attempt == 0) finishInitialization(false)
                retryConsent(attempt, error?.message)
            },
        )
    }

    private fun retryConsent(attempt: Int, error: String?) {
        val delay = retryDelay(attempt)
        logInit("ump lỗi lần ${attempt + 1} ($error)" + if (delay != null) ", thử lại sau ${delay}ms" else ", hết lượt")
        if (delay != null) main.postDelayed({ retryConsentNow(attempt + 1) }, delay)
        // Hết lượt: chờ mạng có lại rồi chạy lại lịch thử (từ lần thứ 2 để không báo Splash lần nữa).
        else appContext?.let { NetworkRegain.awaitOnce(it, "ump") { retryConsentNow(attempt = 1) } }
    }

    private fun retryConsentNow(attempt: Int) {
        if (!adsEnabled || ready) return
        withLiveActivity { if (adsEnabled && !ready) requestConsent(it, attempt) }
    }

    private fun initializeIfAllowed(activity: Activity, consent: ConsentInformation) {
        if (consent.canRequestAds()) initializeSdk(activity)
    }

    @Synchronized
    private fun initializeSdk(context: Context) {
        if (ready || MobileAds.isInitialized) {
            finishInitialization(true)
            return
        }
        if (sdkInitializationScheduled) return
        sdkInitializationScheduled = true
        scope.launch {
            MobileAds.initialize(
                context.applicationContext,
                InitializationConfig.Builder(context.getString(R.string.admob_app_id)).build(),
            ) { main.post { finishInitialization(true) } }
        }
    }

    @Synchronized
    private fun finishInitialization(success: Boolean) {
        if (success && !ready) {
            ready = true
            logInit("sdk sẵn sàng")
            AdsAvailability.notifyChanged()
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
        AdsAvailability.clear()
        InterstitialAds.clear()
        RewardAds.clear()
        AppOpenAds.clear()
        NativeAds.clear()
    }
}
