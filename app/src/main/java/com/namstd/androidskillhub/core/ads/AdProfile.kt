package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.InterDelayConfig

/**
 * Bộ quảng cáo áp cho một user, chọn bởi key `ads_enable` (xem [resolveAdProfile]).
 * Tài liệu sản phẩm (`docs/ads-config.html`) gọi [REMOTE] là "kịch bản A" và [BASIC] là "kịch bản B".
 */
enum class AdProfile {
    /** Không quảng cáo nào (`ads_enable = 0`, hoặc user Premium). */
    OFF,

    /** Quảng cáo đầy đủ, điều khiển bằng Remote Config (`enable`, `inter_delay_time`, `step_count`...). */
    REMOTE,

    /** Bộ gọn cố định trong app - xem [BasicAdProfile]. Remote Config không chỉnh được. */
    BASIC,
}

/**
 * `ads_enable`: 0 tắt · 1 REMOTE · 2 user từ marketing → REMOTE, còn lại (kể cả chưa biết) → BASIC ·
 * 3 máy nhận test ad → BASIC, còn lại (kể cả chưa biết) → REMOTE · giá trị khác → REMOTE.
 * Premium luôn [AdProfile.OFF]. [isMarketingUser] / [isTestAdDevice] = null khi chưa có kết quả - xem [AdAudience].
 */
fun resolveAdProfile(
    adsEnable: Int,
    isPremium: Boolean,
    isMarketingUser: Boolean?,
    isTestAdDevice: Boolean?,
): AdProfile {
    if (isPremium) return AdProfile.OFF
    return when (adsEnable) {
        0 -> AdProfile.OFF
        1 -> AdProfile.REMOTE
        2 -> if (isMarketingUser == true) AdProfile.REMOTE else AdProfile.BASIC
        3 -> if (isTestAdDevice == true) AdProfile.BASIC else AdProfile.REMOTE
        else -> AdProfile.REMOTE
    }
}

/**
 * Cấu hình cố định của [AdProfile.BASIC]: AOA ở Splash, banner thường ở chân các màn in-app, interstitial
 * in-app (gap 90s), App Open khi quay lại app. Không native, không inter ở Language/Intro, không banner ở
 * Splash/Language/Onboarding. Sửa bộ gọn thì sửa ở đây.
 */
object BasicAdProfile {
    /** `enable` cố định cho từng vị trí: chỉ AOA Splash, banner Home/Cài đặt, inter Home và AOA khi quay lại app. */
    fun enable(key: AdKey): String = when (key) {
        AdKey.ADS_SPLASH, AdKey.BANNER_HOME, AdKey.BANNER_SETTING, AdKey.INTER_HOME, AdKey.ON_RESUME -> "1"
        else -> "0"
    }

    val interDelay = InterDelayConfig(gapMs = 90_000L, ignore = emptySet())
}
