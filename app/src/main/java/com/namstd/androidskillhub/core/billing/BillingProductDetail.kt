package com.namstd.androidskillhub.core.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Một phase giá của offer thuê bao (vd. 3 ngày miễn phí, rồi 99.000 ₫ / tuần). */
data class PricingPhase(
    val billingCycleCount: Int,
    val priceAmountMicros: Long,
    /** ISO-8601: P3D, P1W, P1M, P1Y... */
    val billingPeriod: String,
    val formattedPrice: String,
    /** 1 = lặp vô hạn (giá gia hạn), 2 = lặp hữu hạn (giá giới thiệu), 3 = không lặp. */
    val recurrenceMode: Int,
    val priceCurrencyCode: String,
)

/** Một offer của gói thuê bao. `offerId == null` là base plan (giá gốc, không ưu đãi). */
data class SubscriptionOfferDetail(
    val offerId: String?,
    val offerToken: String,
    val offerTags: List<String>,
    val pricingPhases: List<PricingPhase>,
) {
    /** Phase giá gia hạn (lặp vô hạn) - giá thật của gói, bất kể Google trả các phase theo thứ tự nào. */
    fun recurringPhase(): PricingPhase? =
        pricingPhases.firstOrNull { it.recurrenceMode == BillingProductDetail.RECURRENCE_INFINITE } ?: pricingPhases.lastOrNull()
}

data class OneTimePurchaseOfferDetails(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
)

