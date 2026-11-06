package com.namstd.androidskillhub.core.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đọc `ad_config` theo đúng mẫu JSON của team (`src/test/resources/ad_config_sample.json`). */
class PlacementConfigTest {
    private val sample = AdConfigDocument(
        checkNotNull(javaClass.classLoader?.getResource("ad_config_sample.json")).readText(),
    )

    @Test
    fun settingsAreReadAsText() {
        assertEquals("2", sample.setting("ads_enable"))
        assertEquals("inter_language", sample.setting("splash_fallback"))
        assertEquals("true", sample.setting("intro_swipe"))
        assertEquals("next=1, loading=true", sample.setting("language_setup"))
        assertNull(sample.setting("missing"))
    }

    @Test
    fun placementReadsColumnsAndUnitIdsByName1() {
        val splash = sample.placement(AdKey.ADS_SPLASH)
        assertEquals(3, splash.enableCode)
        assertEquals(5L, splash.waitTimeMs)
        assertEquals(
            listOf(
                "ca-app-pub-1234567890123456/1111111113",
                "ca-app-pub-1234567890123456/2222222224",
                "ca-app-pub-1234567890123456/3333333335",
            ),
            splash.ids["INTER_SPLASH"],
        )
        assertEquals(setOf("AOA_SPLASH", "INTER_SPLASH", "NATIVE_INTER_SPLASH"), splash.ids.keys)
        assertEquals("Big5", sample.placement(AdKey.NATIVE_SPLASH).nativeTemplate)
    }

    @Test
    fun objectNamesDoNotMatterOnlyName1() {
        val language = sample.placement(AdKey.NATIVE_LANGUAGE)
        assertEquals(setOf("NATIVE_LANGUAGE", "NATIVE_LANGUAGE_2"), language.ids.keys)
        assertEquals(listOf("ca-app-pub-1234567890123456/1111111119"), sample.placement(AdKey.INTER_LANGUAGE).ids["NATIVE_INTER_LANGUAGE"])
    }

    @Test
    fun digitEnablesAndNumericCollapConfig() {
        assertEquals(setOf(2, 4), sample.placement(AdKey.NATIVE_INTRO).enableDigits)
        assertEquals(setOf(2), sample.placement(AdKey.NATIVE_INTRO_FULL).enableDigits)
        val numeric = PlacementConfig.fromJson(JSONObject("""{"enable":1,"refresh_rate":20,"collap_config":24}"""), AdKey.BANNER_HOME)
        assertEquals("24", numeric.collapConfig)
        assertEquals(20L, numeric.refreshRateMs)
    }

    @Test
    fun unitsWithoutIdsAndEmptySlotsAreHandled() {
        val banner = sample.placement(AdKey.BANNER_HOME)
        assertEquals(4, banner.enableCode)
        assertEquals(emptyList<String>(), banner.ids["BANNER_HOME"])
        assertEquals(setOf("BANNER_SETTING"), sample.placement(AdKey.BANNER_SETTING).ids.keys)
    }

    @Test
    fun onResumeComesFromSettings() {
        val onResume = sample.placement(AdKey.ON_RESUME)
        assertEquals(2, onResume.enableCode)
        assertEquals(listOf("ca-app-pub-1234567890123456/1111111127"), onResume.ids["ON_RESUME"])
    }

    @Test
    fun missingPlacementUsesDefaultEnable() {
        assertEquals("1", sample.placement(AdKey.INTER_INTRO).enable)
        assertEquals("1", sample.placement(AdKey.NATIVE_WELCOME_BACK).enable)
        assertEquals("2", AdConfigDocument("").placement(AdKey.ADS_SPLASH).enable)
        assertTrue(AdConfigDocument("{broken").placement(AdKey.NATIVE_LANGUAGE).enableCode == 1)
    }

    @Test
    fun debugOverrideAcceptsNumberOrObject() {
        assertEquals("24", PlacementConfig.parseOverride("24", AdKey.NATIVE_INTRO).enable)
        val json = PlacementConfig.parseOverride("""{"enable":1,"inter":{"name1":"INTER_LANGUAGE","id1":"x"}}""", AdKey.INTER_LANGUAGE)
        assertEquals(listOf("x"), json.ids["INTER_LANGUAGE"])
        assertEquals(0, PlacementConfig.parseOverride("{broken", AdKey.INTER_LANGUAGE).enableCode)
    }
}
