package com.namstd.androidskillhub.core.ads.template

import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.PlacementConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeAdTemplateTest {
    private fun template(key: AdKey, enable: String, nativeTemplate: String = "") =
        NativeAdTemplate.forPlacement(key, PlacementConfig(enable = enable, nativeTemplate = nativeTemplate))

    @Test
    fun languageLayoutComesFromEnable() {
        assertEquals(NativeAdTemplate.BIG1, template(AdKey.NATIVE_LANGUAGE, "1"))
        assertEquals(NativeAdTemplate.BIG5, template(AdKey.NATIVE_LANGUAGE, "2"))
        assertEquals(NativeAdTemplate.BIG5, template(AdKey.NATIVE_GET_START, "2"))
    }

    @Test
    fun languageIgnoresNativeTemplateWhenBothAreSet() {
        assertEquals(NativeAdTemplate.BIG5, template(AdKey.NATIVE_LANGUAGE, "2", nativeTemplate = "Big1"))
        assertEquals(NativeAdTemplate.BIG1, template(AdKey.NATIVE_LANGUAGE, "1", nativeTemplate = "Tiny1"))
    }

    @Test
    fun unknownLanguageEnableFallsBackToLayout1() {
        assertEquals(NativeAdTemplate.BIG1, template(AdKey.NATIVE_LANGUAGE, "3"))
    }

    @Test
    fun otherPlacementsUseNativeTemplateByDocName() {
        assertEquals(NativeAdTemplate.BIG5, template(AdKey.NATIVE_SPLASH, "1", nativeTemplate = "Big5"))
        assertEquals(NativeAdTemplate.TINY1, template(AdKey.NATIVE_LANGUAGE_SMALL, "1", nativeTemplate = "tiny1"))
        assertEquals(NativeAdTemplate.BIG1, template(AdKey.NATIVE_SPLASH, "1", nativeTemplate = "lang2_a"))
        assertEquals(NativeAdTemplate.BIG1, template(AdKey.NATIVE_SPLASH, "1"))
    }
}
