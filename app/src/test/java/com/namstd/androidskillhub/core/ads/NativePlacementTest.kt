package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.PlacementConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlacementTest {
    private val on = PlacementConfig(enable = true)
    private val off = PlacementConfig(enable = false)

    @Test
    fun disabledPlacementIsNotPreloaded() {
        // preload dùng isEnabled: placement tự có enable trong config mà tắt thì không được tải.
        listOf(
            NativePlacement.SPLASH, NativePlacement.LANGUAGE, NativePlacement.LANGUAGE_SETTING, NativePlacement.LANGUAGE_SMALL,
            NativePlacement.ONBOARDING_1, NativePlacement.ONBOARDING_2, NativePlacement.ONBOARDING_3, NativePlacement.ONBOARDING_4,
            NativePlacement.WELCOME_BACK, NativePlacement.HOME_FEED,
        ).forEach {
            assertFalse(it.name, it.isEnabled(off))
            assertTrue(it.name, it.isEnabled(on))
        }
    }

    @Test
    fun nativesOwnedByAParentPlacementFollowTheParent() {
        // Native sau inter / banner không có enable riêng: placement cha (ad_type) quyết định nên luôn được phép ở đây.
        listOf(NativePlacement.AFTER_INTER_SPLASH, NativePlacement.AFTER_INTER_LANGUAGE, NativePlacement.NATIVE_COLLAPSIBLE)
            .forEach { assertTrue(it.name, it.isEnabled(off)) }
    }
}