/** Thông tin một sản phẩm từ Google Play (giá, offer), tách khỏi class của thư viện Billing để dễ test và hiển thị. */
data class BillingProductDetail(
    val productId: String,
    val name: String,
    val productType: String,
    val description: String,
    val oneTimePurchaseOfferDetails: OneTimePurchaseOfferDetails?,
    val subscriptionOfferDetails: List<SubscriptionOfferDetail>?,
) {
    fun isSubscription(): Boolean = productType == BillingClient.ProductType.SUBS

    /** Giá gia hạn (thuê bao) hoặc giá mua một lần, đúng chuỗi Google Play trả về. */
    fun getFormattedPrice(): String =
        if (isSubscription()) basePlanOffer()?.recurringPhase()?.formattedPrice.orEmpty()
        else oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()

    /** Giá gia hạn tính bằng micros - bỏ qua phase dùng thử / giá giới thiệu. */
    fun getRecurringPriceAmountMicros(): Long =
        if (isSubscription()) basePlanOffer()?.recurringPhase()?.priceAmountMicros ?: 0
        else oneTimePurchaseOfferDetails?.priceAmountMicros ?: 0

    fun getRecurringPriceCurrencyCode(): String =
        if (isSubscription()) basePlanOffer()?.recurringPhase()?.priceCurrencyCode.orEmpty()
        else oneTimePurchaseOfferDetails?.priceCurrencyCode.orEmpty()

    /** Chu kỳ gia hạn ISO-8601 của gói thuê bao (P1W, P1Y...); null với sản phẩm mua một lần. */
    fun recurringPeriod(): String? = if (isSubscription()) basePlanOffer()?.recurringPhase()?.billingPeriod else null

    fun basePlanOffer(): SubscriptionOfferDetail? = subscriptionOfferDetails?.find { it.offerId == null }

    /**
     * Offer dùng thử miễn phí (có phase giá 0) mà user còn đủ điều kiện. Google Play chỉ trả offer user được dùng,
     * nên null = đã hết lượt dùng thử hoặc gói không cấu hình dùng thử.
     */
    fun freeTrialOffer(): SubscriptionOfferDetail? =
        subscriptionOfferDetails?.firstOrNull { offer -> offer.pricingPhases.any { it.priceAmountMicros == 0L } }

    fun hasFreeTrial(): Boolean = freeTrialOffer() != null

    /** Số ngày dùng thử, đọc từ chu kỳ (P3D, P1W...) của phase giá 0. */
    fun freeTrialDays(): Int {
        val period = freeTrialOffer()?.pricingPhases?.firstOrNull { it.priceAmountMicros == 0L }?.billingPeriod
            ?: return 0
        return isoPeriodToDays(period)
    }

    /**
     * Offer cho luồng mua mặc định, theo thứ tự ưu tiên: dùng thử miễn phí → giá giới thiệu (phase hữu hạn có tiền)
     * → base plan.
     */
    fun bestSubscriptionOffer(): SubscriptionOfferDetail? {
        val offers = subscriptionOfferDetails ?: return null
        return offers.firstOrNull { offer -> offer.pricingPhases.any { it.priceAmountMicros == 0L } }
            ?: offers.firstOrNull { offer ->
                offer.pricingPhases.any { it.recurrenceMode == RECURRENCE_FINITE && it.priceAmountMicros > 0 }
            }
            ?: basePlanOffer()
    }

    /**
     * Giá gia hạn chia cho [divisor], định dạng giống hệt chuỗi giá Google Play trả về (ký hiệu tiền, vị trí,
     * dấu phân cách). Vd. gói năm chia 52 để ra giá quy đổi theo tuần.
     */
    fun getRecurringPriceDividedBy(divisor: Double): String {
        val formattedPrice = getFormattedPrice()
        val micros = getRecurringPriceAmountMicros()
        val currencyCode = getRecurringPriceCurrencyCode()
        if (micros == 0L || divisor <= 0.0 || divisor == 1.0) return formattedPrice
        val amount = micros / 1_000_000.0 / divisor
        return formatLikeTemplate(formattedPrice, amount, currencyCode)
            ?: try {
                val currency = Currency.getInstance(currencyCode)
                NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
                    this.currency = currency
                    minimumFractionDigits = currency.defaultFractionDigits
                    maximumFractionDigits = currency.defaultFractionDigits
                }.format(amount)
            } catch (e: IllegalArgumentException) {
                String.format(Locale.US, "%.2f %s", amount, currencyCode)
            }
    }

    companion object {
        const val RECURRENCE_INFINITE = 1
        const val RECURRENCE_FINITE = 2

        fun ProductDetails.toBillingProductDetail() = BillingProductDetail(
            productId = productId,
            name = name,
            productType = productType,
            description = description,
            oneTimePurchaseOfferDetails = oneTimePurchaseOfferDetails?.let {
                OneTimePurchaseOfferDetails(it.formattedPrice, it.priceAmountMicros, it.priceCurrencyCode)
            },
            subscriptionOfferDetails = subscriptionOfferDetails?.map { offer ->
                SubscriptionOfferDetail(
                    offerId = offer.offerId,
                    offerToken = offer.offerToken,
                    offerTags = offer.offerTags,
                    pricingPhases = offer.pricingPhases.pricingPhaseList.map {
                        PricingPhase(
                            it.billingCycleCount, it.priceAmountMicros, it.billingPeriod,
                            it.formattedPrice, it.recurrenceMode, it.priceCurrencyCode,
                        )
                    },
                )
            },
        )

        /** Số ngày của một chu kỳ ISO-8601 (P1Y = 365, P1M = 30, P1W = 7); không đọc được thì 0. */
        fun isoPeriodToDays(period: String): Int {
            val match = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?""").matchEntire(period) ?: return 0
            val (y, m, w, d) = match.destructured
            return (y.toIntOrNull() ?: 0) * 365 + (m.toIntOrNull() ?: 0) * 30 +
                (w.toIntOrNull() ?: 0) * 7 + (d.toIntOrNull() ?: 0)
        }

        /**
         * Thay phần số trong [template] (vd. "131.000 ₫", "$4.99", "4,99 €") bằng [amount], giữ nguyên dấu phân cách
         * hàng nghìn / thập phân của template.
         */
        internal fun formatLikeTemplate(template: String, amount: Double, currencyCode: String): String? {
            val match = Regex("""\d[\d.,  ' ]*\d|\d""").find(template) ?: return null
            val number = match.value
            val fractionDigits = try {
                Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
            } catch (e: IllegalArgumentException) {
                2
            }
            val lastSeparator = number.indexOfLast { !it.isDigit() }
            val hasDecimal = fractionDigits > 0 && lastSeparator >= 0 &&
                number.length - lastSeparator - 1 == fractionDigits
            val integerPart = if (hasDecimal) number.substring(0, lastSeparator) else number
            val groupingSeparator = integerPart.firstOrNull { !it.isDigit() }
            val decimalSeparator = when {
                hasDecimal -> number[lastSeparator]
                groupingSeparator == '.' -> ','
                else -> '.'
            }
            val symbols = DecimalFormatSymbols(Locale.US).apply {
                this.decimalSeparator = decimalSeparator
                this.groupingSeparator = groupingSeparator ?: ','
            }
            val pattern = buildString {
                append(if (groupingSeparator != null) "#,##0" else "0")
                if (fractionDigits > 0) {
                    append('.')
                    repeat(fractionDigits) { append('0') }
                }
            }
            return template.replaceRange(match.range, DecimalFormat(pattern, symbols).format(amount))
        }
    }
}
