package com.namstd.androidskillhub.core.ads

import kotlinx.coroutines.delay

/**
 * Đếm ngược theo giây cho native full trước khi hiện nút đóng / đi tiếp (`wait_ms` của placement, đơn vị mili-giây).
 * Mỗi bước là (số giây đang hiện, thời gian giữ số đó): 5000 → 5,4,3,2,1 mỗi số 1s; 2500 → 3 (0,5s), 2, 1;
 * dưới 1s vẫn hiện "1" trong phần còn lại; ≤ 0 → không có bước nào.
 */
internal fun countdownSteps(durationMs: Long): List<Pair<Int, Long>> {
    if (durationMs <= 0L) return emptyList()
    val first = ((durationMs + 999L) / 1_000L).toInt()
    return (first downTo 1).map { second ->
        val hold = if (second == first) durationMs - (first - 1) * 1_000L else 1_000L
        second to hold
    }
}

/** Chạy [countdownSteps]: gọi [onSecond] với số giây còn lại mỗi lần số đổi; trả về khi hết giờ. Huỷ theo coroutine. */
suspend fun runAdCountdown(durationMs: Long, onSecond: (Int) -> Unit) {
    for ((second, hold) in countdownSteps(durationMs)) {
        onSecond(second)
        delay(hold)
    }
}
