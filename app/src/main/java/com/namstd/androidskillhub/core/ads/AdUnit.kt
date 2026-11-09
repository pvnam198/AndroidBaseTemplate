package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.RemoteConfig

/** Loại quảng cáo của một ad unit, kèm test ID của Google dùng khi chưa cấu hình ID thật. */
enum class AdFormat(val testId: String) {
    APP_OPEN("ca-app-pub-3940256099942544/9257395921"),
    INTERSTITIAL("ca-app-pub-3940256099942544/1033173712"),
    NATIVE("ca-app-pub-3940256099942544/2247696110"),
    BANNER("ca-app-pub-3940256099942544/6300978111"),
    REWARDED("ca-app-pub-3940256099942544/5224354917"),
    REWARDED_INTERSTITIAL("ca-app-pub-3940256099942544/5354046379"),
}

/** Test ID riêng cho banner collapsible (cùng ad unit với banner thường, khác test ID). */
const val TEST_BANNER_COLLAPSIBLE = "ca-app-pub-3940256099942544/2014213617"

/**
 * Các ad unit - tên (viết thường) đúng như cột `ad_unit` của sheet và key trong parameter `ad_ids`. Mỗi unit thuộc
 * một placement ([key]). Chưa có ID thì dùng test ID của [format].
 */
enum class AdUnit(val key: AdKey, val format: AdFormat) {
    AOA_SPLASH(AdKey.ADS_SPLASH, AdFormat.APP_OPEN),
    INTER_SPLASH(AdKey.ADS_SPLASH, AdFormat.INTERSTITIAL),
    NATIVE_INTER_SPLASH(AdKey.ADS_SPLASH, AdFormat.NATIVE),
    NATIVE_SPLASH(AdKey.NATIVE_SPLASH, AdFormat.NATIVE),
    NATIVE_LANGUAGE(AdKey.NATIVE_LANGUAGE, AdFormat.NATIVE),
    NATIVE_LANGUAGE_2(AdKey.NATIVE_LANGUAGE, AdFormat.NATIVE),
    NATIVE_LANGUAGE_SMALL(AdKey.NATIVE_LANGUAGE_SMALL, AdFormat.NATIVE),
    INTER_LANGUAGE(AdKey.INTER_LANGUAGE, AdFormat.INTERSTITIAL),
    NATIVE_INTER_LANGUAGE(AdKey.INTER_LANGUAGE, AdFormat.NATIVE),
    NATIVE_ONBOARDING_1(AdKey.NATIVE_ONBOARDING, AdFormat.NATIVE),
    NATIVE_ONBOARDING_2(AdKey.NATIVE_ONBOARDING, AdFormat.NATIVE),
    NATIVE_ONBOARDING_3(AdKey.NATIVE_ONBOARDING, AdFormat.NATIVE),
    NATIVE_ONBOARDING_4(AdKey.NATIVE_ONBOARDING, AdFormat.NATIVE),
    NATIVE_FULL_ONBOARDING(AdKey.NATIVE_FULL_ONBOARDING, AdFormat.NATIVE),
    INTER_ONBOARDING(AdKey.INTER_ONBOARDING, AdFormat.INTERSTITIAL),
    NATIVE_INTER_ONBOARDING(AdKey.INTER_ONBOARDING, AdFormat.NATIVE),
    ON_RESUME(AdKey.ON_RESUME, AdFormat.APP_OPEN),
    NATIVE_WELCOME_BACK(AdKey.NATIVE_WELCOME_BACK, AdFormat.NATIVE),
    BANNER_HOME(AdKey.BANNER_HOME, AdFormat.BANNER),
    NATIVE_HOME(AdKey.BANNER_HOME, AdFormat.NATIVE),
    NATIVE_COLLAP_HOME(AdKey.BANNER_HOME, AdFormat.NATIVE),
    NATIVE_HOME_FEED(AdKey.NATIVE_HOME_FEED, AdFormat.NATIVE),
    INTER_HOME(AdKey.INTER_HOME, AdFormat.INTERSTITIAL),
    NATIVE_INTER_HOME(AdKey.INTER_HOME, AdFormat.NATIVE),
    BANNER_SETTING(AdKey.BANNER_SETTING, AdFormat.BANNER),
    REWARD_SAMPLE(AdKey.REWARD_SAMPLE, AdFormat.REWARDED),
    REWARD_INTER_SAMPLE(AdKey.REWARD_INTER_SAMPLE, AdFormat.REWARDED_INTERSTITIAL);

    /** Tên trong `ad_ids` / cột `ad_unit` của sheet. */
    val unitName: String get() = name.lowercase()

    /**
     * ID cho waterfall (id_1 → id_3) của unit này, từ `ad_ids`; chưa đặt thì là [testId] (mặc định test ID của
     * [format]). Bộ full và bộ basic dùng chung ID.
     */
    fun ids(testId: String = format.testId): List<String> =
        RemoteConfig.unitIds(unitName).ifEmpty { listOf(testId) }
}
