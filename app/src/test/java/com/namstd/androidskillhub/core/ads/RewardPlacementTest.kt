package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.AdConfigDocument
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
        val doc = AdConfigDocument(
            """{ "profiles": {
                 "full": { "placements": {
                   "reward_sample": { "type": "reward", "ad_type": "reward", "enable": false },
                   "reward_inter_sample": { "type": "reward_inter", "ad_type": "reward_inter", "enable": true, "wait_ms": 3000 } } },
                 "basic": { "placements": { "reward_sample": { "type": "reward", "ad_type": "reward", "enable": true } } } } }""",
        )
        assertFalse(doc.placement(AdSet.FULL, AdKey.REWARD_SAMPLE).enable)
        assertEquals(3000L, doc.placement(AdSet.FULL, AdKey.REWARD_INTER_SAMPLE).waitMs)
        assertTrue(doc.placement(AdSet.BASIC, AdKey.REWARD_SAMPLE).enable)
        assertFalse(doc.placement(AdSet.BASIC, AdKey.REWARD_INTER_SAMPLE).enable)   // không có trong bộ = tắt
    }
}
