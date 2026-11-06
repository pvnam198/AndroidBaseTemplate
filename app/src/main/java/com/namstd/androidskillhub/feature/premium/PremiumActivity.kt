package com.namstd.androidskillhub.feature.premium

import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.radiobutton.MaterialRadioButton
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.billing.BillingProductDetail
import com.namstd.androidskillhub.core.billing.BillingProductType
import com.namstd.androidskillhub.core.billing.Premium
import com.namstd.androidskillhub.core.billing.PremiumProducts
import com.namstd.androidskillhub.core.billing.ProductsResult
import com.namstd.androidskillhub.core.billing.PurchaseUpdate
import com.namstd.androidskillhub.core.ui.base.BaseActivity
import com.namstd.androidskillhub.databinding.ActivityPremiumBinding
import kotlinx.coroutines.launch

/**
 * Màn mua Premium: liệt kê các gói trong [PremiumProducts] với giá Google Play trả về, mua gói đang chọn, khôi phục
 * giao dịch. Mua xong thì Premium bật và toàn bộ quảng cáo tắt (xem [Premium]).
 */
class PremiumActivity : BaseActivity<ActivityPremiumBinding>() {
    private var selectedProductId = PremiumProducts.DEFAULT_SELECTED

    override fun inflateBinding(inflater: LayoutInflater): ActivityPremiumBinding =
        ActivityPremiumBinding.inflate(inflater)

    override fun initViews() {
        binding.mockPremiumSwitch.isVisible = BuildConfig.DEBUG
        binding.mockPremiumSwitch.isChecked = prefs.mockPremium
        Premium.refresh()
        renderStatus()
    }

    override fun initListeners() {
        binding.upgradeButton.setOnClickListener {
            if (!Premium.purchase(this, selectedProductId)) toast(R.string.premium_unavailable)
        }
        binding.restoreButton.setOnClickListener { Premium.refresh() }
        binding.mockPremiumSwitch.setOnCheckedChangeListener { _, checked ->
            Premium.setMockPremium(checked)
            renderStatus()
        }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { Premium.products.collect(::renderProducts) }
                launch { Premium.purchaseUpdates.collect(::onPurchaseUpdate) }
            }
        }
    }

    private fun renderProducts(result: ProductsResult) {
        binding.productsLoading.isVisible = result is ProductsResult.Loading
        binding.productsUnavailable.isVisible = result is ProductsResult.Unavailable
        binding.productList.removeAllViews()
        if (result is ProductsResult.Success) {
            // Giữ đúng thứ tự khai báo trong PremiumProducts.
            val products = PremiumProducts.all.mapNotNull { p -> result.products.find { it.productId == p.productId } }
            if (products.none { it.productId == selectedProductId }) selectedProductId = products.first().productId
            products.forEach { addProductRow(it) }
        }
        renderStatus()
    }

    private fun addProductRow(product: BillingProductDetail) {
        val row = MaterialRadioButton(this).apply {
            id = View.generateViewId()
            text = productLabel(product)
            isChecked = product.productId == selectedProductId
            setOnClickListener {
                selectedProductId = product.productId
                renderStatus()
            }
        }
        binding.productList.addView(row)
    }

    /** "Yearly – 499.000 ₫ / năm · dùng thử 3 ngày". */
    private fun productLabel(product: BillingProductDetail): String {
        val period = when (product.recurringPeriod()) {
            null -> getString(
                if (PremiumProducts.typeOf(product.productId) == BillingProductType.CONSUMABLE) R.string.premium_period_consumable
                else R.string.premium_period_lifetime,
            )
            "P1W" -> getString(R.string.premium_period_week)
            "P1M" -> getString(R.string.premium_period_month)
            "P1Y" -> getString(R.string.premium_period_year)
            else -> product.recurringPeriod()
        }
        val trial = product.freeTrialDays().takeIf { it > 0 }?.let { getString(R.string.premium_trial_days, it) }
        return listOfNotNull("${product.name} – ${product.getFormattedPrice()} $period", trial).joinToString(" · ")
    }

    private fun onPurchaseUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Succeeded ->
                // Gói tiêu hao: app thật cộng vật phẩm (xu, lượt...) ở đây.
                if (update.purchases.any { it.record.productId in PremiumProducts.premiumIds }) toast(R.string.premium_purchased)
                else toast(R.string.premium_consumable_purchased)
            PurchaseUpdate.AlreadyOwned -> toast(R.string.premium_purchased)
            PurchaseUpdate.Error -> toast(R.string.premium_purchase_failed)
            PurchaseUpdate.UserCanceled -> Unit
        }
        renderStatus()
    }

    private fun renderStatus() {
        val premium = Premium.isPremium
        binding.premiumStatus.setText(if (premium) R.string.premium_active else R.string.premium_inactive)
        // Đã Premium thì chỉ còn mua được gói tiêu hao.
        val buyable = !premium || PremiumProducts.typeOf(selectedProductId) == BillingProductType.CONSUMABLE
        binding.upgradeButton.isEnabled = buyable && Premium.products.value is ProductsResult.Success
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
