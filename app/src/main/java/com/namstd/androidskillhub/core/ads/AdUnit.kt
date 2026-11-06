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
}

/** Test ID riêng cho banner collapsible (cùng ad unit với banner thường, khác test ID). */
const val TEST_BANNER_COLLAPSIBLE = "ca-app-pub-3940256099942544/2014213617"

/** Test ID cho rewarded interstitial (hiện chưa dùng). */
const val TEST_REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"

/**
 * Các ad unit trong sheet "Ad Script" - tên đúng như cột `name1`. Mỗi unit thuộc một vị trí ([key]) và có ID riêng,
 * đặt trong `ids` của vị trí đó trên Remote Config. Chưa đặt thì dùng test ID của [format].
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
    NATIVE_INTRO_1(AdKey.NATIVE_INTRO, AdFormat.NATIVE),
    NATIVE_INTRO_2(AdKey.NATIVE_INTRO, AdFormat.NATIVE),
    NATIVE_INTRO_3(AdKey.NATIVE_INTRO, AdFormat.NATIVE),
    NATIVE_INTRO_4(AdKey.NATIVE_INTRO, AdFormat.NATIVE),
    NATIVE_INTRO_FULL(AdKey.NATIVE_INTRO_FULL, AdFormat.NATIVE),
    INTER_INTRO(AdKey.INTER_INTRO, AdFormat.INTERSTITIAL),
    NATIVE_INTER_INTRO(AdKey.INTER_INTRO, AdFormat.NATIVE),
    ON_RESUME(AdKey.ON_RESUME, AdFormat.APP_OPEN),
    INTER_WELCOME_BACK(AdKey.INTER_WELCOME_BACK, AdFormat.INTERSTITIAL),
    NATIVE_INTER_WELCOME_BACK(AdKey.INTER_WELCOME_BACK, AdFormat.NATIVE),
    NATIVE_WELCOME_BACK(AdKey.NATIVE_WELCOME_BACK, AdFormat.NATIVE),
    NATIVE_GET_START_1(AdKey.NATIVE_GET_START, AdFormat.NATIVE),
    NATIVE_GET_START_2(AdKey.NATIVE_GET_START, AdFormat.NATIVE),
    INTER_START(AdKey.INTER_START, AdFormat.INTERSTITIAL),
    NATIVE_INTER_START(AdKey.INTER_START, AdFormat.NATIVE),
    BANNER_HOME(AdKey.BANNER_HOME, AdFormat.BANNER),
    NATIVE_HOME(AdKey.BANNER_HOME, AdFormat.NATIVE),
    NATIVE_COLLAP_HOME(AdKey.BANNER_HOME, AdFormat.NATIVE),
    INTER_HOME(AdKey.INTER_HOME, AdFormat.INTERSTITIAL),
    NATIVE_INTER_HOME(AdKey.INTER_HOME, AdFormat.NATIVE),
    BANNER_SETTING(AdKey.BANNER_SETTING, AdFormat.BANNER),
    INTER_BACK(AdKey.INTER_BACK, AdFormat.INTERSTITIAL),
    NATIVE_INTER_BACK(AdKey.INTER_BACK, AdFormat.NATIVE);

    /**
     * ID cho waterfall (id1 → id3) của unit này; chưa đặt thì là [testId] (mặc định test ID của [format]).
     * Luôn đọc từ cấu hình REMOTE, kể cả ở kịch bản BASIC ("id lấy ở kịch bản A" theo sheet).
     */
    fun ids(testId: String = format.testId): List<String> =
        RemoteConfig.placement(key).ids[name].orEmpty().ifEmpty { listOf(testId) }
}
