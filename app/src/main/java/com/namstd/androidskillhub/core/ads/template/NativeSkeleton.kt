package com.namstd.androidskillhub.core.ads.template

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.namstd.androidskillhub.R

/**
 * Shimmer giữ chỗ lúc native đang load, dựng từ chính layout sẽ hiện (mẫu của placement + style) nên có đúng kích thước
 * và bố cục của ad thật: hiện ad xong không bị nhảy từ khung to sang khung nhỏ (hay ngược lại).
 * Chữ / icon / media / nút thành khối xám; nền card, khoảng cách, chiều cao media giữ nguyên.
 */
object NativeSkeleton {
    private const val PLACEHOLDER = "                              "

    fun create(context: Context, @LayoutRes layout: Int, style: NativeStyle = NativeStyle.EMPTY): ShimmerFrameLayout {
        val content = LayoutInflater.from(context).inflate(layout, null)
        NativeStyleApplier.apply(content, style)
        AdChoicesCorner.reserve(content)
        blockOut(content)
        return ShimmerFrameLayout(context).apply { addView(content) }
    }

    fun create(context: Context, spec: NativeTemplateSpec): ShimmerFrameLayout = create(context, spec.template.layout, spec.style)

    private fun blockOut(view: View) {
        if (view.visibility == View.GONE) return
        val block = ContextCompat.getDrawable(view.context, R.drawable.shimmer_block)
        when (view) {
            is MediaView -> view.background = block
            is RatingBar -> view.visibility = View.INVISIBLE
            is TextView -> {
                // Chữ trong suốt nhưng vẫn chiếm chỗ (ô trống thì thêm chữ giữ chỗ để có bề rộng như khi có nội dung).
                if (view.text.isNullOrEmpty()) view.text = PLACEHOLDER
                view.setTextColor(Color.TRANSPARENT)
                view.background = block
            }
            is ImageView -> {
                view.setImageDrawable(null)
                view.background = block
            }
            is ViewGroup -> for (i in 0 until view.childCount) blockOut(view.getChildAt(i))
        }
    }
}
