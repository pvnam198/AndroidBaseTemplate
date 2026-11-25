package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class InterstitialPlacementTest {
    @Test
    fun onlyFirstInstallAndSplashPlacementsAreOneTime() {
        // Một lần: không load lại sau khi dùng. Còn lại hiện lặp lại nên phải giữ reload.
        val oneTime = InterstitialPlacement.entries.filter { it.oneTime }.toSet()
        assertEquals(
            setOf(InterstitialPlacement.SPLASH, InterstitialPlacement.LANGUAGE, InterstitialPlacement.ONBOARDING),
            oneTime,
        )
    }

    @Test
    fun recurringPlacementsKeepReloading() {
        listOf(InterstitialPlacement.HOME, InterstitialPlacement.RESUME).forEach { assertEquals(it.name, false, it.oneTime) }
    }
}
