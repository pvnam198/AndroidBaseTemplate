package com.namstd.androidskillhub.core.ads.template

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Khung chứa native ad có giới hạn chiều cao: [maxHeightPx] và chiều cao được cấp (AT_MOST / EXACTLY). Quảng cáo cao
 * hơn chỗ thì chỉ thu nhỏ phần media ([AdMediaFrame]) đúng bằng phần dư, không nhỏ hơn [MIN_MEDIA_DP] (mức AdMob yêu
 * cầu) - nút CTA, tiêu đề, icon giữ nguyên, không bị cắt. Media đã nhỏ nhất mà vẫn dư thì khung cắt phần đáy.
 */
class AdFitFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** Chiều cao tối đa (px); 0 = chỉ theo chiều cao được cấp. */
    var maxHeightPx = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE
        else MeasureSpec.getSize(heightMeasureSpec)
        if (maxHeightPx > 0) limit = minOf(limit, maxHeightPx)

        // Đo chiều cao tự nhiên (không giới hạn) của quảng cáo.
        val media = findMedia(this)
        media?.heightCap = 0
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        super.onMeasure(widthMeasureSpec, free)

        val overflow = measuredHeight - limit
        if (overflow > 0 && media != null) {
            val minMedia = (MIN_MEDIA_DP * resources.displayMetrics.density).toInt()
            media.heightCap = maxOf(minMedia, media.measuredHeight - overflow)
            super.onMeasure(widthMeasureSpec, free)
        }
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            else -> minOf(measuredHeight, limit)
        }
        setMeasuredDimension(measuredWidth, height)
    }

    private fun findMedia(view: View): AdMediaFrame? = when (view) {
        is AdMediaFrame -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findMedia(view.getChildAt(it)) }
        else -> null
    }

    private companion object {
        const val MIN_MEDIA_DP = 120
    }
}
