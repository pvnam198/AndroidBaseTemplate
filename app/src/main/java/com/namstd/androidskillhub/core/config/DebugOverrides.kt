package com.namstd.androidskillhub.core.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Override cục bộ (chỉ dùng ở DEBUG) cho các giá trị Remote Config, đánh khoá theo tên key RC và lưu dạng
 * chuỗi thô trong file prefs riêng (tách khỏi [AppPreferences] để không bao giờ đụng với cache của lần
 * fetch). Override, khi đã đặt, thắng Firebase/default trong [RemoteConfig]. Không làm gì cho tới khi gọi [init].
 */
object DebugOverrides {
    private const val FILE_NAME = "debug_overrides"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    /** Tăng mỗi lần override đổi - [RemoteConfig] dùng để biết khi nào parse lại cấu hình. */
    @Volatile
    var version: Int = 0
        private set

    fun get(key: String): String? = prefs?.getString(key, null)

    /** Tên các key đang có override. */
    fun keys(): Set<String> = prefs?.all?.keys.orEmpty()

    fun set(key: String, value: String) {
        prefs?.edit { putString(key, value) }
        version++
    }

    fun remove(key: String) {
        prefs?.edit { remove(key) }
        version++
    }

    fun clear() {
        prefs?.edit { clear() }
        version++
    }
}
