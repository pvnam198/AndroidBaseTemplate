package com.namstd.androidskillhub.core.ads

import android.os.Handler
import android.os.Looper

private val mainThreadHandler = Handler(Looper.getMainLooper())

/** Chạy [block] trên main thread — ads-mobile-sdk gọi các callback full-screen-content từ một background dispatcher. */
internal fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainThreadHandler.post(block)
}
