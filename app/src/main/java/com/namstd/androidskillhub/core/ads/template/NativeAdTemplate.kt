package com.namstd.androidskillhub.core.ads.template

import androidx.annotation.LayoutRes
import com.namstd.androidskillhub.R

/**
 * Native ad card layouts, selected by Remote Config key `native_template` ([key] is the value to set).
 *
 * The first four are the original app layouts. The rest are the Figma "Wavez - ADs" / "Mẫu v2" templates
 * (layout/ad_tpl_*.xml); every one uses the same view ids, so [com.namstd.androidskillhub.core.ads.NativeAds]
 * can bind any of them:
 * `ad_headline` (required), `ad_icon`, `ad_body`, `ad_call_to_action`, `ad_media`, `ad_stars`, `ad_price`,
 * `ad_advertiser` (all optional), plus hidden controls `ad_close` / `ad_collapse` / `ad_next` that only a
 * screen with its own flow (collapsible, full-screen break...) shows and wires.
 *
 * The Collap templates are the expanded cards; their collapsed strips are [TINY1] (Collap1, Collap3) and
 * [TINY2] (Collap2).
 */
enum class NativeAdTemplate(val key: String, @LayoutRes val layout: Int) {
    MEDIA("native media", R.layout.ad_native),
    NO_MEDIA("native no media", R.layout.ad_native_no_media),
    BANNER("native banner", R.layout.ad_native_banner),
    FULLSCREEN("native fullscreen", R.layout.ad_native_fullscreen),

    BIG1("big1", R.layout.ad_tpl_big1),
    BIG2("big2", R.layout.ad_tpl_big2),
    BIG3("big3", R.layout.ad_tpl_big3),
    BIG4("big4", R.layout.ad_tpl_big4),
    BIG5("big5", R.layout.ad_tpl_big5),

    /** Identical to Big5 in Figma. */
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

    /** "Native Language, Get start enable = 1": top card (= Big1) and bottom card (= Big2). */
    LANG1_A("lang1_a", R.layout.ad_tpl_big1),
    LANG1_B("lang1_b", R.layout.ad_tpl_big2),

    /** "Native Language, Get start enable = 2": top card (purple CTA) and bottom card (= Big5). */
    LANG2_A("lang2_a", R.layout.ad_tpl_lang2),
    LANG2_B("lang2_b", R.layout.ad_tpl_big5),
    ;

    companion object {
        /** Template for a `native_template` value (case-insensitive); unknown or blank falls back to [MEDIA]. */
        fun fromKey(value: String?): NativeAdTemplate {
            val key = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.key == key } ?: MEDIA
        }

        /** All keys, for the Debug Config hint. */
        val keys: String get() = entries.joinToString(" | ") { it.key }
    }
}
