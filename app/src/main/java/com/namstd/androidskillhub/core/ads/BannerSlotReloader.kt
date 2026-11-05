package com.namstd.androidskillhub.core.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Handle của một slot banner: [destroy] gỡ bỏ nó, [reload] thay ngay một ad mới vào - dành cho các
 * màn tự reload bằng tay (khi resume, khi chuyển tab...) thay cho, hoặc bổ sung thêm vào, timer.
 */
interface BannerSlotHandle : AdHandle {
    /** Reload ngay: quay về shimmer nhỏ, rồi tới ad mới. Bỏ qua nếu slot đang load dở. */
    fun reload()

    companion object {
        /** Cho slot chưa từng load (SDK chưa sẵn sàng, ads tắt): không có gì để reload hay destroy. */
        val NONE = object : BannerSlotHandle {
            override fun reload() = Unit
            override fun destroy() = Unit
        }
    }
}

/**
 * Timer reload của slot banner - dùng chung cho AdMob banner của [BannerAds] và [NativeCollapseAd]
 * (native tự làm có thể thay chỗ slot banner); không native ad nào khác dùng nó. Đây là trigger duy
 * nhất có sẵn: mọi trigger khác (resume, chuyển tab...) là code riêng của màn hình gọi [reloadNow].
 *
 * Khi bật [autoReload] và [gapMs] > 0, slot reload sau [gapMs] kể từ khi mỗi ad được hiển thị (hoặc
 * một lượt load thất bại). Cả hai đều do caller truyền vào - các base screen lấy mặc định từ Remote
 * Config; core không bao giờ tự đọc config. Lần reload đến hạn trong lúc activity chưa resume sẽ chờ
 * onResume. Không reload khi đang có một lượt load chạy dở. Slot báo tiến trình qua [onLoadStarted],
 * [onShown] và [onFailed]. Chỉ chạy trên main thread.
 */
internal class BannerSlotReloader(
    activity: Activity,
    /** Timer có chạy hay không. */
    private val autoReload: Boolean,
    /** Thời gian từ lúc ad được hiển thị (hoặc load thất bại) tới lần reload của timer; 0 = không có timer. */
    private val gapMs: Long,
    private val onReload: () -> Unit,
) {
    private val lifecycle: Lifecycle? = (activity as? LifecycleOwner)?.lifecycle
    private val handler = Handler(Looper.getMainLooper())
    private val tick = Runnable { fireTimer() }

    /** True từ lúc bắt đầu load cho tới khi ad được hiển thị hoặc load thất bại. Khởi đầu là true: lượt load đầu của slot đang chạy. */
    private var busy = true
    /** Một lần reload của timer đã đến hạn trong lúc activity chưa resume. */
    private var due = false
    private var cancelled = false

    private val observer = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            if (due) fireTimer()
        }
    }

    init {
        lifecycle?.addObserver(observer)
    }

    /** Reload bằng tay - màn hình tự quyết khi nào; chỉ một lượt load đang chạy mới chặn được nó. */
    fun reloadNow() {
        if (cancelled || busy) return
        due = false
        onReload()
    }

    fun onLoadStarted() {
        busy = true
        due = false
        handler.removeCallbacks(tick)
    }

    /** Ad đã lên màn hình - khoảng cách của timer bắt đầu tính từ bây giờ. */
    fun onShown() = settle()

    /** Không có gì để hiện - thử lại sau một khoảng cách. */
    fun onFailed() = settle()

    fun cancel() {
        cancelled = true
        due = false
        handler.removeCallbacks(tick)
        lifecycle?.removeObserver(observer)
    }

    private fun fireTimer() {
        if (cancelled || busy) return
        if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == false) {
            due = true
            return
        }
        reloadNow()
    }

    private fun settle() {
        busy = false
        handler.removeCallbacks(tick)
        // Không có khoảng cách thì không có timer: nếu không nó sẽ reload ngay khi mỗi ad vừa hiện.
        if (cancelled || !autoReload || gapMs <= 0) return
        handler.postDelayed(tick, gapMs)
    }

    companion object {
        /** Khi reload, slot hiện shimmer nhỏ ít nhất trong khoảng thời gian này trước khi ad mới xuất hiện. */
        const val MIN_RELOAD_SHIMMER_MS = 1_200L
    }
}
