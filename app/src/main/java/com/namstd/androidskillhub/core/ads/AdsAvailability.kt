package com.namstd.androidskillhub.core.ads

import android.os.Handler
import android.os.Looper
import com.namstd.androidskillhub.core.config.RemoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quảng cáo đã dùng được chưa: SDK đã khởi tạo (qua bước đồng ý UMP) **và** đã có cấu hình quảng cáo từ Remote Config.
 * Lúc mở app mà mạng lỗi thì hai bước này được thử lại sau ([Ads], [RemoteConfig]); khi xong, [notifyChanged] chạy
 * các việc đã hoãn (preload) và báo [version] để màn đang mở load lại slot quảng cáo của nó (BaseActivity / BaseFragment).
 */
object AdsAvailability {
    private val main = Handler(Looper.getMainLooper())
    private val deferred = DeferredWork { usable }
    private val _version = MutableStateFlow(0)

    /** Tăng mỗi lần trạng thái đổi (SDK vừa sẵn sàng / vừa có config). */
    val version: StateFlow<Int> = _version.asStateFlow()

    val usable: Boolean get() = Ads.isReady && RemoteConfig.hasAdConfig()

    /** Chạy [action] ngay nếu đã dùng được, không thì hoãn tới khi dùng được (gộp theo [key], chạy một lần). */
    fun runWhenUsable(key: String, action: () -> Unit) = deferred.submit(key, action)

    fun notifyChanged() {
        main.post {
            if (usable) logInit("quảng cáo dùng được")
            deferred.flush()
            _version.value++
        }
    }

    /** Bỏ mọi việc đang hoãn (tắt quảng cáo - Premium). */
    fun clear() = deferred.clear()
}

/** Hàng việc chờ tới khi [ready] (tách riêng, không phụ thuộc Android, để test). */
internal class DeferredWork(private val ready: () -> Boolean) {
    private val pending = LinkedHashMap<String, () -> Unit>()

    @Synchronized
    fun submit(key: String, action: () -> Unit) {
        if (ready()) action() else pending[key] = action
    }

    fun flush() {
        if (!ready()) return
        val actions = synchronized(this) { pending.values.toList().also { pending.clear() } }
        actions.forEach { it() }
    }

    @Synchronized
    fun clear() = pending.clear()

    @Synchronized
    fun size() = pending.size
}
