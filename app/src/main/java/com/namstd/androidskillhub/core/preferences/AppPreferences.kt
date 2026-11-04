package com.namstd.androidskillhub.core.preferences

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.namstd.androidskillhub.core.language.AppLanguage
import androidx.core.content.edit

class AppPreferences private constructor(context: Context) {
    private val values = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var selectedLanguage: String
        get() = values.getString(KEY_LANGUAGE, AppLanguage.default.code) ?: AppLanguage.default.code
        set(value) {
            requireNotNull(AppLanguage.fromCode(value)) { "Unsupported language code: $value" }
            values.edit { putString(KEY_LANGUAGE, value) }
        }

    var setupCompleted: Boolean
        get() = values.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = values.edit { putBoolean(KEY_SETUP_COMPLETE, value) }

    var isPremium: Boolean
        get() = values.getBoolean(KEY_PREMIUM, false)
        set(value) = values.edit { putBoolean(KEY_PREMIUM, value) }

    /**
     * Đếm số lần đã yêu cầu hiện interstitial, lưu qua các lần mở app. Dùng cho tần suất
     * `step_count`: chỉ hiện ở lần chia hết cho [com.namstd.androidskillhub.core.config.RemoteConfig.stepCount].
     */
    var interstitialStepCounter: Int
        get() = values.getInt(KEY_INTER_STEP_COUNTER, 0)
        set(value) = values.edit { putInt(KEY_INTER_STEP_COUNTER, value) }

    /** Kết quả kiểm tra nguồn user cho `ads_enable = 2`: true = đến từ marketing, null = attribution chưa về. */
    var isMarketingUser: Boolean?
        get() = optionalBoolean(KEY_MARKETING_USER)
        set(value) = putOptionalBoolean(KEY_MARKETING_USER, value)

    /** Kết quả kiểm tra test ad cho `ads_enable = 3`: true = máy nhận quảng cáo mẫu, null = chưa load native nào. */
    var isTestAdDevice: Boolean?
        get() = optionalBoolean(KEY_TEST_AD_DEVICE)
        set(value) = putOptionalBoolean(KEY_TEST_AD_DEVICE, value)

    private fun optionalBoolean(key: String): Boolean? = if (values.contains(key)) values.getBoolean(key, false) else null

    private fun putOptionalBoolean(key: String, value: Boolean?) =
        values.edit { if (value == null) remove(key) else putBoolean(key, value) }

    fun applyLocale(language: String = selectedLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    /** Generic cache slot for other singletons (e.g. [com.namstd.androidskillhub.core.config.RemoteConfig]) to persist values by key into this same shared prefs file. */
    fun getCachedBoolean(key: String, default: Boolean): Boolean = values.getBoolean(key, default)

    fun putCachedBoolean(key: String, value: Boolean) {
        values.edit { putBoolean(key, value) }
    }

    fun getCachedLong(key: String, default: Long): Long = values.getLong(key, default)

    fun putCachedLong(key: String, value: Long) {
        values.edit { putLong(key, value) }
    }

    fun getCachedString(key: String, default: String): String = values.getString(key, default) ?: default

    fun putCachedString(key: String, value: String) {
        values.edit { putString(key, value) }
    }

    fun resetOnboarding() {
        setupCompleted = false
    }

    companion object {
        private const val FILE_NAME = "app_preferences"
        private const val KEY_LANGUAGE = "selected_language"
        private const val KEY_SETUP_COMPLETE = "setup_completed"
        private const val KEY_PREMIUM = "is_premium"
        private const val KEY_INTER_STEP_COUNTER = "interstitial_step_counter"
        private const val KEY_MARKETING_USER = "is_marketing_user"
        private const val KEY_TEST_AD_DEVICE = "is_test_ad_device"

        @Volatile private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
    }
}
