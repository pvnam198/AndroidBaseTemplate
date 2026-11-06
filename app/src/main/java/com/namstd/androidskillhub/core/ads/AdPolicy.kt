package com.namstd.androidskillhub.core.ads

import android.content.Context
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.AdSet
import com.namstd.androidskillhub.core.config.InterDelayConfig
import com.namstd.androidskillhub.core.config.PlacementConfig
import com.namstd.androidskillhub.core.config.PlacementFormat
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

    /** Cấu hình placement [key] cho user này: OFF = tắt hết, FULL = `ad_placements.full`, BASIC = `ad_placements.basic`. */
    fun config(key: AdKey): PlacementConfig = when (profile) {
        AdProfile.OFF -> PlacementConfig.OFF
        AdProfile.FULL -> RemoteConfig.placement(AdSet.FULL, key)
        AdProfile.BASIC -> RemoteConfig.placement(AdSet.BASIC, key)
    }

    /** Loại quảng cáo của [key] cho user này; null khi placement tắt. */
    fun format(key: AdKey): PlacementFormat? = config(key).takeIf { it.enable }?.format

    /** Khoảng cách giữa các interstitial của bộ đang dùng. */
    val interDelay: InterDelayConfig
        get() = if (profile == AdProfile.BASIC) RemoteConfig.basicInterDelay else RemoteConfig.fullInterDelay

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
