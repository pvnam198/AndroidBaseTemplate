package com.namstd.androidskillhub.core.ui.base

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.ads.AdPolicy
import com.namstd.androidskillhub.core.ads.NativeAds
import com.namstd.androidskillhub.core.ads.NativePlacement
import com.namstd.androidskillhub.core.ads.template.NativeStyleApplier
import com.namstd.androidskillhub.databinding.AdNativeBinding
import com.namstd.androidskillhub.databinding.AdNativeShimmerBinding

sealed interface AdFeedItem<out T> {
    data class Content<T>(val value: T) : AdFeedItem<T>
    data object AdSlot : AdFeedItem<Nothing>
}

private const val POLL_INTERVAL_MS = 400L

/**
 * RecyclerView adapter chèn xen native ad vào một danh sách nội dung. [items] do chính màn hình
 * dựng - đặt [AdFeedItem.AdSlot] ở đâu và cách nhau bao xa hoàn toàn do bên gọi quyết định,
 * base này chỉ lo phần native ad của từng slot.
 *
 * Một slot không bao giờ tự gửi request quảng cáo riêng: nó liên tục [NativeAds.poll] slot [placement]
 * dùng chung trong lúc đang hiện trên màn hình, nên slot nào tình cờ đang hiện khi ad kế tiếp load
 * xong thì slot đó giành được ad - slot bị cuộn qua trước lúc đó sẽ dừng poll
 * ([onViewRecycled]) và đơn giản là không nhận ad nào, thay vì lãng phí một request riêng
 * cho nó. Khi một vị trí đã giành được ad thì giữ luôn ad đó và không bao giờ poll lại cho vị trí ấy.
 *
 * Slot không có ad nào sắp tới ([NativeAds.isUnavailable]: tắt quảng cáo, SDK chưa sẵn sàng, hoặc load lỗi)
 * sẽ thu gọn về không thay vì shimmer mãi; mỗi slot mới được bind sẽ thử load lại một lần.
 * [hideAds] (vd. user vừa lên premium) bỏ mọi ad và thu gọn mọi slot vĩnh viễn.
 *
 * Lớp con chỉ cần cung cấp cách tạo/bind ViewHolder riêng của item nội dung.
 */
