package com.namstd.androidskillhub.core.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingLogicTest {
    private fun phase(micros: Long, period: String, mode: Int, cycles: Int = 0, price: String = "") =
        PricingPhase(cycles, micros, period, price, mode, "VND")

    private val basePlan = SubscriptionOfferDetail(
        offerId = null, offerToken = "base", offerTags = emptyList(),
        pricingPhases = listOf(phase(499_000_000_000, "P1Y", 1, price = "499.000 ₫")),
    )
    private val trial = SubscriptionOfferDetail(
        offerId = "trial3d", offerToken = "trial", offerTags = emptyList(),
        pricingPhases = listOf(phase(0, "P3D", 2, cycles = 1), phase(499_000_000_000, "P1Y", 1, price = "499.000 ₫")),
    )
    private val intro = SubscriptionOfferDetail(
        offerId = "intro", offerToken = "intro", offerTags = emptyList(),
        pricingPhases = listOf(phase(99_000_000_000, "P1Y", 2, cycles = 1), phase(499_000_000_000, "P1Y", 1)),
    )

    private fun yearly(vararg offers: SubscriptionOfferDetail) =
        BillingProductDetail("premium_yearly", "Yearly", "subs", "", null, offers.toList())

    @Test
    fun bestOfferPrefersTrialThenIntroThenBasePlan() {
        assertEquals("trial", yearly(basePlan, intro, trial).bestSubscriptionOffer()?.offerToken)
        assertEquals("intro", yearly(basePlan, intro).bestSubscriptionOffer()?.offerToken)
        assertEquals("base", yearly(basePlan).bestSubscriptionOffer()?.offerToken)
    }

    @Test
    fun priceAndTrialComeFromTheRightPhase() {
        val product = yearly(basePlan, trial)
        assertEquals("499.000 ₫", product.getFormattedPrice())
        assertEquals("P1Y", product.recurringPeriod())
        assertEquals(3, product.freeTrialDays())
        assertEquals(0, yearly(basePlan).freeTrialDays())
    }

    @Test
    fun oneTimeProductHasNoPeriod() {
        val lifetime = BillingProductDetail(
            "premium_lifetime", "Lifetime", "inapp", "",
            OneTimePurchaseOfferDetails("$9.99", 9_990_000, "USD"), null,
        )
        assertFalse(lifetime.isSubscription())
        assertNull(lifetime.recurringPeriod())
        assertEquals("$9.99", lifetime.getFormattedPrice())
    }

    @Test
    fun dividedPriceKeepsTheStoreFormat() {
        assertEquals("$0.96", BillingProductDetail.formatLikeTemplate("$49.99", 49.99 / 52, "USD"))
        assertEquals("9.596 ₫", BillingProductDetail.formatLikeTemplate("499.000 ₫", 499_000.0 / 52, "VND"))
        assertEquals("0,96 €", BillingProductDetail.formatLikeTemplate("49,99 €", 49.99 / 52, "EUR"))
    }

    @Test
    fun isoPeriods() {
        assertEquals(3, BillingProductDetail.isoPeriodToDays("P3D"))
        assertEquals(7, BillingProductDetail.isoPeriodToDays("P1W"))
        assertEquals(365, BillingProductDetail.isoPeriodToDays("P1Y"))
        assertEquals(0, BillingProductDetail.isoPeriodToDays("garbage"))
    }

    @Test
    fun premiumNeedsAPaidPurchaseOfAPremiumProduct() {
        val ids = setOf("premium_yearly")
        val paid = PurchaseRecord("premium_yearly", "t1", isPurchased = true)
        val pending = PurchaseRecord("premium_yearly", "t2", isPurchased = false)
        val other = PurchaseRecord("coins_100", "t3", isPurchased = true)
        assertEquals(true, ownsPremium(listOf(paid), isQueryOk = true, premiumIds = ids))
        assertEquals(false, ownsPremium(listOf(pending, other), isQueryOk = true, premiumIds = ids))
        assertEquals(false, ownsPremium(emptyList(), isQueryOk = true, premiumIds = ids))
        // Query lỗi: không kết luận, giữ nguyên Premium cũ.
        assertNull(ownsPremium(emptyList(), isQueryOk = false, premiumIds = ids))
        assertTrue(PremiumProducts.all.any { it.productId == PremiumProducts.DEFAULT_SELECTED })
    }

    @Test
    fun sampleProductsHaveOneOfEachTypeAndOnlyNonConsumablesGivePremium() {
        assertEquals(
            setOf(BillingProductType.SUBS, BillingProductType.NON_CONSUMABLE, BillingProductType.CONSUMABLE),
            PremiumProducts.all.map { it.type }.toSet(),
        )
        assertEquals(setOf(PremiumProducts.YEARLY, PremiumProducts.LIFETIME), PremiumProducts.premiumIds)
        val coins = PurchaseRecord(PremiumProducts.COIN_PACK, "t", isPurchased = true)
        val lifetime = PurchaseRecord(PremiumProducts.LIFETIME, "t2", isPurchased = true)
        assertEquals(false, ownsPremium(listOf(coins), isQueryOk = true))
        assertEquals(true, ownsPremium(listOf(coins, lifetime), isQueryOk = true))
    }
}
