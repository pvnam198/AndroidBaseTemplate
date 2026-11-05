package com.namstd.androidskillhub.core.ads

import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.google.android.libraries.ads.mobile.sdk.common.AdValue

/** Gửi sự kiện paid ad của AdMob lên Adjust dưới dạng ad revenue, dùng chung cho `onAdPaid` của mọi định dạng ad. */
object AdjustRevenueLogger {
    private const val SOURCE_ADMOB = "admob_sdk"

    fun log(adValue: AdValue) {
        val revenue = AdjustAdRevenue(SOURCE_ADMOB)
        revenue.setRevenue(adValue.valueMicros / 1_000_000.0, adValue.currencyCode)
        Adjust.trackAdRevenue(revenue)
    }
}
