package com.namstd.androidskillhub.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.InAppMessageParams
import com.android.billingclient.api.InAppMessageResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.namstd.androidskillhub.core.billing.BillingProductDetail.Companion.toBillingProductDetail
import com.namstd.androidskillhub.core.billing.PurchaseRecord.Companion.toPurchaseRecords
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Lớp bọc Google Play Billing: kết nối, query gói + giao dịch, mở luồng mua, acknowledge / consume, và
 * thông báo lỗi thanh toán của Google Play. Không giữ trạng thái Premium - việc đó do [Premium] làm.
 */
class GooglePlayBilling(
    context: Context,
    /** null = không gửi doanh thu mua trong app lên Adjust. */
    private val adjustTracker: AdjustIapTracker? = null,
) {
    private val detailProducts = HashMap<String, ProductDetails>()
    private val billingProducts = HashMap<String, BillingProduct>()

    @Volatile
    var purchaseUpdateListener: ((PurchaseUpdate) -> Unit)? = null

    private val purchasesListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> handleSuccessfulPurchases(purchases)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> emit(PurchaseUpdate.AlreadyOwned)
            BillingClient.BillingResponseCode.USER_CANCELED -> emit(PurchaseUpdate.UserCanceled)
            else -> {
                Log.w(TAG, "purchase failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                emit(PurchaseUpdate.Error)
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private fun emit(update: PurchaseUpdate) {
        purchaseUpdateListener?.invoke(update)
    }

    private fun handleSuccessfulPurchases(purchases: List<Purchase>?) {
        if (purchases.isNullOrEmpty()) {
            // OK mà không có giao dịch nào: thường là sản phẩm đã sở hữu từ phiên trước.
            emit(PurchaseUpdate.AlreadyOwned)
            return
        }
        val records = purchases.flatMap { it.toPurchaseRecords() }.filter { it.isPurchased }
        records.forEach { record ->
            adjustTracker?.trackPurchase(record, detailProducts[record.productId]?.toBillingProductDetail())
            consumeOrAcknowledge(record)
        }
        // Gói tiêu hao được báo sau khi consume xong (xem consumeOrAcknowledge); ở đây chỉ báo các gói còn lại.
        val owned = records.filterNot { isConsumable(it) }
        if (owned.isNotEmpty()) emit(PurchaseUpdate.Succeeded(owned.map(::purchasedItem)))
        // Còn lại là giao dịch PENDING (chờ thanh toán): Google Play sẽ gọi lại listener khi thanh toán xong.
    }

    private val connectLock = Any()
    private var pendingConnection: CompletableDeferred<BillingConnectionResult>? = null

    /**
     * Kết nối tới Google Play. Gọi startConnection khi client đang kết nối dở thì BillingClient báo lỗi ngay, nên
     * các lần gọi song song dùng chung một lần kết nối đang chạy.
     */
    suspend fun connect(): BillingConnectionResult {
        if (billingClient.isReady) return BillingConnectionResult.Connected
        val deferred = synchronized(connectLock) {
            pendingConnection?.takeIf { it.isActive }
                ?: CompletableDeferred<BillingConnectionResult>().also {
                    pendingConnection = it
                    startConnection(it)
                }
        }
        return deferred.await()
    }

    private fun startConnection(deferred: CompletableDeferred<BillingConnectionResult>) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                deferred.complete(BillingConnectionResult.Disconnected)
            }

            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    deferred.complete(BillingConnectionResult.Connected)
                } else {
                    Log.w(TAG, "setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    deferred.complete(BillingConnectionResult.Failed)
                }
            }
        })
    }

    /**
     * Query giá các [products] và mọi giao dịch user đang có; acknowledge / consume giao dịch nào còn sót, và gửi
     * doanh thu kỳ gia hạn hiện tại lên Adjust.
     */
    suspend fun queryProductDetailsAndPurchases(products: List<BillingProduct>): BillingQueryResult = coroutineScope {
        products.forEach { billingProducts[it.productId] = it }
        val detailsDeferred = async { fetchProductDetails(products) }
        val purchasesDeferred = async { fetchPurchases() }
        val details = detailsDeferred.await()
        val purchases = purchasesDeferred.await()
        val records = purchases.orEmpty()

        detailProducts.putAll(details)
        records.filter { it.isPurchased }.forEach { consumeOrAcknowledge(it) }
        adjustTracker?.trackPurchases(records, details.mapValues { it.value.toBillingProductDetail() })

        BillingQueryResult(
            productDetails = details.values.map { it.toBillingProductDetail() },
            purchaseRecords = records,
            isPurchasesQueryOk = purchases != null,
        )
    }

    private suspend fun fetchProductDetails(products: List<BillingProduct>): Map<String, ProductDetails> {
        val subs = products.filter { it.type == BillingProductType.SUBS }.map { it.productId }
        val inApp = products.filter { it.type != BillingProductType.SUBS }.map { it.productId }
        return (queryDetails(subs, BillingClient.ProductType.SUBS) + queryDetails(inApp, BillingClient.ProductType.INAPP))
            .associateBy { it.productId }
    }

    private suspend fun queryDetails(ids: List<String>, type: String): List<ProductDetails> {
        if (ids.isEmpty()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            ids.map { QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(type).build() },
        ).build()
        val deferred = CompletableDeferred<List<ProductDetails>>()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails($type) failed: ${result.responseCode} ${result.debugMessage}")
            }
            deferred.complete(details.productDetailsList)
        }
        return deferred.await()
    }

    /** null nếu Google Play báo lỗi khi query (khác với user không có giao dịch nào). */
    private suspend fun fetchPurchases(): List<PurchaseRecord>? {
        val subs = queryPurchases(BillingClient.ProductType.SUBS) ?: return null
        val inApp = queryPurchases(BillingClient.ProductType.INAPP) ?: return null
        return (subs + inApp).flatMap { it.toPurchaseRecords() }
    }

    private suspend fun queryPurchases(type: String): List<Purchase>? {
        val deferred = CompletableDeferred<List<Purchase>?>()
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, list ->
            deferred.complete(list.takeIf { result.responseCode == BillingClient.BillingResponseCode.OK })
        }
        return deferred.await()
    }

    private fun isConsumable(record: PurchaseRecord) =
        billingProducts[record.productId]?.type == BillingProductType.CONSUMABLE

    private fun purchasedItem(record: PurchaseRecord) =
        PurchasedItem(record, detailProducts[record.productId]?.toBillingProductDetail())

    /**
     * Giao dịch phải được acknowledge trong 3 ngày, nếu không Google Play tự hoàn tiền. Sản phẩm tiêu hao thì
     * consume (consume cũng là acknowledge) để mua lại được, và chỉ báo [PurchaseUpdate.Succeeded] khi consume thành
     * công: Google chỉ cho consume một token một lần, nên app cộng vật phẩm đúng một lần - kể cả giao dịch mua dở
     * (app bị tắt trước khi consume) được tìm thấy ở lần query sau.
     */
    private fun consumeOrAcknowledge(record: PurchaseRecord) {
        if (isConsumable(record)) {
            billingClient.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(record.purchaseToken).build()) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    emit(PurchaseUpdate.Succeeded(listOf(purchasedItem(record))))
                } else {
                    Log.e(TAG, "consume failed: ${result.responseCode} ${result.debugMessage}")
                }
            }
        } else if (!record.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(record.purchaseToken).build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.e(TAG, "acknowledge failed: ${result.debugMessage}")
                }
            }
        }
    }

    /**
     * Mở luồng mua của Google Play cho [product]; kết quả về qua [purchaseUpdateListener].
     * @param useFreeTrial true: mua theo offer tốt nhất (ưu tiên dùng thử nếu user còn lượt); false: mua thẳng base plan.
     */
    fun purchase(activity: Activity, product: BillingProductDetail, useFreeTrial: Boolean = true) {
        val details = detailProducts[product.productId] ?: run { emit(PurchaseUpdate.Error); return }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        if (product.isSubscription()) {
            val offer = (if (useFreeTrial) product.bestSubscriptionOffer() else product.basePlanOffer())
                ?: product.bestSubscriptionOffer()
                ?: run {
                    Log.e(TAG, "no subscription offer for ${product.productId}")
                    emit(PurchaseUpdate.Error)
                    return
                }
            productParams.setOfferToken(offer.offerToken)
        }
        val flowParams = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.responseCode} ${result.debugMessage}")
            emit(PurchaseUpdate.Error)
        }
    }

    /**
     * Thông báo của Google Play khi gia hạn bị từ chối (grace period / account hold), cho user sửa thanh toán ngay
     * trong app. Trả về true nếu user đã sửa và gói được khôi phục.
     */
    suspend fun showInAppMessages(activity: Activity): Boolean {
        if (connect() != BillingConnectionResult.Connected) return false
        val params = InAppMessageParams.newBuilder()
            .addInAppMessageCategoryToShow(InAppMessageParams.InAppMessageCategoryId.TRANSACTIONAL)
            .build()
        val deferred = CompletableDeferred<Boolean>()
        val launch = billingClient.showInAppMessages(activity, params) { result ->
            deferred.complete(result.responseCode == InAppMessageResult.InAppMessageResponseCode.SUBSCRIPTION_STATUS_UPDATED)
        }
        // Gọi lỗi (không hỗ trợ, mất kết nối...) thì callback không bao giờ chạy.
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) return false
        return deferred.await()
    }

    private companion object {
        const val TAG = "GooglePlayBilling"
    }
}
