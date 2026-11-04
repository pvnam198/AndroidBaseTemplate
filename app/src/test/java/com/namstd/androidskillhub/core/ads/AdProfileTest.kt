package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdProfileTest {
    private fun profile(adsEnable: Int, premium: Boolean = false, marketing: Boolean? = null, testAd: Boolean? = null) =
        resolveAdProfile(adsEnable, premium, marketing, testAd)

    @Test
    fun zeroIsOffAndOneIsAlwaysRemote() {
        assertEquals(AdProfile.OFF, profile(0))
        assertEquals(AdProfile.REMOTE, profile(1, marketing = false, testAd = true))
    }

    @Test
    fun twoMarketingUserIsRemoteEveryoneElseBasic() {
        assertEquals(AdProfile.REMOTE, profile(2, marketing = true))
        assertEquals(AdProfile.BASIC, profile(2, marketing = false))
        assertEquals(AdProfile.BASIC, profile(2, marketing = null))
    }

    @Test
    fun threeTestAdDeviceIsBasicEveryoneElseRemote() {
        assertEquals(AdProfile.BASIC, profile(3, testAd = true))
        assertEquals(AdProfile.REMOTE, profile(3, testAd = false))
        assertEquals(AdProfile.REMOTE, profile(3, testAd = null))
    }

    @Test
    fun unknownValueIsRemote() {
        assertEquals(AdProfile.REMOTE, profile(4))
        assertEquals(AdProfile.REMOTE, profile(-1))
    }

    @Test
    fun premiumIsAlwaysOff() {
        for (value in 0..4) assertEquals(AdProfile.OFF, profile(value, premium = true, marketing = true, testAd = false))
    }
}
