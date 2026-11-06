package com.namstd.androidskillhub.core.billing

import android.app.Activity
import android.content.Context
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.core.ads.Ads
import com.namstd.androidskillhub.core.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Mua bán Premium cho toàn app: giữ danh sách gói ([products]) và kết quả mua ([purchaseUpdates]), và đồng bộ
 * `AppPreferences.isPremium` + [Ads.adsEnabled] theo giao dịch thật trên Google Play:
 * - mỗi lần [refresh] (mở app, mở màn Premium, bấm Khôi phục): có giao dịch đã thanh toán của một gói trong
 *   [PremiumProducts.premiumIds] thì Premium, không có thì mất Premium (gói hết hạn / bị hoàn tiền). Query giao dịch
 *   lỗi thì giữ nguyên trạng thái cũ, không tước Premium oan;
 * - mua xong một gói Premium thì bật Premium ngay. Gói tiêu hao (xu...) không đụng tới Premium: app nhận nó qua
 *   [purchaseUpdates] để cộng vật phẩm;
 * - Google báo "đã sở hữu" thì query lại để biết chắc.
 *
 * Gọi [initialize] một lần trong `Application.onCreate`.
 */
object Premium {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appContext: Context
    private lateinit var billing: GooglePlayBilling
    private var fetchJob: Job? = null

    /** Có gói Premium thật hay không (lần đồng bộ gần nhất), tách khỏi Premium giả của bản debug. */
    private var ownsPurchase = false

    private val _products = MutableStateFlow<ProductsResult>(ProductsResult.Loading)
    val products: StateFlow<ProductsResult> = _products.asStateFlow()

    private val _purchaseUpdates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 4)
    val purchaseUpdates: SharedFlow<PurchaseUpdate> = _purchaseUpdates.asSharedFlow()

    private val prefs get() = AppPreferences.getInstance(appContext)

    val isPremium: Boolean get() = prefs.isPremium

    fun initialize(context: Context) {
        if (::billing.isInitialized) return
        appContext = context.applicationContext
        ownsPurchase = prefs.isPremium && !(BuildConfig.DEBUG && prefs.mockPremium)
        val tracker = BuildConfig.ADJUST_IAP_EVENT_TOKEN.takeIf { it.isNotBlank() }?.let { AdjustIapTracker(appContext, it) }
        billing = GooglePlayBilling(appContext, tracker)
        billing.purchaseUpdateListener = { update ->
            scope.launch {
                when (update) {
                    is PurchaseUpdate.Succeeded ->
                        if (update.purchases.any { it.record.productId in PremiumProducts.premiumIds }) setOwned(true)
                    PurchaseUpdate.AlreadyOwned -> refresh()
                    else -> Unit
                }
                _purchaseUpdates.emit(update)
            }
        }
        refresh()
    }

    /** Query lại gói và giao dịch trên Google Play (cũng là "Khôi phục giao dịch"). */
    fun refresh() {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _products.value = ProductsResult.Loading
            val result = if (billing.connect() != BillingConnectionResult.Connected) {
                ProductsResult.Unavailable
            } else {
                val query = billing.queryProductDetailsAndPurchases(PremiumProducts.all)
                ownsPremium(query.purchaseRecords, query.isPurchasesQueryOk)?.let(::setOwned)
                if (query.productDetails.isEmpty()) ProductsResult.Unavailable
                else ProductsResult.Success(query.productDetails, query.purchaseRecords, query.isPurchasesQueryOk)
            }
            _products.value = result
        }
    }

    /**
     * Mở luồng mua [productId]; trả về false nếu gói chưa tải được. Kết quả về qua [purchaseUpdates].
     * @param useFreeTrial true = dùng offer dùng thử nếu user còn lượt.
     */
    fun purchase(activity: Activity, productId: String, useFreeTrial: Boolean = true): Boolean {
        val product = (products.value as? ProductsResult.Success)?.products?.find { it.productId == productId }
            ?: return false
        billing.purchase(activity, product, useFreeTrial)
        return true
    }

    /**
     * Cho Google Play hiện thông báo khi gia hạn bị từ chối (chỉ hiện khi thật sự có lỗi thanh toán); user sửa xong
     * thì query lại để Premium được khôi phục. Gọi ở màn chính, mỗi phiên một lần.
     */
    fun showPaymentIssueMessage(activity: Activity) {
        scope.launch { if (billing.showInAppMessages(activity)) refresh() }
    }

    /** Chỉ bản debug: bật / tắt Premium giả (không cần mua) để test luồng không quảng cáo. */
    fun setMockPremium(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.mockPremium = enabled
        setOwned(ownsPurchase)
    }

    private fun setOwned(owned: Boolean) {
        ownsPurchase = owned
        val premium = owned || (BuildConfig.DEBUG && prefs.mockPremium)
        prefs.isPremium = premium
        Ads.adsEnabled = !premium
    }
}

/**
 * User có Premium theo các giao dịch [records] hay không: có giao dịch đã thanh toán (không tính đang chờ) của một
 * gói trong [premiumIds]. null = query giao dịch lỗi, không kết luận được.
 */
internal fun ownsPremium(
    records: List<PurchaseRecord>,
    isQueryOk: Boolean,
    premiumIds: Set<String> = PremiumProducts.premiumIds,
): Boolean? {
    if (!isQueryOk) return null
    return records.any { it.isPurchased && it.productId in premiumIds }
}
