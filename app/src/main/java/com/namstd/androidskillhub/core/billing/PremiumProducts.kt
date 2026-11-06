package com.namstd.androidskillhub.core.billing

/**
 * Các sản phẩm app bán - product ID phải trùng với Play Console. Đây là bộ mẫu của base, mỗi loại một gói; app
 * thật thay ID của mình (thêm / bớt thoải mái):
 * - [YEARLY] - thuê bao (SUBS): có Premium khi gói còn hạn;
 * - [LIFETIME] - mua một lần dùng mãi (NON_CONSUMABLE): có Premium vĩnh viễn;
 * - [COIN_PACK] - mua một lần tiêu hao (CONSUMABLE): KHÔNG cho Premium; mua xong được consume để mua lại được, app
 *   tự cộng vật phẩm (xu, lượt...) khi nhận [PurchaseUpdate.Succeeded] có sản phẩm này.
 */
object PremiumProducts {
    const val YEARLY = "premium_yearly"
    const val LIFETIME = "premium_lifetime"
    const val COIN_PACK = "coin_pack_100"

    val all: List<BillingProduct> = listOf(
        BillingProduct(YEARLY, BillingProductType.SUBS),
        BillingProduct(LIFETIME, BillingProductType.NON_CONSUMABLE),
        BillingProduct(COIN_PACK, BillingProductType.CONSUMABLE),
    )

    /** Sản phẩm cho Premium (tắt quảng cáo): mọi gói trừ loại tiêu hao. */
    val premiumIds: Set<String> = all.filter { it.type != BillingProductType.CONSUMABLE }.map { it.productId }.toSet()

    fun typeOf(productId: String): BillingProductType? = all.find { it.productId == productId }?.type

    /** Gói được chọn sẵn khi mở màn Premium. */
    const val DEFAULT_SELECTED = YEARLY
}
