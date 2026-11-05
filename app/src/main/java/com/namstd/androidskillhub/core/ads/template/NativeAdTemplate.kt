package com.namstd.androidskillhub.core.ads.template

import androidx.annotation.LayoutRes
import com.namstd.androidskillhub.R

/**
 * Các layout card native ad, chọn bằng key Remote Config `native_template` ([key] là giá trị cần đặt).
 *
 * Mọi layout đều là template Figma "Wavez - ADs" / "Mẫu v2" (layout/ad_tpl_*.xml); bốn cái đầu là các
 * giá trị key ban đầu, nay được vẽ bằng template Figma gần nhất. Mọi layout dùng chung view id, nên
 * [com.namstd.androidskillhub.core.ads.NativeAds] có thể bind bất kỳ cái nào:
 * `ad_headline` (bắt buộc), `ad_icon`, `ad_body`, `ad_call_to_action`, `ad_media`, `ad_stars`, `ad_price`,
 * `ad_advertiser` (đều tùy chọn), cộng thêm các control ẩn `ad_close` / `ad_collapse` / `ad_next` mà chỉ
 * màn có luồng riêng (collapsible, full-screen break...) mới hiện và gắn xử lý.
 *
 * Các template Collap là card ở dạng expand; dải thu nhỏ tương ứng là [TINY1] (Collap1, Collap3) và
 * [TINY2] (Collap2).
 */
enum class NativeAdTemplate(val key: String, @LayoutRes val layout: Int) {
    MEDIA("native media", R.layout.ad_tpl_big1),
    NO_MEDIA("native no media", R.layout.ad_tpl_small1),
    BANNER("native banner", R.layout.ad_tpl_tiny1),
    FULLSCREEN("native fullscreen", R.layout.ad_tpl_full1),

    BIG1("big1", R.layout.ad_tpl_big1),
    BIG2("big2", R.layout.ad_tpl_big2),
    BIG3("big3", R.layout.ad_tpl_big3),
    BIG4("big4", R.layout.ad_tpl_big4),
    BIG5("big5", R.layout.ad_tpl_big5),

    /** Giống hệt Big5 trong Figma. */
    BIG6("big6", R.layout.ad_tpl_big5),
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

    /** "Native Language, Get start enable = 1": card trên (= Big1) và card dưới (= Big2). */
    LANG1_A("lang1_a", R.layout.ad_tpl_big1),
    LANG1_B("lang1_b", R.layout.ad_tpl_big2),

    /** "Native Language, Get start enable = 2": card trên (CTA màu tím) và card dưới (= Big5). */
    LANG2_A("lang2_a", R.layout.ad_tpl_lang2),
    LANG2_B("lang2_b", R.layout.ad_tpl_big5),
    ;

    companion object {
        /** Template ứng với một giá trị `native_template` (không phân biệt hoa thường); không khớp hoặc rỗng thì về [MEDIA]. */
        fun fromKey(value: String?): NativeAdTemplate {
            val key = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == key } ?: MEDIA
        }

        /** Toàn bộ key, dùng cho gợi ý ở Debug Config. */
        val keys: String get() = entries.joinToString(" | ") { it.key }
    }
}
