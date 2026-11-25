package com.namstd.androidskillhub.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdAudienceTest {
    @Test
    fun testAdHeadlineIsRecognised() {
        assertTrue(AdAudience.isTestAdHeadline("Test Ad: Flood-It!"))
        assertTrue(AdAudience.isTestAdHeadline("  test ad: Matties Watch Co."))
    }

    @Test
    fun realHeadlineIsNotTestAd() {
        assertFalse(AdAudience.isTestAdHeadline("Pangle Test Ads"))
        assertFalse(AdAudience.isTestAdHeadline("be - Multi-Service Platform"))
        assertFalse(AdAudience.isTestAdHeadline(""))
    }
}
