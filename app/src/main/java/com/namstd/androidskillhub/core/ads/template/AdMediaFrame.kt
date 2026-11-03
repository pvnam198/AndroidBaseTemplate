package com.namstd.androidskillhub.core.ads.template

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.namstd.androidskillhub.R

/**
 * Holds a native ad's `MediaView` at a fixed width:height ratio (`app:adRatio`, e.g. "16:9") with
 * rounded corners (`app:adCornerRadius`), as the Figma "Ad/Media" atom. Height follows the width it
 * is given; with no ratio set it just wraps its child like a plain [FrameLayout]. A `match_parent` height
 * given an exact size fills it instead (full-screen templates), keeping the ratio as the wrap_content fallback.
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
        // match_parent with a fixed height to fill (full-screen layouts) wins; the ratio is the fallback
        // for when the same layout sits in a wrap_content container.
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
