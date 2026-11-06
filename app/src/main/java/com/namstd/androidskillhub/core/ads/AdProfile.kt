package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdsProfileRule

/**
 * Bộ quảng cáo áp cho một user, chọn bởi `ads_profile` (xem [resolveAdProfile]).
 */
enum class AdProfile {
    /** Không quảng cáo nào (`ads_profile = off`, hoặc user Premium). */
    OFF,

    /** Bộ đầy đủ - `ad_placements.full`. */
    FULL,

    /** Bộ cơ bản - `ad_placements.basic` (chỉ placement có trong đó mới chạy). */
    BASIC,
}

/**
 * `ads_profile` → bộ cho user này: off → OFF · full_for_all → FULL · full_for_paid_users: user từ marketing → FULL,
 * còn lại (kể cả chưa biết nguồn) → BASIC · full_except_test_devices: máy nhận test ad → BASIC, còn lại (kể cả chưa
 * biết) → FULL. Premium luôn [AdProfile.OFF]. [isMarketingUser] / [isTestAdDevice] = null khi chưa có kết quả - xem [AdAudience].
 */
fun resolveAdProfile(
    rule: AdsProfileRule,
    isPremium: Boolean,
    isMarketingUser: Boolean?,
    isTestAdDevice: Boolean?,
): AdProfile {
    if (isPremium) return AdProfile.OFF
    return when (rule) {
        AdsProfileRule.OFF -> AdProfile.OFF
        AdsProfileRule.FULL_FOR_ALL -> AdProfile.FULL
        AdsProfileRule.FULL_FOR_PAID_USERS -> if (isMarketingUser == true) AdProfile.FULL else AdProfile.BASIC
        AdsProfileRule.FULL_EXCEPT_TEST_DEVICES -> if (isTestAdDevice == true) AdProfile.BASIC else AdProfile.FULL
    }
}
