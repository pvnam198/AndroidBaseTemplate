package com.namstd.androidskillhub.core.config

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đọc 3 phần config theo file mẫu của team (`src/test/resources/ad_config_sample.json` = file "Tải tất cả" của sheet). */
class PlacementConfigTest {
    private val sample = JSONObject(checkNotNull(javaClass.classLoader?.getResource("ad_config_sample.json")).readText())
    private val placements = AdPlacementsDocument(sample.getJSONObject("ad_placements").toString())
    private val ids = AdIdsDocument(sample.getJSONObject("ad_ids").toString())

    private fun full(key: AdKey) = placements.placement(AdSet.FULL, key)
    private fun basic(key: AdKey) = placements.placement(AdSet.BASIC, key)

    @Test
    fun fullOverridesOnlyTheFieldsItSets() {
        val splash = full(AdKey.ADS_SPLASH)
        assertTrue(splash.enable)                       // mặc định
        assertEquals(PlacementFormat.INTER_THEN_NATIVE_FULL, splash.format)
        assertEquals(5000L, splash.waitMs)
        assertEquals(1, splash.step)                    // mặc định

        val onboarding = full(AdKey.NATIVE_ONBOARDING)
        assertEquals(setOf(2, 4), onboarding.pages)
        assertEquals("Big4", onboarding.template)

        assertTrue(full(AdKey.NATIVE_SPLASH).enable)    // mặc định tắt, JSON bật
        assertEquals(setOf(2, 4), full(AdKey.BANNER_HOME).openAdOnClicks)
        assertEquals(PlacementFormat.WELCOME_BACK, full(AdKey.ON_RESUME).format)
    }

    @Test
    fun placementsMissingFromFullUseDefaults() {
        assertEquals(AdKey.INTER_HOME.fullDefault, full(AdKey.INTER_HOME))
        assertFalse(full(AdKey.INTER_HOME).enable)
        assertEquals(PlacementFormat.BANNER_COLLAPSIBLE, full(AdKey.BANNER_SETTING).format)
        assertTrue(full(AdKey.NATIVE_HOME_FEED).enable)
    }

    @Test
    fun basicRunsOnlyThePlacementsItLists() {
        assertEquals(PlacementFormat.AOA, basic(AdKey.ADS_SPLASH).format)
        assertTrue(basic(AdKey.ADS_SPLASH).enable)
        assertEquals(PlacementFormat.BANNER, basic(AdKey.BANNER_HOME).format)
        assertEquals(PlacementFormat.INTER, basic(AdKey.INTER_HOME).format)
        for (off in listOf(AdKey.NATIVE_SPLASH, AdKey.INTER_LANGUAGE, AdKey.NATIVE_ONBOARDING, AdKey.NATIVE_HOME_FEED)) {
            assertFalse(off.key, basic(off).enable)
        }
    }

    @Test
    fun basicEntryCanBeSwitchedOffExplicitly() {
        val doc = AdPlacementsDocument("""{"basic":{"inter_home":{"enable":false,"format":"inter"}}}""")
        assertFalse(doc.placement(AdSet.BASIC, AdKey.INTER_HOME).enable)
    }

    @Test
    fun idsAreReadByAdUnitNameIgnoringCase() {
        assertEquals(
            listOf(
                "ca-app-pub-5839201746382519/8274610392",
                "ca-app-pub-7193842056174928/3948271056",
                "ca-app-pub-2649183750294618/6150392847",
            ),
            ids.ids("aoa_splash"),
        )
        assertEquals(ids.ids("native_home_feed"), ids.ids("NATIVE_HOME_FEED"))
        assertTrue(ids.ids("missing").isEmpty())
        assertEquals(listOf("a"), AdIdsDocument("""{"x":["a",""]}""").ids("x"))
    }

    @Test
    fun brokenOrEmptyJsonMeansDefaults() {
        for (raw in listOf("", "{broken", "[]")) {
            assertEquals(AdKey.ADS_SPLASH.fullDefault, AdPlacementsDocument(raw).placement(AdSet.FULL, AdKey.ADS_SPLASH))
            assertFalse(AdPlacementsDocument(raw).placement(AdSet.BASIC, AdKey.ADS_SPLASH).enable)
        }
    }

    @Test
    fun badValuesKeepTheDefault() {
        val doc = AdPlacementsDocument("""{"full":{"ads_splash":{"format":"xyz","step":0,"wait_ms":-5}}}""")
        val splash = doc.placement(AdSet.FULL, AdKey.ADS_SPLASH)
        assertEquals(PlacementFormat.INTER, splash.format)
        assertEquals(1, splash.step)
        assertEquals(0L, splash.waitMs)
    }

    @Test
    fun numberListsAcceptArraysOrText() {
        assertEquals(setOf(2, 4), PlacementConfig.intSet(org.json.JSONArray("[2,4]")))
        assertEquals(setOf(1, 2, 3), PlacementConfig.intSet("1, 2,3"))
        assertEquals(emptySet<Int>(), PlacementConfig.intSet(null))
    }

    @Test
    fun keysMatchTheSheet() {
        val sheetKeys = sample.getJSONObject("ad_placements").getJSONObject("full").keys().asSequence().toSet() +
            sample.getJSONObject("ad_placements").getJSONObject("basic").keys().asSequence().toSet()
        sheetKeys.forEach { assertTrue(it, AdKey.fromKey(it) != null) }
        assertNull(AdKey.fromKey("native_get_start"))
        assertEquals(PlacementFormat.INTER_THEN_NATIVE_FULL, PlacementFormat.fromKey("Inter_Then_Native_Full"))
    }

    @Test
    fun everyAdUnitInTheSampleExistsInCode() {
        val units = com.namstd.androidskillhub.core.ads.AdUnit.entries.map { it.unitName }.toSet()
        sample.getJSONObject("ad_ids").keys().forEach { assertTrue(it, it in units) }
    }
}
