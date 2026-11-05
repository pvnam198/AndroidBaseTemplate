package com.namstd.androidskillhub.core.ads

import android.content.Context
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.InterDelayConfig
import com.namstd.androidskillhub.core.config.PlacementConfig
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

    /**
     * Cấu hình vị trí [key] cho user này: OFF = tắt hết, REMOTE = đúng như Remote Config, BASIC = `enable` cố định
     * của [BasicAdProfile] (các cột khác, kể cả ID, vẫn lấy từ Remote Config).
     */
    fun config(key: AdKey): PlacementConfig = when (profile) {
        AdProfile.OFF -> PlacementConfig.OFF
        AdProfile.REMOTE -> RemoteConfig.placement(key)
        AdProfile.BASIC -> RemoteConfig.placement(key).copy(enable = BasicAdProfile.enable(key), stepCount = 1)
    }

    /** Gap giữa các interstitial - xem [RemoteConfig.interDelay]. */
    val interDelay: InterDelayConfig
        get() = if (profile == AdProfile.BASIC) BasicAdProfile.interDelay else RemoteConfig.interDelay

    /** Vị trí inter bù khi Splash không kịp hiện quảng cáo - chỉ ở [AdProfile.REMOTE]. Xem [SplashFallback]. */
    val splashFallback: InterstitialPlacement?
        get() = if (profile == AdProfile.REMOTE) RemoteConfig.splashFallback else null

    /** Native chỉ có ở [AdProfile.REMOTE]. */
    val nativeAllowed: Boolean
        get() = Ads.adsEnabled && profile == AdProfile.REMOTE

    /** Native ở [placement] có được bật không: theo [nativeAllowed] và `enable` của vị trí đó. */
    fun nativeEnabled(placement: NativePlacement): Boolean =
        nativeAllowed && placement.isEnabled(config(placement.unit.key))
}
