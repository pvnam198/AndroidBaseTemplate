package com.namstd.androidskillhub.core.ads

import com.namstd.androidskillhub.core.config.RemoteConfig

/**
 * Các danh sách fallback theo thứ tự. Giá trị mặc định là các test ad unit Android của Google.
 * Thay mọi danh sách, cùng với app ID trong manifest, trước khi phát hành.
 */
data class AdUnitIds(
    val banner: List<String> = listOf(TEST_BANNER),
    val bannerCollapsible: List<String> = listOf(TEST_BANNER_COLLAPSIBLE),
    val interstitial: List<String> = listOf(TEST_INTERSTITIAL),
    val native: Map<NativePlacement, List<String>> = NativePlacement.entries.associateWith { listOf(TEST_NATIVE) },
    val rewarded: List<String> = listOf(TEST_REWARDED),
    val rewardedInterstitial: List<String> = listOf(TEST_REWARDED_INTERSTITIAL),
    val appOpen: List<String> = listOf(TEST_APP_OPEN),
) {
    companion object {
        /**
         * Dựng [AdUnitIds] từ các key tiering của [RemoteConfig] (highfloor → medium → all_price).
         * Format nào chưa set (list rỗng) thì giữ test id mặc định. `id_native` áp chung cho mọi placement.
         * Rewarded / RewardedInterstitial (hiện chưa dùng) luôn dùng test id.
         */
        fun fromConfig(): AdUnitIds {
            val defaults = AdUnitIds()
            val native = RemoteConfig.idNative.ifEmpty { null }
            return AdUnitIds(
                banner = RemoteConfig.idBanner.ifEmpty { defaults.banner },
                bannerCollapsible = RemoteConfig.idBannerCollapsible.ifEmpty { defaults.bannerCollapsible },
                interstitial = RemoteConfig.idInterstitial.ifEmpty { defaults.interstitial },
                native = if (native != null) NativePlacement.entries.associateWith { native } else defaults.native,
                appOpen = RemoteConfig.idAppOpen.ifEmpty { defaults.appOpen },
            )
        }

        const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_BANNER_COLLAPSIBLE = "ca-app-pub-3940256099942544/2014213617"
        const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
        const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
        const val TEST_REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
        const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    }
}
