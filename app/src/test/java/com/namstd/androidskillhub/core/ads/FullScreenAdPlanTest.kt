package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.PlacementFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FullScreenAdPlanTest {
    @Test
    fun splashFormats() {
        assertEquals(FullScreenAdPlan.APP_OPEN, resolveFullScreenPlan(InterstitialPlacement.SPLASH, PlacementFormat.AOA))
        assertEquals(FullScreenAdPlan.INTERSTITIAL, resolveFullScreenPlan(InterstitialPlacement.SPLASH, PlacementFormat.INTER))
        assertEquals(FullScreenAdPlan.INTER_PLUS_NATIVE, resolveFullScreenPlan(InterstitialPlacement.SPLASH, PlacementFormat.INTER_THEN_NATIVE_FULL))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.SPLASH, null))
    }

    @Test
    fun interPlacementFormats() {
        for (placement in listOf(InterstitialPlacement.LANGUAGE, InterstitialPlacement.ONBOARDING, InterstitialPlacement.HOME)) {
            assertEquals(FullScreenAdPlan.INTERSTITIAL, resolveFullScreenPlan(placement, PlacementFormat.INTER))
            assertEquals(FullScreenAdPlan.NATIVE_ONLY, resolveFullScreenPlan(placement, PlacementFormat.NATIVE_FULL))
            assertEquals(FullScreenAdPlan.INTER_PLUS_NATIVE, resolveFullScreenPlan(placement, PlacementFormat.INTER_THEN_NATIVE_FULL))
            assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(placement, null))
        }
    }

    @Test
    fun formatsThatDoNotFitThePlacementAreOff() {
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.SPLASH, PlacementFormat.NATIVE_FULL))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.LANGUAGE, PlacementFormat.AOA))
        assertEquals(FullScreenAdPlan.OFF, resolveFullScreenPlan(InterstitialPlacement.HOME, PlacementFormat.BANNER))
    }

    @Test
    fun placementKeysMatchTheSheet() {
        assertEquals(listOf("ads_splash", "inter_language", "inter_onboarding", "inter_home"), InterstitialPlacement.entries.map { it.key })
    }
}
