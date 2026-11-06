package com.namstd.androidskillhub.core.billing

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustEvent
import java.util.Calendar
import java.util.TimeZone

/**
 * Gửi doanh thu mua trong app lên Adjust (event [eventToken]), để tính hiệu quả chi phí quảng cáo.
 *
 * Hai đường gửi:
 * 1. [trackPurchase] - ngay sau khi mua xong: chỉ kỳ thanh toán đầu, bỏ qua nếu giá 0 (dùng thử).
 * 2. [trackPurchases] - mỗi lần mở app (query giao dịch): kỳ gia hạn user đang ở (bắt đầu ≤ bây giờ < kết thúc), bỏ
 *    qua kỳ đầu (đường 1 đã gửi). Không gửi bù kỳ đã qua: user không mở app suốt kỳ 2 mà mở ở kỳ 3 thì chỉ có kỳ 3.
 *
 * Mỗi kỳ chỉ gửi một lần (đánh dấu theo token + thời điểm bắt đầu kỳ, lưu trong SharedPreferences riêng).
 */
class AdjustIapTracker(context: Context, private val eventToken: String) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun trackPurchase(purchase: PurchaseRecord, detail: BillingProductDetail?) {
        detail ?: return
        val (price, currency) = firstPurchasePrice(detail)
        if (price <= 0.0) return
        val key = cycleKey(purchase.purchaseToken, purchase.purchaseTime)
        if (isLogged(key)) return
        send(price, currency, purchase, detail.productType, key)
        markLogged(key)
    }

    fun trackPurchases(purchases: List<PurchaseRecord>, productDetails: Map<String, BillingProductDetail>) {
        val now = System.currentTimeMillis()
        purchases.filter { it.isPurchased }.forEach { purchase ->
            val detail = productDetails[purchase.productId] ?: return@forEach
            if (!detail.isSubscription()) return@forEach // mua một lần: không có kỳ gia hạn
            val offer = detail.bestSubscriptionOffer() ?: return@forEach
            val cycle = currentRenewalCycle(purchase.purchaseTime, offer.pricingPhases, now) ?: return@forEach
            if (cycle.price <= 0.0) return@forEach
            val key = cycleKey(purchase.purchaseToken, cycle.start)
            if (isLogged(key)) return@forEach
            send(cycle.price, cycle.currency, purchase, detail.productType, key)
            markLogged(key)
        }
    }

    private data class Cycle(val start: Long, val price: Double, val currency: String)

    /** Kỳ thanh toán chứa [now], đi theo các phase giá kể từ [purchaseTime]; null nếu vẫn ở kỳ đầu hoặc gói đã hết. */
    private fun currentRenewalCycle(purchaseTime: Long, phases: List<PricingPhase>, now: Long): Cycle? {
        var start = purchaseTime
        var first = true
        for (phase in phases) {
            val period = parsePeriod(phase.billingPeriod) ?: break
            val maxCycles = if (phase.recurrenceMode == BillingProductDetail.RECURRENCE_INFINITE) Int.MAX_VALUE
            else phase.billingCycleCount
            for (i in 0 until maxCycles) {
                val end = addPeriod(start, period)
                if (first) {
                    first = false
                    start = end
                    continue
                }
                if (start > now) return null
                if (now < end) return Cycle(start, phase.priceAmountMicros / 1_000_000.0, phase.priceCurrencyCode)
                start = end
            }
        }
        return null
    }

    private fun firstPurchasePrice(detail: BillingProductDetail): Pair<Double, String> =
        if (detail.isSubscription()) {
            val phase = detail.bestSubscriptionOffer()?.pricingPhases?.firstOrNull() ?: return 0.0 to "USD"
            phase.priceAmountMicros / 1_000_000.0 to phase.priceCurrencyCode
        } else {
            val offer = detail.oneTimePurchaseOfferDetails
            (offer?.priceAmountMicros ?: 0L) / 1_000_000.0 to (offer?.priceCurrencyCode ?: "USD")
        }

    private fun send(price: Double, currency: String, purchase: PurchaseRecord, productType: String, dedupId: String) {
        val event = AdjustEvent(eventToken).apply {
            setRevenue(price, currency)
            setProductId(purchase.productId)
            setPurchaseToken(purchase.purchaseToken)
            setOrderId(purchase.orderId)
            setDeduplicationId(dedupId)
            addCallbackParameter("product_type", productType)
        }
        Adjust.verifyAndTrackPlayStorePurchase(event) { result ->
            Log.d(TAG, "verify: ${result.verificationStatus} ${result.code} ${result.message}")
        }
    }

    private fun isLogged(key: String) = prefs.getBoolean(key, false)

    private fun markLogged(key: String) = prefs.edit { putBoolean(key, true) }

    /** Chu kỳ ISO-8601 → (năm, tháng, ngày); tuần đổi ra 7 ngày. */
    private fun parsePeriod(period: String): Triple<Int, Int, Int>? {
        val match = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""").matchEntire(period) ?: return null
        val (y, m, w, d) = match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
        return Triple(y, m, w * 7 + d)
    }

    private fun addPeriod(epochMs: Long, period: Triple<Int, Int, Int>): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMs
            add(Calendar.YEAR, period.first)
            add(Calendar.MONTH, period.second)
            add(Calendar.DAY_OF_MONTH, period.third)
        }.timeInMillis

    private fun cycleKey(token: String, startMs: Long) = "cycle_${token}_$startMs"

    private companion object {
        const val TAG = "AdjustIapTracker"
        const val PREFS_NAME = "adjust_iap_tracker"
    }
}
