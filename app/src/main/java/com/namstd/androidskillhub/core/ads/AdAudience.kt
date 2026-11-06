package com.namstd.androidskillhub.core.ads

import android.content.Context
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAttribution
import com.namstd.androidskillhub.core.preferences.AppPreferences

/**
 * Hai phép kiểm tra user mà `ads_profile` cần (xem [resolveAdProfile]). Kết quả lưu trong [AppPreferences]
 * nên lần mở app sau dùng luôn, không phải chờ kiểm tra lại:
 * - [isMarketingUser] (`full_for_paid_users`): user đến từ marketing, theo attribution của Adjust.
 * - [isTestAdDevice] (`full_except_test_devices`): máy nhận quảng cáo mẫu của Google - native có headline "Test Ad".
 *   Chỉ kiểm tra được khi có native load, nên máy đã bị đánh dấu thì giữ nguyên (BASIC không load native).
 */
object AdAudience {
    private lateinit var prefs: AppPreferences

    /** Gọi sau `Adjust.initSdk`. Listener attribution gắn trên `AdjustConfig` (xem AndroidSkillHubApp). */
    fun initialize(context: Context) {
        prefs = AppPreferences.getInstance(context)
        Adjust.getAttribution { onAttribution(it) }
    }

    val isMarketingUser: Boolean? get() = prefs.isMarketingUser
    val isTestAdDevice: Boolean? get() = prefs.isTestAdDevice

    /** Attribution từ Adjust (lần đầu về trễ vài giây sau khi cài). Network chứa "organic" hoặc untrusted = không phải marketing. */
    fun onAttribution(attribution: AdjustAttribution?) {
        val network = attribution?.network?.trim().orEmpty()
        if (network.isEmpty()) return
        prefs.isMarketingUser = !network.contains("organic", ignoreCase = true) &&
            !network.equals(UNTRUSTED_NETWORK, ignoreCase = true)
    }

    /** Gọi mỗi khi một native load xong: Google trả quảng cáo mẫu thì headline bắt đầu bằng "Test Ad". */
    fun onNativeLoaded(headline: String?) {
        if (headline.isNullOrBlank()) return
        prefs.isTestAdDevice = headline.trimStart().startsWith(TEST_AD_PREFIX, ignoreCase = true)
    }

    private const val UNTRUSTED_NETWORK = "Untrusted Devices"
    private const val TEST_AD_PREFIX = "Test Ad"
}
