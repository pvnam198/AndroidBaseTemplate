package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdKey

/**
 * Loại quảng cáo của một ad unit: [idType] là key trong `ad_ids` của placement, [testId] là test ID của Google dùng
 * khi placement chưa có ID.
 */
enum class AdFormat(val idType: String, val testId: String) {
    APP_OPEN("aoa", "ca-app-pub-3940256099942544/9257395921"),
    INTERSTITIAL("inter", "ca-app-pub-3940256099942544/1033173712"),
    NATIVE("native", "ca-app-pub-3940256099942544/2247696110"),
    BANNER("banner", "ca-app-pub-3940256099942544/6300978111"),
    REWARDED("reward", "ca-app-pub-3940256099942544/5224354917"),
    REWARDED_INTERSTITIAL("reward_inter", "ca-app-pub-3940256099942544/5354046379"),
}

/** Test ID riêng cho banner collapsible (cùng ad unit với banner thường, khác test ID). */
const val TEST_BANNER_COLLAPSIBLE = "ca-app-pub-3940256099942544/2014213617"

/**
 * Các ad unit: mỗi unit là một loại Ad Unit ([format]) trong `ad_ids` của một placement ([key]), vd INTER_SPLASH =
 * `ads_splash.ad_ids.inter`. Chưa có ID thì dùng test ID của [format].
 */
enum class AdUnit(val key: AdKey, val format: AdFormat) {
    AOA_SPLASH(AdKey.ADS_SPLASH, AdFormat.APP_OPEN),
    INTER_SPLASH(AdKey.ADS_SPLASH, AdFormat.INTERSTITIAL),
    NATIVE_INTER_SPLASH(AdKey.ADS_SPLASH, AdFormat.NATIVE),
    NATIVE_SPLASH(AdKey.NATIVE_SPLASH, AdFormat.NATIVE),
    NATIVE_LANGUAGE(AdKey.NATIVE_LANGUAGE, AdFormat.NATIVE),
    NATIVE_LANGUAGE_2(AdKey.NATIVE_LANGUAGE_2, AdFormat.NATIVE),
    NATIVE_LANGUAGE_SMALL(AdKey.NATIVE_LANGUAGE_SMALL, AdFormat.NATIVE),
    INTER_LANGUAGE(AdKey.INTER_LANGUAGE, AdFormat.INTERSTITIAL),
    NATIVE_INTER_LANGUAGE(AdKey.INTER_LANGUAGE, AdFormat.NATIVE),
    NATIVE_ONBOARDING(AdKey.NATIVE_ONBOARDING, AdFormat.NATIVE),
    NATIVE_FULL_ONBOARDING(AdKey.NATIVE_FULL_ONBOARDING, AdFormat.NATIVE),
    INTER_AFTER_ONBOARDING(AdKey.INTER_AFTER_ONBOARDING, AdFormat.INTERSTITIAL),
    NATIVE_INTER_AFTER_ONBOARDING(AdKey.INTER_AFTER_ONBOARDING, AdFormat.NATIVE),
    ON_RESUME(AdKey.ON_RESUME, AdFormat.APP_OPEN),
    /** Native của `on_resume`: màn Welcome Back (ad_type native) hoặc native full sau inter (inter_then_native / native_as_inter). */
    NATIVE_ON_RESUME(AdKey.ON_RESUME, AdFormat.NATIVE),
    INTER_ON_RESUME(AdKey.ON_RESUME, AdFormat.INTERSTITIAL),
    BANNER_HOME(AdKey.BANNER_HOME, AdFormat.BANNER),
    NATIVE_HOME(AdKey.BANNER_HOME, AdFormat.NATIVE),
    NATIVE_HOME_FEED(AdKey.NATIVE_HOME_FEED, AdFormat.NATIVE),
    INTER_HOME(AdKey.INTER_HOME, AdFormat.INTERSTITIAL),
    NATIVE_INTER_HOME(AdKey.INTER_HOME, AdFormat.NATIVE),
    BANNER_SETTING(AdKey.BANNER_SETTING, AdFormat.BANNER),
    REWARD_SAMPLE(AdKey.REWARD_SAMPLE, AdFormat.REWARDED),
    REWARD_INTER_SAMPLE(AdKey.REWARD_INTER_SAMPLE, AdFormat.REWARDED_INTERSTITIAL);

    /**
     * ID cho waterfall (id_1 → id_3) của unit này: `ad_ids.<loại>` của placement [key] trong bộ quảng cáo của user
     * ([AdPolicy.config]); chưa đặt thì là [testId] (mặc định test ID của [format]).
     */
    fun ids(testId: String = format.testId): List<String> =
        AdPolicy.config(key).ids(format.idType).ifEmpty { listOf(testId) }
}
