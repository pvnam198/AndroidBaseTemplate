package com.namstd.androidskillhub.core.config

import com.namstd.androidskillhub.core.ads.AdUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Đọc file cấu hình xuất từ web (`src/test/resources/ad_config_sample.json` = `docs/ad_config_sample.json`). */
class PlacementConfigTest {
    private val raw = checkNotNull(javaClass.classLoader?.getResource("ad_config_sample.json")).readText()
    private val doc = AdConfigDocument(raw)

    private fun full(key: AdKey) = doc.placement(AdSet.FULL, key)
    private fun basic(key: AdKey) = doc.placement(AdSet.BASIC, key)

    @Test
    fun rootFields() {
        assertEquals("1", doc.configVersion)
        assertEquals(AdsProfileRule.FULL_FOR_ALL, AdsProfileRule.fromKey(doc.adsProfile))
        assertEquals(10000, doc.settings.getInt("splash_timeout_ms"))
        assertEquals("inter_language", doc.settings.getJSONArray("splash_fallback").getString(0))
        assertEquals(1000, doc.settings.getJSONObject("onboarding_pages").getJSONObject("2").getInt("next_delay_ms"))
        assertEquals("Big5", doc.templatesJson.getJSONObject("LanguageDisplayFirst").getString("base"))
    }

    @Test
    fun profileSettings() {
        assertEquals(10000, doc.profileSettings(AdSet.FULL).getInt("inter_gap_ms"))
        assertEquals("inter_after_onboarding", doc.profileSettings(AdSet.FULL).getJSONArray("inter_gap_ignore").getString(2))
        assertEquals(90000, doc.profileSettings(AdSet.BASIC).getInt("inter_gap_ms"))
    }

    @Test
    fun fullPlacements() {
        val splash = full(AdKey.ADS_SPLASH)
        assertTrue(splash.enable)
        assertEquals(PlacementType.SPLASH, splash.type)
        assertEquals(AdType.INTER_THEN_NATIVE, splash.adType)
        assertEquals(1, splash.ids("inter").size)
        assertEquals(1, splash.ids("native").size)
        assertEquals("Full1", splash.template)
        assertEquals(AdFallback.SHOW_AVAILABLE, splash.fallback)

        // on_resume: khác aoa = Welcome Back, Tiếp tục chạy inter_then_native; native chân màn là native_welcome_back.
        val resume = full(AdKey.ON_RESUME)
        assertEquals(AdType.INTER_THEN_NATIVE, resume.adType)
        assertEquals(PlacementType.RESUME, resume.type)
        assertFalse(resume.customUi)
        assertEquals(1, resume.ids("inter").size)
        assertEquals(1, resume.ids("native").size)
        val welcome = full(AdKey.NATIVE_WELCOME_BACK)
        assertTrue(welcome.enable)
        assertEquals("Big1", welcome.template)
        assertEquals(3000L, welcome.waitMs)
        assertFalse(basic(AdKey.NATIVE_WELCOME_BACK).enable)

        val home = full(AdKey.BANNER_HOME)
        assertEquals(AdType.NATIVE_AS_BANNER_COLLAP, home.adType)
        assertEquals(30000L, home.refreshMs)
        assertEquals(setOf(2), home.openAdOnClicks)
        assertEquals("Collap1", home.template)

        // Mỗi trang Onboarding một placement riêng (mẫu: trang 2 Big4, tắt trang 4).
        assertTrue(full(AdKey.NATIVE_ONBOARDING_1).enable)
        assertEquals("Big4", full(AdKey.NATIVE_ONBOARDING_2).template)
        assertEquals(1, full(AdKey.NATIVE_ONBOARDING_3).ids("native").size)
        assertFalse(full(AdKey.NATIVE_ONBOARDING_4).enable)
        assertEquals(setOf(2), full(AdKey.NATIVE_FULL_ONBOARDING).pages)
        assertEquals(AdType.INTER_THEN_NATIVE, full(AdKey.INTER_AFTER_ONBOARDING).adType)
        assertEquals(AdType.BANNER_COLLAPSIBLE, full(AdKey.BANNER_SETTING).adType)
        assertEquals(AdType.REWARD, full(AdKey.REWARD_SAMPLE).adType)
        assertEquals(5000L, full(AdKey.REWARD_INTER_SAMPLE).waitMs)
        assertEquals(1, full(AdKey.INTER_HOME).step)
    }

