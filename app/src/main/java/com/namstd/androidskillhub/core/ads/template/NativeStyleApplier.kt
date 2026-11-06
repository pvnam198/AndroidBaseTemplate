package com.namstd.androidskillhub.core.ads.template

import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.namstd.androidskillhub.R

/**
 * Áp [NativeStyle] lên một view native đã vẽ (mọi mẫu dùng chung view id). Đổi màu / bo góc / viền / cỡ chữ / kích
 * thước trong giới hạn của [NativeStyle]; không ẩn hay đổi vị trí view nào, nên nhãn "Ad", ảnh (≥ 120dp) và nút vẫn
 * đúng như mẫu gốc.
 */
object NativeStyleApplier {
    fun apply(root: View, style: NativeStyle) {
        if (style.isEmpty) return
        val density = root.resources.displayMetrics.density
        val context = root.context

        root.findViewById<View>(R.id.ad_call_to_action)?.let { cta ->
            if (style.ctaBg != null || style.ctaCornerDp != null) {
                val shape = GradientDrawable().apply {
                    setColor(style.ctaBg ?: ContextCompat.getColor(context, R.color.ad_cta))
                    cornerRadius = (style.ctaCornerDp ?: DEFAULT_CTA_CORNER_DP) * density
                }
                cta.background = RippleDrawable(ColorStateList.valueOf(RIPPLE), shape, null)
            }
            style.ctaText?.let { (cta as? TextView)?.setTextColor(it) }
        }

        if (style.cardBg != null || style.cardCornerDp != null || style.cardStroke != null || style.cardStrokeDp != null) {
            // Mẫu toàn màn hình (Full*) có nền màu phẳng không bo góc; mẫu card có nền bo góc.
            val flat = root.background is ColorDrawable
            root.background = GradientDrawable().apply {
                setColor(style.cardBg ?: ContextCompat.getColor(context, R.color.ad_card_bg))
                cornerRadius = (style.cardCornerDp ?: if (flat) 0f else root.resources.getDimension(R.dimen.ad_card_radius) / density) * density
                val strokeDp = style.cardStrokeDp ?: if (style.cardStroke != null) 1f else 0f
                if (strokeDp > 0f) setStroke((strokeDp * density).toInt().coerceAtLeast(1), style.cardStroke ?: 0x22000000)
            }
        }

        style.ctaHeightDp?.let { h -> root.findViewById<View>(R.id.ad_call_to_action)?.let { setHeight(it, h * density) } }
        style.ctaTextSp?.let { root.findViewById<TextView>(R.id.ad_call_to_action)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, it) }
        style.titleTextSp?.let { root.findViewById<TextView>(R.id.ad_headline)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, it) }
        style.bodyTextSp?.let { sp ->
            root.findViewById<TextView>(R.id.ad_body)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        }
        style.iconSizeDp?.let { size ->
            root.findViewById<View>(R.id.ad_icon)?.let { icon ->
                icon.layoutParams = icon.layoutParams?.apply { width = (size * density).toInt(); height = (size * density).toInt() }
            }
        }
        style.mediaHeightDp?.let { h ->
            val media = root.findViewById<View>(R.id.ad_media)
            val frame = media?.parent as? AdMediaFrame
            when {
                // Mẫu full-screen: ảnh lấp phần còn lại, không đổi.
                frame?.layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT -> Unit
                frame != null -> frame.fixedHeight = (h * density).toInt()
                media != null && (media.layoutParams?.height ?: 0) > 0 -> setHeight(media, h * density)
            }
        }
        style.cardPaddingDp?.let { p -> val px = (p * density).toInt(); root.setPadding(px, px, px, px) }

        style.titleColor?.let { root.findViewById<TextView>(R.id.ad_headline)?.setTextColor(it) }
        style.bodyColor?.let { color ->
            root.findViewById<TextView>(R.id.ad_body)?.setTextColor(color)
            root.findViewById<TextView>(R.id.ad_advertiser)?.setTextColor(color)
        }
    }

    private fun setHeight(view: View, px: Float) {
        view.layoutParams = view.layoutParams?.apply { height = px.toInt() }
    }

    private const val DEFAULT_CTA_CORNER_DP = 100f
    private const val RIPPLE = 0x33FFFFFF
}
