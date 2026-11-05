package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenAdPlanTest {
    @Test
    fun splashMapsAdsSplashValue() {
        assertEquals(FullScreenAdPlan.APP_OPEN, resolveFullScreenPlan(InterstitialPlacement.SPLASH, 1))
        assertEquals(FullScreenAdPlan.INTERSTITIAL, resolveFullScreenPlan(InterstitialPlacement.SPLASH, 2))
        assertEquals(FullScreenAdPlan.INTER_PLUS_NATIVE, resolveFullScreenPlan(InterstitialPlacement.SPLASH, 3))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.SPLASH, 0))
    }

    @Test
    fun interPlacementsMapTheirOwnEnable() {
        for (placement in listOf(InterstitialPlacement.LANGUAGE, InterstitialPlacement.INTRO, InterstitialPlacement.HOME)) {
            assertEquals(FullScreenAdPlan.INTERSTITIAL, resolveFullScreenPlan(placement, 1))
            assertEquals(FullScreenAdPlan.NATIVE_ONLY, resolveFullScreenPlan(placement, 2))
            assertEquals(FullScreenAdPlan.INTER_PLUS_NATIVE, resolveFullScreenPlan(placement, 3))
            assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(placement, 0))
        }
    }

    @Test
    fun unknownValuesFallBackToOff() {
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.SPLASH, 9))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.LANGUAGE, 9))
    }

    @Test
    fun placementKeysMatchTheSheet() {
        assertEquals(listOf("ads_splash", "inter_language", "inter_intro", "inter_home"), InterstitialPlacement.entries.map { it.key })
    }
}
