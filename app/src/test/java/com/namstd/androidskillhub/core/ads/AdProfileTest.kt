package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdsProfileRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdProfileTest {
    private fun profile(rule: AdsProfileRule, premium: Boolean = false, marketing: Boolean? = null, testAd: Boolean? = null) =
        resolveAdProfile(rule, premium, marketing, testAd)

    @Test
    fun offAndFullForAll() {
        assertEquals(AdProfile.OFF, profile(AdsProfileRule.OFF))
        assertEquals(AdProfile.FULL, profile(AdsProfileRule.FULL_FOR_ALL, marketing = false, testAd = true))
    }

    @Test
    fun fullForPaidUsersOnly() {
        assertEquals(AdProfile.FULL, profile(AdsProfileRule.FULL_FOR_PAID_USERS, marketing = true))
        assertEquals(AdProfile.BASIC, profile(AdsProfileRule.FULL_FOR_PAID_USERS, marketing = false))
        // Chưa biết nguồn (Adjust chưa trả attribution) = như user tự nhiên.
        assertEquals(AdProfile.BASIC, profile(AdsProfileRule.FULL_FOR_PAID_USERS, marketing = null))
    }

    @Test
    fun fullExceptTestDevices() {
        assertEquals(AdProfile.BASIC, profile(AdsProfileRule.FULL_EXCEPT_TEST_DEVICES, testAd = true))
        assertEquals(AdProfile.FULL, profile(AdsProfileRule.FULL_EXCEPT_TEST_DEVICES, testAd = false))
        assertEquals(AdProfile.FULL, profile(AdsProfileRule.FULL_EXCEPT_TEST_DEVICES, testAd = null))
    }

    @Test
    fun premiumAlwaysOff() {
        for (rule in AdsProfileRule.entries) assertEquals(AdProfile.OFF, profile(rule, premium = true, marketing = true))
    }

    @Test
    fun ruleNamesMatchTheSheet() {
        assertEquals(
            listOf("off", "full_for_all", "full_for_paid_users", "full_except_test_devices"),
            AdsProfileRule.entries.map { it.key },
        )
        assertEquals(AdsProfileRule.FULL_FOR_PAID_USERS, AdsProfileRule.fromKey(" Full_For_Paid_Users "))
        assertNull(AdsProfileRule.fromKey("2"))
    }
}
