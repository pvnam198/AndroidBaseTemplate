package com.namstd.androidskillhub.core.ads

/**
 * `open_ad_on_clicks` của native collapsible: đếm **mọi click trên quảng cáo** (thẻ, nút CTA, nút X / thu gọn), lần
 * click thứ mấy nằm trong [openOnClicks] thì mở trang quảng cáo. Click trên thẻ vốn đã mở quảng cáo; với nút X thì
 * lần nằm trong danh sách mở quảng cáo thay vì thu gọn. Rỗng = tắt (nút X luôn chỉ thu gọn).
 */
class CollapClickGate(openOnClicks: Set<Int>) {
    private val clicks: Set<Int> = openOnClicks.filter { it >= 1 }.toSet()

    val isEnabled: Boolean = clicks.isNotEmpty()

    /** [clickNumber] đếm từ 1 (mọi click trên quảng cáo trong phiên app): true = lần click này mở quảng cáo. */
    fun opensAd(clickNumber: Int): Boolean = clickNumber in clicks
}