abstract class BaseAdRecyclerAdapter<T>(
    private val activity: Activity,
    items: List<AdFeedItem<T>>,
    private val placement: NativePlacement,
) : BaseRecyclerAdapter<AdFeedItem<T>, RecyclerView.ViewHolder>(items) {

    private val loadedAds = mutableMapOf<Int, NativeAd>()
    private var adsHidden = false

    protected abstract fun onCreateContentViewHolder(parent: ViewGroup): RecyclerView.ViewHolder
    protected abstract fun onBindContent(holder: RecyclerView.ViewHolder, value: T, position: Int)

    final override fun getItemViewType(position: Int) = when (items[position]) {
        is AdFeedItem.Content -> TYPE_CONTENT
        is AdFeedItem.AdSlot -> TYPE_NATIVE_AD
    }

    final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_NATIVE_AD) {
            val container = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_native_ad_slot, parent, false) as FrameLayout
            NativeAdViewHolder(activity, container)
        } else {
            onCreateContentViewHolder(parent)
        }

    final override fun bind(holder: RecyclerView.ViewHolder, item: AdFeedItem<T>, position: Int) {
        when (item) {
            is AdFeedItem.Content -> onBindContent(holder, item.value, position)
            is AdFeedItem.AdSlot -> bindAdSlot(holder as NativeAdViewHolder, position)
        }
    }

    private fun bindAdSlot(holder: NativeAdViewHolder, position: Int) {
        // Placement tắt (vd. native_home_feed enable false) thì ô quảng cáo ẩn như khi tắt quảng cáo.
        if (adsHidden || !AdPolicy.nativeEnabled(placement)) {
            holder.hide()
            return
        }
        val cached = loadedAds[position]
        if (cached != null) {
            holder.show(cached)
            return
        }
        // Thử lại một lần cho mỗi slot mới bind nếu lần load trước lỗi; không làm gì khi đang load hoặc đã có cache.
        NativeAds.preload(placement)
        holder.showShimmer()
        holder.startPolling(placement) { ad -> loadedAds[position] = ad }
    }

    final override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? NativeAdViewHolder)?.cancelPolling()
    }

    /** Huỷ mọi ad mà list này từng giành được và xoá cache - gọi khi màn hình bị huỷ. */
    fun release() {
        loadedAds.values.forEach(NativeAd::destroy)
        loadedAds.clear()
    }

    /** Bỏ mọi ad và thu gọn mọi slot quảng cáo trong suốt phần đời còn lại của list - vd. user đã lên premium. */
    fun hideAds() {
        if (adsHidden) return
        adsHidden = true
        release()
        notifyItemRangeChanged(0, itemCount)
    }

    private class NativeAdViewHolder(
        private val activity: Activity,
        private val container: FrameLayout,
    ) : RecyclerView.ViewHolder(container) {

        // Kích thước của slot khi inflate từ item_native_ad_slot, được khôi phục mỗi khi slot hiện lại.
        private val slotMinHeight = container.minimumHeight
        private val slotParams = container.layoutParams as RecyclerView.LayoutParams
        private val slotHeight = slotParams.height
        private val slotMarginTop = slotParams.topMargin
        private val slotMarginBottom = slotParams.bottomMargin

        private val pollRunnable = Runnable { poll() }
        private var placement: NativePlacement? = null
        private var onWon: ((NativeAd) -> Unit)? = null

        fun startPolling(placement: NativePlacement, onWon: (NativeAd) -> Unit) {
            this.placement = placement
            this.onWon = onWon
            poll()
        }

        private fun poll() {
            val placement = placement ?: return
            // Kiểm tra trước khi poll: bản thân poll() sẽ kích hoạt một lần load mới và che mất lỗi.
            if (NativeAds.isUnavailable(placement)) {
                hide()
                return
            }
            val ad = NativeAds.poll(placement)
            if (ad != null) {
                onWon?.invoke(ad)
                onWon = null
                show(ad)
            } else {
                container.postDelayed(pollRunnable, POLL_INTERVAL_MS)
            }
        }

        fun cancelPolling() {
            container.removeCallbacks(pollRunnable)
            onWon = null
        }

        fun show(ad: NativeAd) {
            val binding = AdNativeBinding.inflate(LayoutInflater.from(activity))
            populate(ad, binding)
            placement?.let { NativeStyleApplier.apply(binding.root, NativeAds.templateFor(it).style) }
            container.removeAllViews()
            container.addView(binding.root)
            setCollapsed(false)
        }

        /** Không có ad cho slot này - thu gọn nó về không. */
        fun hide() {
            cancelPolling()
            container.removeAllViews()
            setCollapsed(true)
        }

        /**
         * RecyclerView layout item theo kích thước đã measure bất kể visibility, nên chỉ GONE thôi
         * vẫn để lại minHeight và margin của slot thành một khoảng trống. Thu gọn sẽ đưa cả ba về 0;
         * bỏ thu gọn thì trả lại các giá trị từ layout (holder được tái sử dụng giữa các slot).
         */
        private fun setCollapsed(collapsed: Boolean) {
            container.visibility = if (collapsed) View.GONE else View.VISIBLE
            container.minimumHeight = if (collapsed) 0 else slotMinHeight
            val params = container.layoutParams as RecyclerView.LayoutParams
            params.height = if (collapsed) 0 else slotHeight
            params.topMargin = if (collapsed) 0 else slotMarginTop
            params.bottomMargin = if (collapsed) 0 else slotMarginBottom
            container.layoutParams = params
        }

        private fun populate(ad: NativeAd, binding: AdNativeBinding) {
            val view = binding.root
            view.headlineView = binding.adHeadline
            view.bodyView = binding.adBody
            view.advertiserView = binding.adAdvertiser
            view.iconView = binding.adIcon
            view.callToActionView = binding.adCallToAction
            binding.adHeadline.text = ad.headline
            binding.adBody.text = ad.body
            binding.adAdvertiser.text = ad.advertiser
            binding.adCallToAction.text = ad.callToAction
            binding.adIcon.setImageDrawable(ad.icon?.drawable)
            binding.adBody.visibility = visibility(ad.body)
            binding.adAdvertiser.visibility = visibility(ad.advertiser)
            binding.adCallToAction.visibility = visibility(ad.callToAction)
            binding.adIcon.visibility = visibility(ad.icon)
            view.registerNativeAd(ad, binding.adMedia)
        }

        private fun visibility(value: Any?) = if (value == null) View.GONE else View.VISIBLE

        fun showShimmer() {
            val shimmer = AdNativeShimmerBinding.inflate(LayoutInflater.from(activity))
            container.removeAllViews()
            container.addView(shimmer.root)
            setCollapsed(false)
            shimmer.root.startShimmer()
        }
    }

    private companion object {
        const val TYPE_CONTENT = 0
        const val TYPE_NATIVE_AD = 1
    }
}
