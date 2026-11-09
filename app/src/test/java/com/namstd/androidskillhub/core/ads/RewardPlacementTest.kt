package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.AdPlacementsDocument
import com.namstd.androidskillhub.core.config.AdSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardPlacementTest {
    @Test
    fun everyRewardPlacementUsesARewardUnitOfItsOwnPlacement() {
        RewardPlacement.entries.forEach {
            assertEquals(it.adKey, it.unit.key)
            assertTrue(it.unit.format == AdFormat.REWARDED || it.unit.format == AdFormat.REWARDED_INTERSTITIAL)
        }
    }

    @Test
    fun kindFollowsTheUnitFormat() {
        assertEquals(RewardKind.REWARD, RewardPlacement.REWARD_SAMPLE.kind)
        assertEquals(RewardKind.REWARD_INTER, RewardPlacement.REWARD_INTER_SAMPLE.kind)
    }

    @Test
    fun rewardPlacementsReadEnableAndWaitMsFromConfig() {
        val doc = AdPlacementsDocument(
            """{ "full": { "reward_sample": { "enable": false }, "reward_inter_sample": { "wait_ms": 3000 } },
                 "basic": { "reward_sample": {} } }""",
        )
        assertFalse(doc.placement(AdSet.FULL, AdKey.REWARD_SAMPLE).enable)
        assertEquals(3000L, doc.placement(AdSet.FULL, AdKey.REWARD_INTER_SAMPLE).waitMs)
        assertTrue(doc.placement(AdSet.BASIC, AdKey.REWARD_SAMPLE).enable)
        assertFalse(doc.placement(AdSet.BASIC, AdKey.REWARD_INTER_SAMPLE).enable)   // basic: không ghi = tắt
        assertEquals(5000L, AdKey.REWARD_INTER_SAMPLE.fullDefault.waitMs)
    }
}
