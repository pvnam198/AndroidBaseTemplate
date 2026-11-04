package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.EnableConfig

/** Loại quảng cáo full-screen cần chạy cho một [InterstitialPlacement], suy ra từ [EnableConfig]. */
enum class FullScreenAdPlan { OFF, APP_OPEN, INTERSTITIAL, INTER_PLUS_NATIVE, NATIVE_ONLY }

/**
 * Map vị trí → loại quảng cáo theo [EnableConfig]:
 * - SPLASH đọc `ads_splash` (1 AOA, 2 Inter, 3 Inter+Native, 0 off)
 * - LANGUAGE/INTRO đọc `inter` (1 Inter, 2 Native, 3 Inter+Native, 0 off)
 * - IN_APP đọc `inter_inapp` (cùng mã với `inter`)
 *
 * APP_OPEN chỉ phát sinh ở SPLASH và được xử lý trong SplashActivity (AOA không phải interstitial);
 * nếu lọt vào showInterstitial thì coi như OFF.
 */
fun resolveFullScreenPlan(placement: InterstitialPlacement, cfg: EnableConfig): FullScreenAdPlan =
    when (placement) {
        InterstitialPlacement.SPLASH -> when (cfg.adsSplash) {
            1 -> FullScreenAdPlan.APP_OPEN
            2 -> FullScreenAdPlan.INTERSTITIAL
            3 -> FullScreenAdPlan.INTER_PLUS_NATIVE
            else -> FullScreenAdPlan.OFF
        }
        InterstitialPlacement.LANGUAGE, InterstitialPlacement.INTRO -> interPlan(cfg.inter)
        InterstitialPlacement.IN_APP -> interPlan(cfg.interInApp)
    }

private fun interPlan(code: Int): FullScreenAdPlan = when (code) {
    1 -> FullScreenAdPlan.INTERSTITIAL
    2 -> FullScreenAdPlan.NATIVE_ONLY
    3 -> FullScreenAdPlan.INTER_PLUS_NATIVE
    else -> FullScreenAdPlan.OFF
}
