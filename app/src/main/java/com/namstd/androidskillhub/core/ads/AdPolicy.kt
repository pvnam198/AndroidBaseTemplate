package com.namstd.androidskillhub.core.ads

import android.content.Context
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.AdSet
import com.namstd.androidskillhub.core.config.AdType
import com.namstd.androidskillhub.core.config.InterDelayConfig
import com.namstd.androidskillhub.core.config.OpenGapConfig
import com.namstd.androidskillhub.core.config.PlacementConfig
import com.namstd.androidskillhub.core.config.RemoteConfig
import com.namstd.androidskillhub.core.preferences.AppPreferences

/**
 * Nguồn duy nhất cho "user này được hiện quảng cáo gì". Code quảng cáo đọc ở đây, không đọc thẳng [RemoteConfig]:
 * [RemoteConfig] trả cả hai bộ đúng như trên Firebase, còn [AdPolicy] chọn bộ ([AdProfile]) cho user này.
 */
object AdPolicy {
    private lateinit var prefs: AppPreferences

    fun initialize(context: Context) {
        prefs = AppPreferences.getInstance(context)
    }

    /** Tính lại mỗi lần đọc, nên đổi `ads_profile` hay có kết quả [AdAudience] mới là áp ngay. */
    val profile: AdProfile
        get() = resolveAdProfile(
            rule = RemoteConfig.adsProfileRule,
            isPremium = prefs.isPremium,
            isMarketingUser = AdAudience.isMarketingUser,
            isTestAdDevice = AdAudience.isTestAdDevice,
        )

    /** Cấu hình placement [key] cho user này: OFF = tắt hết, FULL = `profiles.full`, BASIC = `profiles.basic`. */
    fun config(key: AdKey): PlacementConfig = when (profile) {
        AdProfile.OFF -> PlacementConfig.OFF
        AdProfile.FULL -> RemoteConfig.placement(AdSet.FULL, key)
        AdProfile.BASIC -> RemoteConfig.placement(AdSet.BASIC, key)
    }

    /** Loại quảng cáo (`ad_type`) của [key] cho user này; null khi placement tắt. */
    fun adType(key: AdKey): AdType? = config(key).takeIf { it.enable }?.adType

    private val set: AdSet get() = if (profile == AdProfile.BASIC) AdSet.BASIC else AdSet.FULL

    /** Khoảng cách giữa các interstitial của bộ đang dùng. */
    val interDelay: InterDelayConfig
        get() = RemoteConfig.interDelay(set)

    /** Khoảng cách App Open ↔ App Open và App Open ↔ inter của bộ đang dùng (`open_gap_ms` / `open_inter_gap_ms` của bộ). */
    val openGaps: OpenGapConfig
        get() = RemoteConfig.openGaps(set)

    /** Placement inter bù khi Splash không kịp hiện quảng cáo - chỉ ở bộ full. Xem [SplashFallback]. */
    val splashFallback: InterstitialPlacement?
        get() = if (profile == AdProfile.FULL) RemoteConfig.splashFallback else null

    /** Có quảng cáo native không (tắt khi tắt quảng cáo / Premium). Bộ nào có native thì do cấu hình của bộ đó quyết. */
    val nativeAllowed: Boolean
        get() = Ads.adsEnabled && profile != AdProfile.OFF

    /** Native ở [placement] có được bật không: theo [nativeAllowed] và cấu hình placement của nó. */
    fun nativeEnabled(placement: NativePlacement): Boolean =
        nativeAllowed && placement.isEnabled(config(placement.unit.key))
}
