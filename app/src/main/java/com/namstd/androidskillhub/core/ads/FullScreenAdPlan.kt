package com.namstd.androidskillhub.core.ads

/** Loại quảng cáo full-screen cần chạy cho một [InterstitialPlacement], suy ra từ `enable` của vị trí đó. */
enum class FullScreenAdPlan { OFF, APP_OPEN, INTERSTITIAL, INTER_PLUS_NATIVE, NATIVE_ONLY }

/**
 * Map `enable` của vị trí → loại quảng cáo:
 * - SPLASH (`ads_splash`): 1 AOA, 2 Inter, 3 Inter+Native, 0 off
 * - các vị trí inter khác (`inter_language`, `inter_onboarding`, `inter_home`): 1 Inter, 2 Native, 3 Inter+Native, 0 off
 *
 * APP_OPEN chỉ phát sinh ở SPLASH và được xử lý trong SplashActivity (AOA không phải interstitial);
 * nếu lọt vào showInterstitial thì coi như OFF.
 */
fun resolveFullScreenPlan(placement: InterstitialPlacement, enable: Int): FullScreenAdPlan =
    if (placement == InterstitialPlacement.SPLASH) {
        when (enable) {
            1 -> FullScreenAdPlan.APP_OPEN
            2 -> FullScreenAdPlan.INTERSTITIAL
            3 -> FullScreenAdPlan.INTER_PLUS_NATIVE
            else -> FullScreenAdPlan.OFF
        }
    } else {
        when (enable) {
            1 -> FullScreenAdPlan.INTERSTITIAL
            2 -> FullScreenAdPlan.NATIVE_ONLY
            3 -> FullScreenAdPlan.INTER_PLUS_NATIVE
            else -> FullScreenAdPlan.OFF
        }
    }

/** Loại quảng cáo full-screen của [placement] cho user này - đọc `enable` qua [AdPolicy.config]. */
fun fullScreenPlan(placement: InterstitialPlacement): FullScreenAdPlan =
    resolveFullScreenPlan(placement, AdPolicy.config(placement.adKey).enableCode)
