package com.namstd.androidskillhub.core.billing

import com.android.billingclient.api.Purchase

/** Loại sản phẩm trên Google Play: gói thuê bao, mua một lần dùng mãi, hoặc mua một lần tiêu hao (mua lại được). */
enum class BillingProductType { SUBS, NON_CONSUMABLE, CONSUMABLE }

/** Một sản phẩm khai báo trên Play Console mà app bán. */
data class BillingProduct(val productId: String, val type: BillingProductType)

/**
 * Một sản phẩm trong một giao dịch. Google Play cho mua nhiều sản phẩm trong 1 giao dịch (`Purchase.products`),
 * mỗi sản phẩm thành 1 record; token và trạng thái là chung cho cả giao dịch.
 */
data class PurchaseRecord(
    val productId: String,
    val purchaseToken: String,
    val purchaseTime: Long = 0L,
    val orderId: String? = null,
    /** false = đang chờ thanh toán (PENDING), chưa được tính là đã mua. */
    val isPurchased: Boolean = false,
    val isAcknowledged: Boolean = false,
    val isAutoRenewing: Boolean = false,
) {
    companion object {
        fun Purchase.toPurchaseRecords(): List<PurchaseRecord> = products.map { productId ->
            PurchaseRecord(
                productId = productId,
                purchaseToken = purchaseToken,
                purchaseTime = purchaseTime,
                orderId = orderId,
                isPurchased = purchaseState == Purchase.PurchaseState.PURCHASED,
                isAcknowledged = isAcknowledged,
                isAutoRenewing = isAutoRenewing,
            )
        }
    }
}

/** Sản phẩm vừa mua xong, kèm thông tin giá (nếu đã query được). */
data class PurchasedItem(val record: PurchaseRecord, val productDetail: BillingProductDetail?)

/** Kết quả một lần mua (từ luồng mua của Google Play). */
sealed class PurchaseUpdate {
    data class Succeeded(val purchases: List<PurchasedItem>) : PurchaseUpdate()
    data object AlreadyOwned : PurchaseUpdate()
    data object UserCanceled : PurchaseUpdate()
    data object Error : PurchaseUpdate()
}

sealed class BillingConnectionResult {
    data object Connected : BillingConnectionResult()
    data object Disconnected : BillingConnectionResult()
    data object Failed : BillingConnectionResult()
}

data class BillingQueryResult(
    val productDetails: List<BillingProductDetail>,
    val purchaseRecords: List<PurchaseRecord>,
    /** false = query giao dịch bị lỗi: [purchaseRecords] rỗng KHÔNG có nghĩa là user không có gói. */
    val isPurchasesQueryOk: Boolean = true,
)

/** Trạng thái danh sách gói cho màn Premium. */
sealed class ProductsResult {
    data object Loading : ProductsResult()
    data class Success(
        val products: List<BillingProductDetail>,
        val purchaseRecords: List<PurchaseRecord>,
        val isPurchasesQueryOk: Boolean = true,
    ) : ProductsResult()

    /** Không kết nối được Google Play hoặc không gói nào trả về (chưa khai báo trên Play Console, máy không có Play...). */
    data object Unavailable : ProductsResult()
}
