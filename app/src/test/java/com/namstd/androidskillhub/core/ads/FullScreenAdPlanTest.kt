package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdType
import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenAdPlanTest {
    @Test
    fun splashFormats() {
        assertEquals(FullScreenAdPlan.APP_OPEN, resolveFullScreenPlan(InterstitialPlacement.SPLASH, AdType.AOA))
        assertEquals(FullScreenAdPlan.INTERSTITIAL, resolveFullScreenPlan(InterstitialPlacement.SPLASH, AdType.INTER))
        assertEquals(FullScreenAdPlan.INTER_PLUS_NATIVE, resolveFullScreenPlan(InterstitialPlacement.SPLASH, AdType.INTER_THEN_NATIVE))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.SPLASH, null))
    }

    @Test
    fun interPlacementFormats() {
        for (placement in listOf(InterstitialPlacement.LANGUAGE, InterstitialPlacement.ONBOARDING, InterstitialPlacement.HOME, InterstitialPlacement.RESUME)) {
            assertEquals(FullScreenAdPlan.INTERSTITIAL, resolveFullScreenPlan(placement, AdType.INTER))
            assertEquals(FullScreenAdPlan.NATIVE_ONLY, resolveFullScreenPlan(placement, AdType.NATIVE_AS_INTER))
            assertEquals(FullScreenAdPlan.INTER_PLUS_NATIVE, resolveFullScreenPlan(placement, AdType.INTER_THEN_NATIVE))
            assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(placement, null))
        }
    }

    @Test
    fun formatsThatDoNotFitThePlacementAreOff() {
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.SPLASH, AdType.NATIVE_AS_INTER))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.LANGUAGE, AdType.AOA))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.HOME, AdType.BANNER))
    }

    @Test
    fun placementKeysMatchTheConfig() {
        assertEquals(listOf("ads_splash", "inter_language", "inter_after_onboarding", "inter_home", "on_resume"), InterstitialPlacement.entries.map { it.key })
    }
}
