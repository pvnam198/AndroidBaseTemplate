package com.namstd.androidskillhub.core.ads.template

import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.PlacementConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStyleTest {
    private val doc = AdTemplatesDocument(
        """
        {
          "cta_top_purple": { "base": "Big5", "cta_bg": "#7B2FA6", "cta_corner_dp": 12, "card_stroke": "#7B2FA6" },
          "Big2": { "cta_bg": "#E53935", "base": "Big9" },
          "broken": { "base": "nope", "cta_bg": "red", "card_corner_dp": -3, "title_color": "#80FFFFFF" }
        }
        """,
    )

    @Test
    fun parsesRgbAndArgbColorsAndRejectsGarbage() {
        assertEquals(0xFF7B2FA6.toInt(), NativeStyle.parseColor("#7B2FA6"))
        assertEquals(0x807B2FA6.toInt(), NativeStyle.parseColor(" #807b2fa6 "))
        assertNull(NativeStyle.parseColor("red"))
        assertNull(NativeStyle.parseColor("#FFF"))
        assertNull(NativeStyle.parseColor("#GGGGGG"))
        assertNull(NativeStyle.parseColor(null))
    }

    @Test
    fun customTemplateUsesItsBaseLayoutAndStyle() {
        val spec = doc.resolve("CTA_Top_Purple", NativeAdTemplate.BIG1)
        assertEquals(NativeAdTemplate.BIG5, spec.template)
        assertEquals(0xFF7B2FA6.toInt(), spec.style.ctaBg)
        assertEquals(12f, spec.style.ctaCornerDp)
        assertEquals(0xFF7B2FA6.toInt(), spec.style.cardStroke)
        assertNull(spec.style.cardBg)
    }

    @Test
    fun builtInNameOverridesStyleAndIgnoresBase() {
        val spec = doc.resolve("big2", NativeAdTemplate.BIG1)
        assertEquals(NativeAdTemplate.BIG2, spec.template)
        assertEquals(0xFFE53935.toInt(), spec.style.ctaBg)
    }

    @Test
    fun builtInOverrideAlsoAppliesWhenItIsThePlacementDefault() {
        assertEquals(0xFFE53935.toInt(), doc.resolve("", NativeAdTemplate.BIG2).style.ctaBg)
        assertTrue(doc.resolve("", NativeAdTemplate.BIG1).style.isEmpty)
    }

    @Test
    fun badBaseAndBadValuesFallBack() {
        val spec = doc.resolve("broken", NativeAdTemplate.SMALL1)
        assertEquals(NativeAdTemplate.SMALL1, spec.template)
        assertNull(spec.style.ctaBg)
        assertNull(spec.style.cardCornerDp)
        assertEquals(0x80FFFFFF.toInt(), spec.style.titleColor)
        assertEquals(NativeAdTemplate.TINY1, doc.resolve("ghost", NativeAdTemplate.TINY1).template)
    }

    @Test
    fun brokenJsonMeansNoCustomTemplates() {
        val empty = AdTemplatesDocument("{not json")
        assertTrue(empty.customNames.isEmpty())
        assertEquals(NativeTemplateSpec(NativeAdTemplate.BIG3), empty.resolve("big3", NativeAdTemplate.BIG1))
    }

    @Test
    fun sizesAreReadAndClampedToSafeRanges() {
        val style = NativeStyle.fromJson(
            org.json.JSONObject(
                """{ "cta_height_dp": 56, "title_text_sp": 18.5, "media_height_dp": 60, "icon_size_dp": 500,
                     "card_padding_dp": "abc", "body_text_sp": -2, "cta_text_sp": 15 }""",
            ),
        )
        assertEquals(56f, style.ctaHeightDp)
        assertEquals(18.5f, style.titleTextSp)
        assertEquals(NativeStyle.MIN_MEDIA_DP, style.mediaHeightDp)   // ảnh không nhỏ hơn 120dp (chính sách AdMob)
        assertEquals(96f, style.iconSizeDp)
        assertNull(style.cardPaddingDp)
        assertNull(style.bodyTextSp)
        assertEquals(15f, style.ctaTextSp)
    }

    @Test
    fun customNamesExcludeBuiltInOverrides() {
        assertEquals(setOf("cta_top_purple", "broken"), doc.customNames.toSet())
    }

    @Test
    fun specForPicksPlacementDefaultWhenTemplateBlank() {
        val spec = NativeAdTemplate.specFor(AdKey.NATIVE_LANGUAGE_SMALL, PlacementConfig(enable = true), doc)
        assertEquals(NativeAdTemplate.TINY1, spec.template)
        val custom = NativeAdTemplate.specFor(AdKey.NATIVE_LANGUAGE, PlacementConfig(enable = true, template = "cta_top_purple"), doc)
        assertEquals(NativeAdTemplate.BIG5, custom.template)
    }
}