    @Test
    fun basicPlacements() {
        val splash = basic(AdKey.ADS_SPLASH)
        assertTrue(splash.enable)
        assertEquals(AdType.AOA, splash.adType)
        assertEquals(1, splash.ids("aoa").size)
        assertFalse(basic(AdKey.NATIVE_LANGUAGE).enable)
        assertFalse(basic(AdKey.ON_RESUME).enable)
        for (on in listOf(AdKey.INTER_HOME, AdKey.BANNER_SETTING, AdKey.REWARD_SAMPLE, AdKey.REWARD_INTER_SAMPLE)) {
            assertTrue(on.key, basic(on).enable)
        }
        // Placement tắt vẫn giữ ID (splash_fallback có thể hiện inter của placement đang tắt).
        assertEquals(1, basic(AdKey.INTER_LANGUAGE).ids("inter").size)
    }

    @Test
    fun everyPlacementInTheFileIsKnownAndEveryKnownPlacementIsInTheFile() {
        val root = JSONObject(raw).getJSONObject("profiles")
        for (set in AdSet.entries) {
            val keys = root.getJSONObject(set.key).getJSONObject("placements").keys().asSequence().toSet()
            keys.forEach { assertTrue(it, AdKey.fromKey(it) != null) }
            AdKey.entries.forEach { assertTrue("${set.key}.${it.key}", it.key in keys) }
        }
    }

    @Test
    fun everyAdUnitHasIdsInTheFullProfile() {
        // Unit nào cũng phải có ID trong bộ full, trừ các unit chỉ dùng khi ad_type đổi sang loại khác.
        val optional = setOf(
            AdUnit.AOA_SPLASH, AdUnit.ON_RESUME, AdUnit.BANNER_HOME, AdUnit.NATIVE_INTER_HOME,
        )
        for (unit in AdUnit.entries - optional) {
            assertTrue(unit.name, full(unit.key).ids(unit.format.idType).isNotEmpty())
        }
    }

    @Test
    fun missingOrBrokenConfigTurnsEverythingOff() {
        for (raw in listOf("", "{", "[]", """{"profiles":{}}""")) {
            val d = AdConfigDocument(raw)
            for (key in AdKey.entries) {
                assertEquals(PlacementConfig.OFF, d.placement(AdSet.FULL, key))
                assertEquals(PlacementConfig.OFF, d.placement(AdSet.BASIC, key))
            }
            assertNull(d.adsProfile)
        }
    }

    @Test
    fun invalidValuesFallBackToDefaults() {
        val c = PlacementConfig.fromJson(
            JSONObject("""{"enable":true,"ad_type":"xyz","step":0,"wait_ms":-5,"fallback":"?","ad_ids":{"Inter":["a","","b","c","d"]}}"""),
        )
        assertTrue(c.enable)
        assertNull(c.adType)
        assertEquals(1, c.step)
        assertEquals(0L, c.waitMs)
        assertEquals(AdFallback.SHOW_AVAILABLE, c.fallback)
        assertEquals(listOf("a", "b", "c"), c.ids("inter"))
        assertEquals(AdFallback.SKIP, PlacementConfig.fromJson(JSONObject("""{"fallback":"skip"}""")).fallback)
        assertEquals(AdType.NATIVE_AS_BANNER_COLLAP, AdType.fromKey(" Native_As_Banner_Collap "))
    }

    @Test
    fun numberListsAcceptArraysOrText() {
        assertEquals(setOf(2, 4), PlacementConfig.intSet(org.json.JSONArray("[2,4]")))
        assertEquals(setOf(1, 2, 3), PlacementConfig.intSet("1, 2,3"))
        assertEquals(emptySet<Int>(), PlacementConfig.intSet(null))
    }
}
