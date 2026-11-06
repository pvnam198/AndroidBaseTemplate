package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.PlacementFormat

/** Loại quảng cáo full-screen cần chạy cho một [InterstitialPlacement], suy ra từ `format` của placement đó. */
enum class FullScreenAdPlan { OFF, APP_OPEN, INTERSTITIAL, INTER_PLUS_NATIVE, NATIVE_ONLY }

/**
 * `format` của placement (null = placement tắt) → loại quảng cáo:
 * - SPLASH (`ads_splash`): aoa · inter · inter_then_native_full;
 * - các placement inter khác (`inter_language`, `inter_onboarding`, `inter_home`): inter · native_full · inter_then_native_full.
 * Format không hợp với placement thì coi như tắt.
 *
 * APP_OPEN chỉ phát sinh ở SPLASH và được xử lý trong SplashActivity (AOA không phải interstitial);
 * nếu lọt vào showInterstitial thì coi như OFF.
 */
fun resolveFullScreenPlan(placement: InterstitialPlacement, format: PlacementFormat?): FullScreenAdPlan = when (format) {
    PlacementFormat.AOA -> if (placement == InterstitialPlacement.SPLASH) FullScreenAdPlan.APP_OPEN else FullScreenAdPlan.OFF
    PlacementFormat.INTER -> FullScreenAdPlan.INTERSTITIAL
    PlacementFormat.INTER_THEN_NATIVE_FULL -> FullScreenAdPlan.INTER_PLUS_NATIVE
    PlacementFormat.NATIVE_FULL -> if (placement == InterstitialPlacement.SPLASH) FullScreenAdPlan.OFF else FullScreenAdPlan.NATIVE_ONLY
    else -> FullScreenAdPlan.OFF
}

/** Loại quảng cáo full-screen của [placement] cho user này - đọc qua [AdPolicy.format]. */
fun fullScreenPlan(placement: InterstitialPlacement): FullScreenAdPlan =
    resolveFullScreenPlan(placement, AdPolicy.format(placement.adKey))
