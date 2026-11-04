package com.namstd.androidskillhub.core.ads

/**
 * Click-bait của native collapsible (key `collap_config`), tính trên **nút X** (thu gọn): mỗi chữ số là một lần
 * bấm X mở trang quảng cáo thay vì thu gọn. `357` = lần bấm X thứ 3, 5, 7 mở quảng cáo, các lần khác thu gọn như
 * thường (kể cả sau lần 7). `0` / rỗng = tắt. Bấm vào thẻ quảng cáo luôn mở quảng cáo như native thường.
 */
class CollapClickGate(raw: String) {
    private val adTaps: Set<Int> = raw.filter { it.isDigit() }.map { it.digitToInt() }.filter { it >= 1 }.toSet()

    /** True khi có ít nhất một lần bấm X mở quảng cáo - cần bật custom click gesture cho ad. */
    val isEnabled: Boolean = adTaps.isNotEmpty()

    /** [closeTapNumber] đếm từ 1: true = lần bấm X này mở quảng cáo, false = thu gọn. */
    fun opensAd(closeTapNumber: Int): Boolean = closeTapNumber in adTaps
}
