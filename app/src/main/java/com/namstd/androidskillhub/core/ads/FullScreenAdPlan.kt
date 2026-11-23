package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdType

/** Loại quảng cáo full-screen cần chạy cho một [InterstitialPlacement], suy ra từ `ad_type` của placement đó. */
enum class FullScreenAdPlan { OFF, APP_OPEN, INTERSTITIAL, INTER_PLUS_NATIVE, NATIVE_ONLY }

/**
 * `ad_type` của placement (null = placement tắt) → loại quảng cáo:
 * - SPLASH (`ads_splash`): aoa · inter · inter_then_native (native_as_inter chưa hỗ trợ ở Splash → tắt);
 * - các placement inter khác (`inter_language`, `inter_after_onboarding`, `inter_home`): inter · native_as_inter ·
 *   inter_then_native;
 * - RESUME (`on_resume`, lúc bấm Tiếp tục ở Welcome Back): như trên, thêm `native` = native toàn màn.
 * Ad type không hợp với placement thì coi như tắt.
 *
 * APP_OPEN chỉ phát sinh ở SPLASH và được xử lý trong SplashActivity (AOA không phải interstitial);
 * nếu lọt vào showInterstitial thì coi như OFF.
 */
fun resolveFullScreenPlan(placement: InterstitialPlacement, adType: AdType?): FullScreenAdPlan = when (adType) {
    AdType.AOA -> if (placement == InterstitialPlacement.SPLASH) FullScreenAdPlan.APP_OPEN else FullScreenAdPlan.OFF
    AdType.INTER -> FullScreenAdPlan.INTERSTITIAL
    AdType.INTER_THEN_NATIVE -> FullScreenAdPlan.INTER_PLUS_NATIVE
    AdType.NATIVE_AS_INTER -> if (placement == InterstitialPlacement.SPLASH) FullScreenAdPlan.OFF else FullScreenAdPlan.NATIVE_ONLY
    AdType.NATIVE -> if (placement == InterstitialPlacement.RESUME) FullScreenAdPlan.NATIVE_ONLY else FullScreenAdPlan.OFF
    else -> FullScreenAdPlan.OFF
}

/** Loại quảng cáo full-screen của [placement] cho user này - đọc qua [AdPolicy.adType]. */
fun fullScreenPlan(placement: InterstitialPlacement): FullScreenAdPlan =
    resolveFullScreenPlan(placement, AdPolicy.adType(placement.adKey))
