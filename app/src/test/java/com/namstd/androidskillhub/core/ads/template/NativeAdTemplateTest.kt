package com.namstd.androidskillhub.core.ads.template

import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.PlacementConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAdTemplateTest {
    private fun template(key: AdKey, template: String = "") =
        NativeAdTemplate.forPlacement(key, PlacementConfig(enable = true, template = template))

    @Test
    fun everyPlacementUsesItsTemplateByDocName() {
        assertEquals(NativeAdTemplate.BIG5, template(AdKey.NATIVE_SPLASH, "Big5"))
        assertEquals(NativeAdTemplate.BIG5, template(AdKey.NATIVE_LANGUAGE, "big5"))   // layout 2 của Language
        assertEquals(NativeAdTemplate.BIG4, template(AdKey.NATIVE_ONBOARDING, "Big4"))
    }

    @Test
    fun blankOrUnknownFallsBackToThePlacementDefault() {
        assertEquals(NativeAdTemplate.BIG1, template(AdKey.NATIVE_SPLASH))
        assertEquals(NativeAdTemplate.BIG1, template(AdKey.NATIVE_LANGUAGE, "lang2_a"))
        assertEquals(NativeAdTemplate.TINY1, template(AdKey.NATIVE_LANGUAGE_SMALL))
        assertEquals(NativeAdTemplate.TINY1, template(AdKey.NATIVE_LANGUAGE_SMALL, "xyz"))
        assertEquals(NativeAdTemplate.TINY2, template(AdKey.NATIVE_LANGUAGE_SMALL, "Tiny2"))
    }
}
