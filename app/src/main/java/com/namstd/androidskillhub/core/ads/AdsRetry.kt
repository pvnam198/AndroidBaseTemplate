package com.namstd.androidskillhub.core.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.namstd.androidskillhub.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Lịch thử lại khi request lúc mở app (xin đồng ý quảng cáo UMP, fetch Remote Config) lỗi vì mạng chập chờn:
 * chờ 2s, 4s, 8s giữa các lần. Hết lượt thì chờ mạng có lại ([NetworkRegain]).
 */
internal val RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L)

/** Thời gian chờ trước lần thử lại thứ [attempt] (0 = sau lần lỗi đầu), null = hết lượt. */
internal fun retryDelay(attempt: Int): Long? = RETRY_DELAYS_MS.getOrNull(attempt)

/** Log các mốc khởi động quảng cáo (bản debug): `adb logcat -s AdsInit`. */
internal fun logInit(message: String) {
    if (BuildConfig.DEBUG) android.util.Log.d("AdsInit", message)
}

/**
 * Báo khi mạng có lại (mạng mặc định được hệ thống xác nhận ra được internet - `NET_CAPABILITY_VALIDATED`), cho các
 * việc khởi động đã hết lượt thử lại. Mỗi việc đăng ký theo key, chạy một lần rồi bỏ; không còn việc chờ thì gỡ callback.
 */
object NetworkRegain {
    private val main = Handler(Looper.getMainLooper())
    private val waiting = ConcurrentHashMap<String, () -> Unit>()
    private var manager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized
    fun awaitOnce(context: Context, key: String, action: () -> Unit) {
        logInit("$key: chờ mạng có lại")
        waiting[key] = action
        if (callback != null) return
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) main.post { fire() }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess {
            manager = cm
            callback = cb
        }
    }

    @Synchronized
    private fun fire() {
        val actions = waiting.values.toList()
        waiting.clear()
        callback?.let { cb -> runCatching { manager?.unregisterNetworkCallback(cb) } }
        callback = null
        if (actions.isNotEmpty()) logInit("mạng có lại: chạy lại ${actions.size} việc")
        actions.forEach { it() }
    }
}
