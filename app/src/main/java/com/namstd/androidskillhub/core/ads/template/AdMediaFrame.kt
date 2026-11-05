package com.namstd.androidskillhub.core.ads.template

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.namstd.androidskillhub.R

/**
 * Chứa `MediaView` của native ad theo tỉ lệ rộng:cao cố định (`app:adRatio`, vd. "16:9") với góc bo
 * tròn (`app:adCornerRadius`), giống atom "Ad/Media" trong Figma. Chiều cao theo chiều rộng được cấp;
 * không đặt ratio thì chỉ wrap view con như một [FrameLayout] thường. Chiều cao `match_parent` được cấp
 * kích thước chính xác thì sẽ lấp đầy luôn (template full-screen), tỉ lệ chỉ là fallback khi wrap_content.
 */
class AdMediaFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private var ratio = 0f
    private var cornerRadius = 0f

    init {
        context.obtainStyledAttributes(attrs, R.styleable.AdMediaFrame).apply {
            ratio = parseRatio(getString(R.styleable.AdMediaFrame_adRatio))
            cornerRadius = getDimension(R.styleable.AdMediaFrame_adCornerRadius, 0f)
            recycle()
        }
        if (cornerRadius > 0f) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
            clipToOutline = true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // match_parent với chiều cao cố định cần lấp đầy (layout full-screen) được ưu tiên; ratio là fallback
        // cho trường hợp cùng layout đó nằm trong container wrap_content.
        val fillsExactHeight = layoutParams?.height == LayoutParams.MATCH_PARENT &&
            MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
        if (ratio <= 0f || fillsExactHeight || MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val height = (width / ratio).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    private fun parseRatio(value: String?): Float {
        val parts = value?.split(":")?.mapNotNull { it.trim().toFloatOrNull() } ?: return 0f
        return if (parts.size == 2 && parts[1] > 0f) parts[0] / parts[1] else 0f
    }
}
