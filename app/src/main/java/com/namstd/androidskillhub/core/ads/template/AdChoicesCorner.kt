package com.namstd.androidskillhub.core.ads.template

import android.view.View
import android.view.ViewGroup

/**
 * Chừa góc cho biểu tượng AdChoices ("i"). SDK vẽ icon (~15dp) ở góc trên trái (`setAdChoicesPlacement(TOP_LEFT)` -
 * xem NativeAds) của vùng nội dung *bên trong padding* của NativeAdView, nên với mẫu card có padding nó đè lên phần
 * tử đầu tiên (nút CTA, icon app, nhãn "Ad"). Chuyển padding của khung vào lớp con: icon nằm sát góc card, và nội dung
 * bắt đầu dưới icon (padding trên tối thiểu [TOP_DP]). Mẫu Full không có padding: góc trên trái là header trống / ảnh,
 * không cần đổi. Gọi trước khi gắn ad (cả cho shimmer - để cùng kích thước).
 */
object AdChoicesCorner {
    private const val TOP_DP = 16

    fun reserve(root: View) {
        if (root !is ViewGroup || root.childCount == 0) return
        if (root.paddingLeft == 0 && root.paddingTop == 0 && root.paddingRight == 0 && root.paddingBottom == 0) return
        val content = root.getChildAt(0)
        val top = maxOf(root.paddingTop, (TOP_DP * root.resources.displayMetrics.density).toInt())
        content.setPadding(
            content.paddingLeft + root.paddingLeft,
            content.paddingTop + top,
            content.paddingRight + root.paddingRight,
            content.paddingBottom + root.paddingBottom,
        )
        root.setPadding(0, 0, 0, 0)
    }
}
