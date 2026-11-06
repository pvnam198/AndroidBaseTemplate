package com.namstd.androidskillhub.core.ads.template

import com.namstd.androidskillhub.core.config.parseObject
import org.json.JSONObject

/**
 * Màu / hình dạng / kích thước đè lên một mẫu native (parameter `ad_templates`). Trường null = giữ như mẫu gốc. Áp được cho mọi
 * mẫu vì các layout native dùng chung view id (`ad_call_to_action`, `ad_headline`, `ad_body`...). Màu dạng `#RRGGBB`
 * hoặc `#AARRGGBB`, ghi sai thì bỏ qua. Không có trường nào ẩn / che được nhãn "Ad", ảnh hay nút - các phần bắt buộc
 * theo chính sách native của AdMob luôn giữ nguyên.
 */
data class NativeStyle(
    val ctaBg: Int? = null,
    val ctaText: Int? = null,
    val ctaCornerDp: Float? = null,
    val cardBg: Int? = null,
    val cardCornerDp: Float? = null,
    val cardStroke: Int? = null,
    val cardStrokeDp: Float? = null,
    val titleColor: Int? = null,
    val bodyColor: Int? = null,
    val ctaHeightDp: Float? = null,
    val ctaTextSp: Float? = null,
    val titleTextSp: Float? = null,
    val bodyTextSp: Float? = null,
    val mediaHeightDp: Float? = null,
    val iconSizeDp: Float? = null,
    val cardPaddingDp: Float? = null,
) {
    val isEmpty: Boolean get() = this == EMPTY

    companion object {
        val EMPTY = NativeStyle()

        /** Các trường `ad_templates` đọc được (để gợi ý ở Debug Config / tài liệu). */
        val FIELDS = listOf(
            "cta_bg", "cta_text", "cta_corner_dp", "card_bg", "card_corner_dp", "card_stroke", "card_stroke_dp",
            "title_color", "body_color", "cta_height_dp", "cta_text_sp", "title_text_sp", "body_text_sp",
            "media_height_dp", "icon_size_dp", "card_padding_dp",
        )

        /** Giới hạn kích thước để style không làm hỏng mẫu; ảnh ≥ 120dp theo chính sách native của AdMob. */
        const val MIN_MEDIA_DP = 120f

        fun fromJson(json: JSONObject?): NativeStyle {
            if (json == null) return EMPTY
            return NativeStyle(
                ctaBg = color(json, "cta_bg"),
                ctaText = color(json, "cta_text"),
                ctaCornerDp = dp(json, "cta_corner_dp"),
                cardBg = color(json, "card_bg"),
                cardCornerDp = dp(json, "card_corner_dp"),
                cardStroke = color(json, "card_stroke"),
                cardStrokeDp = dp(json, "card_stroke_dp"),
                titleColor = color(json, "title_color"),
                bodyColor = color(json, "body_color"),
                ctaHeightDp = dp(json, "cta_height_dp")?.coerceIn(28f, 80f),
                ctaTextSp = dp(json, "cta_text_sp")?.coerceIn(10f, 24f),
                titleTextSp = dp(json, "title_text_sp")?.coerceIn(10f, 28f),
                bodyTextSp = dp(json, "body_text_sp")?.coerceIn(9f, 22f),
                mediaHeightDp = dp(json, "media_height_dp")?.coerceIn(MIN_MEDIA_DP, 400f),
                iconSizeDp = dp(json, "icon_size_dp")?.coerceIn(24f, 96f),
                cardPaddingDp = dp(json, "card_padding_dp")?.coerceIn(0f, 32f),
            )
        }

        /** `#RRGGBB` → 0xFFRRGGBB, `#AARRGGBB` → 0xAARRGGBB; sai định dạng → null. */
        fun parseColor(value: String?): Int? {
            val hex = value?.trim()?.removePrefix("#") ?: return null
            if (hex.length != 6 && hex.length != 8) return null
            val n = hex.toLongOrNull(16) ?: return null
            return (if (hex.length == 6) n or 0xFF000000L else n).toInt()
        }

        private fun color(json: JSONObject, name: String): Int? =
            if (json.has(name)) parseColor(json.optString(name)) else null

        private fun dp(json: JSONObject, name: String): Float? =
            if (json.has(name)) json.optDouble(name, Double.NaN).takeIf { !it.isNaN() && it >= 0 }?.toFloat() else null
    }
}

/** Mẫu để vẽ một native: layout gốc [template] + màu / hình dạng [style] đè lên. */
data class NativeTemplateSpec(val template: NativeAdTemplate, val style: NativeStyle = NativeStyle.EMPTY)

/**
 * Parameter `ad_templates`: `{ tên mẫu: { "base": "Big5", "cta_bg": "#7B2FA6", ... } }`.
 * - Tên mới (vd `cta_top_purple`): mẫu tự đặt, vẽ bằng layout `base` + style. Cột `template` của placement ghi tên này.
 * - Tên trùng mẫu có sẵn (vd `Big5`): đè style lên mẫu đó ở mọi placement dùng nó; `base` bỏ qua.
 * Tên không phân biệt hoa thường. JSON hỏng = không có mẫu tuỳ biến nào.
 */
class AdTemplatesDocument(raw: String) {
    private val entries: Map<String, JSONObject> = parseObject(raw).let { json ->
        json.keys().asSequence().mapNotNull { name -> json.optJSONObject(name)?.let { name.trim().lowercase() to it } }.toMap()
    }

    /** Tên các mẫu tuỳ biến (không gồm mẫu có sẵn chỉ bị đè style), theo thứ tự trong JSON. */
    val customNames: List<String>
        get() = entries.keys.filter { name -> NativeAdTemplate.entries.none { it.key == name } }

    /** Mẫu cho giá trị `template` [name] của một placement; [fallback] khi trống / không khớp gì. */
    fun resolve(name: String?, fallback: NativeAdTemplate): NativeTemplateSpec {
        val key = name?.trim()?.lowercase().orEmpty()
        val custom = entries[key]
        val builtIn = NativeAdTemplate.entries.firstOrNull { it.key == key }
        val base = builtIn
            ?: custom?.optString("base")?.let { b -> NativeAdTemplate.entries.firstOrNull { it.key == b.trim().lowercase() } }
            ?: fallback
        // Mẫu có sẵn được đè style thì style đó áp cả khi placement dùng nó làm mặc định (template trống).
        return NativeTemplateSpec(base, NativeStyle.fromJson(custom ?: entries[base.key]))
    }
}
