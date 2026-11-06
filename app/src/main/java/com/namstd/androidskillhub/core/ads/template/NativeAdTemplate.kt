package com.namstd.androidskillhub.core.ads.template

import androidx.annotation.LayoutRes
import com.namstd.androidskillhub.R
import com.namstd.androidskillhub.core.config.AdKey
import com.namstd.androidskillhub.core.config.PlacementConfig

/**
 * Bộ template card native dùng chung cho mọi placement, chọn bằng `template` của placement đó trong
 * `ad_placements`. [key] là đúng tên mẫu trong tài liệu / Figma "Wavez - ADs" (Big1…Big9, Small1…, Tiny1, Tiny2,
 * Collap1…, Full1…), không phân biệt hoa thường; màn nào cần mẫu nào thì ghi đúng tên đó.
 *
 * Mọi layout (layout/ad_tpl_*.xml) dùng chung view id, nên
 * [com.namstd.androidskillhub.core.ads.NativeAds] có thể bind bất kỳ cái nào:
 * `ad_headline` (bắt buộc), `ad_icon`, `ad_body`, `ad_call_to_action`, `ad_media`, `ad_stars`,
 * `ad_advertiser` (đều tùy chọn; không hiện giá - "FREE" - dù Figma có vẽ), cộng thêm các control ẩn `ad_close` / `ad_collapse` / `ad_next` mà chỉ
 * màn có luồng riêng (collapsible, full-screen break...) mới hiện và gắn xử lý.
 *
 * Các template Collap là card ở dạng expand; dải thu nhỏ tương ứng là [TINY1] (Collap1, Collap3) và
 * [TINY2] (Collap2).
 */
enum class NativeAdTemplate(val key: String, @LayoutRes val layout: Int) {
    BIG1("big1", R.layout.ad_tpl_big1),
    BIG2("big2", R.layout.ad_tpl_big2),
    BIG3("big3", R.layout.ad_tpl_big3),
    BIG4("big4", R.layout.ad_tpl_big4),
    BIG5("big5", R.layout.ad_tpl_big5),

    BIG6("big6", R.layout.ad_tpl_big6),
    BIG7("big7", R.layout.ad_tpl_big7),
    BIG8("big8", R.layout.ad_tpl_big8),
    BIG9("big9", R.layout.ad_tpl_big9),

    SMALL1("small1", R.layout.ad_tpl_small1),
    SMALL2("small2", R.layout.ad_tpl_small2),
    SMALL3("small3", R.layout.ad_tpl_small3),
    SMALL4("small4", R.layout.ad_tpl_small4),

    TINY1("tiny1", R.layout.ad_tpl_tiny1),
    TINY2("tiny2", R.layout.ad_tpl_tiny2),

    COLLAP1("collap1", R.layout.ad_tpl_collap1),
    COLLAP2("collap2", R.layout.ad_tpl_collap2),
    COLLAP3("collap3", R.layout.ad_tpl_collap3),

    FULL1("full1", R.layout.ad_tpl_full1),
    FULL2("full2", R.layout.ad_tpl_full2),
    FULL3("full3", R.layout.ad_tpl_full3),
    FULL4("full4", R.layout.ad_tpl_full4),
    FULL5("full5", R.layout.ad_tpl_full5),
    ;

    /** Dải thấp (Tiny1, Tiny2): lúc load dùng shimmer thấp như banner thay vì shimmer card. */
    val isStrip: Boolean get() = this == TINY1 || this == TINY2

    /**
     * Bộ native collapsible: mẫu Collap* là dạng lớn, bấm nút thu gọn / đóng thì thành dải này (đúng từng bộ trong
     * Figma: Collap1 → Tiny1, Collap2 → Tiny2, Collap3 → Tiny1). Mẫu không phải Collap* → null.
     */
    val collapsed: NativeAdTemplate?
        get() = when (this) {
            COLLAP1, COLLAP3 -> TINY1
            COLLAP2 -> TINY2
            else -> null
        }

    companion object {
        /**
         * Template mặc định khi `template` của placement bỏ trống / ghi sai, cho placement mà [BIG1] không vừa chỗ:
         * `native_language_small` là dải nằm dưới tiêu đề nên mặc định [TINY1] (đúng như sheet).
         */
        private val DEFAULT_BY_KEY: Map<AdKey, NativeAdTemplate> = mapOf(
            AdKey.NATIVE_LANGUAGE_SMALL to TINY1,
        )

        /** Template cho placement [key] theo `template` của nó; trống / sai thì mặc định của placement (thường [BIG1]). */
        fun forPlacement(key: AdKey, config: PlacementConfig): NativeAdTemplate =
            fromKey(config.template, DEFAULT_BY_KEY[key] ?: BIG1)

        /**
         * Mẫu + style cho placement [key]: `template` có thể là mẫu có sẵn hoặc tên mẫu tự đặt trong `ad_templates`
         * ([templates]); trống / không khớp thì mặc định của placement.
         */
        fun specFor(key: AdKey, config: PlacementConfig, templates: AdTemplatesDocument): NativeTemplateSpec =
            templates.resolve(config.template, DEFAULT_BY_KEY[key] ?: BIG1)

        /** Template ứng với một tên mẫu (không phân biệt hoa thường); không khớp hoặc rỗng thì về [fallback]. */
        fun fromKey(value: String?, fallback: NativeAdTemplate = BIG1): NativeAdTemplate {
            val key = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == key } ?: fallback
        }

        /** Toàn bộ key, dùng cho gợi ý ở Debug Config. */
        val keys: String get() = entries.joinToString(" | ") { it.key }
    }
}
