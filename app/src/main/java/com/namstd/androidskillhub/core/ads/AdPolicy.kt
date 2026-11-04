package com.namstd.androidskillhub.core.ads

import android.content.Context
import com.namstd.androidskillhub.core.config.EnableConfig
import com.namstd.androidskillhub.core.config.InterDelayConfig
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.preferences.AppPreferences

/**
 * Nguồn duy nhất cho "user này được hiện quảng cáo gì". Code quảng cáo đọc ở đây, không đọc thẳng
 * [RemoteConfig] cho các giá trị dưới đây: [RemoteConfig] chỉ trả giá trị đúng như trên Firebase,
 * còn [AdPolicy] chọn giữa giá trị đó và [BasicAdProfile] theo [profile].
 */
object AdPolicy {
    private lateinit var prefs: AppPreferences

    fun initialize(context: Context) {
        prefs = AppPreferences.getInstance(context)
    }

    /** Tính lại mỗi lần đọc, nên đổi `ads_enable` hay có kết quả [AdAudience] mới là áp ngay. */
    val profile: AdProfile
        get() = resolveAdProfile(
            adsEnable = RemoteConfig.adsEnable,
            isPremium = prefs.isPremium,
            isMarketingUser = AdAudience.isMarketingUser,
            isTestAdDevice = AdAudience.isTestAdDevice,
        )

    /** Loại quảng cáo theo vị trí - xem [EnableConfig]. OFF = tắt hết. */
    val enable: EnableConfig
        get() = when (profile) {
            AdProfile.OFF -> EnableConfig.NONE
            AdProfile.REMOTE -> RemoteConfig.enable
            AdProfile.BASIC -> BasicAdProfile.enable
        }

    /** Gap giữa các interstitial - xem [RemoteConfig.interDelay]. */
    val interDelay: InterDelayConfig
        get() = if (profile == AdProfile.BASIC) BasicAdProfile.interDelay else RemoteConfig.interDelay

    /** Tần suất interstitial theo lần yêu cầu - xem [RemoteConfig.stepCount]. */
    val stepCount: Int
        get() = if (profile == AdProfile.BASIC) BasicAdProfile.STEP_COUNT else RemoteConfig.stepCount

    /** Native chỉ có ở [AdProfile.REMOTE]. */
    val nativeAllowed: Boolean
        get() = Ads.adsEnabled && profile == AdProfile.REMOTE

    /** [inApp] = false cho Splash/Language/Onboarding: [AdProfile.BASIC] chỉ có banner ở màn in-app. */
    fun bannerAllowed(inApp: Boolean): Boolean = when (profile) {
        AdProfile.OFF -> false
        AdProfile.REMOTE -> true
        AdProfile.BASIC -> inApp
    }
}
