package com.namstd.androidskillhub.core.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Loại quảng cáo của một placement (`ad_type`). [key] là đúng giá trị ghi trong JSON của web cấu hình quảng cáo.
 * Placement type nào nhận ad type nào: xem phần "Tài liệu" trên web.
 */
enum class AdType(val key: String) {
    AOA("aoa"),
    INTER("inter"),
    NATIVE("native"),
    BANNER("banner"),
    BANNER_COLLAPSIBLE("banner_collapsible"),
    REWARD("reward"),
    REWARD_INTER("reward_inter"),
    /** Native toàn màn hình thay cho Interstitial (template Fullscreen). */
    NATIVE_AS_INTER("native_as_inter"),
    /** Interstitial, đóng xong hiện tiếp 1 native toàn màn hình. */
    INTER_THEN_NATIVE("inter_then_native"),
    /** Native nhỏ (Small / Tiny) ở ô banner. */
    NATIVE_AS_BANNER("native_as_banner"),
    /** Native ở ô banner, lúc đầu mở rộng, user bấm thu gọn (template Collapsible). */
    NATIVE_AS_BANNER_COLLAP("native_as_banner_collap"),
    ;

    companion object {
        fun fromKey(value: String?): AdType? =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.key == v } }
    }
}

/** Loại vị trí quảng cáo (`type`). */
enum class PlacementType(val key: String) {
    SPLASH("splash"),
    ONBOARDING("onboarding"),
    INTER("inter"),
    BANNER("banner"),
    RESUME("resume"),
    NATIVE("native"),
    REWARD("reward"),
    REWARD_INTER("reward_inter"),
    ;

    companion object {
        fun fromKey(value: String?): PlacementType? =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.key == v } }
    }
}

/** `fallback` của luồng inter_then_native khi 1 trong 2 quảng cáo chưa tải được. */
enum class AdFallback(val key: String) {
    /** Vẫn hiện quảng cáo đã tải được (mặc định). */
    SHOW_AVAILABLE("show_available"),
    /** Thiếu 1 trong 2 thì bỏ qua cả luồng. */
    SKIP("skip"),
    ;

    companion object {
        fun fromKey(value: String?): AdFallback =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.key == v } } ?: SHOW_AVAILABLE
    }
}

/**
 * Cấu hình một placement trong `profiles.<bộ>.placements`, vd
 * `"ads_splash": { "type": "splash", "ad_type": "inter_then_native", "enable": true, "ad_ids": { "inter": [...], "native": [...] }, "template": "Full1" }`.
 * Thời gian tính bằng ms. Key không có = mặc định theo tài liệu.
 *
 * - [adIds]: `{ loại Ad Unit (aoa / inter / native / banner / reward / reward_inter): [ID theo thứ tự ưu tiên] }`, tối đa 3 ID mỗi loại.
 * - [template]: tên native template (gốc trong SDK hoặc template riêng ở `templates` gốc file), trống = mặc định của chỗ hiện.
 * - [pages]: placement type onboarding - chèn trang native full sau các trang này (đếm từ 1).
 * - [step]: placement inter - cứ [step] lần gọi mới hiện 1 lần.
 * - [waitMs]: đếm ngược trước khi đóng được native toàn màn hình.
 * - [refreshMs]: tự tải lại ô banner, 0 = không.
 * - [openAdOnClicks]: native_as_banner_collap - lần click thứ mấy trên quảng cáo thì mở trang quảng cáo.
 * - [fallback]: inter_then_native - xử lý khi thiếu 1 trong 2 quảng cáo.
 * - [customUi]: placement type resume - dùng màn Welcome Back riêng (app hiện chỉ có 1 màn Welcome Back).
 */
data class PlacementConfig(
    val enable: Boolean,
    val type: PlacementType? = null,
    val adType: AdType? = null,
    val adIds: Map<String, List<String>> = emptyMap(),
    val template: String = "",
    val pages: Set<Int> = emptySet(),
    val step: Int = 1,
    val waitMs: Long = 0L,
    val refreshMs: Long = 0L,
    val openAdOnClicks: Set<Int> = emptySet(),
    val fallback: AdFallback = AdFallback.SHOW_AVAILABLE,
    val customUi: Boolean = false,
) {
    /** ID của loại Ad Unit [idType] (vd `inter`, `native`) theo thứ tự thử; rỗng nếu không có. */
    fun ids(idType: String): List<String> = adIds[idType].orEmpty()

    companion object {
        /** Placement tắt hẳn - khi tắt quảng cáo, user Premium, hoặc placement không có trong cấu hình. */
        val OFF = PlacementConfig(enable = false)

        private const val MAX_IDS_PER_TYPE = 3

        /** Object JSON của một placement → cấu hình; null = [OFF]. Trường sai kiểu dùng mặc định. */
        fun fromJson(json: JSONObject?): PlacementConfig {
            if (json == null) return OFF
            return PlacementConfig(
                enable = json.optBoolean("enable", false),
                type = PlacementType.fromKey(json.optString("type")),
                adType = AdType.fromKey(json.optString("ad_type")),
                adIds = adIds(json.optJSONObject("ad_ids")),
                template = json.optString("template").trim(),
                pages = intSet(json.opt("pages")),
                step = json.optInt("step", 1).coerceAtLeast(1),
                waitMs = json.optLong("wait_ms", 0L).coerceAtLeast(0L),
                refreshMs = json.optLong("refresh_ms", 0L).coerceAtLeast(0L),
                openAdOnClicks = intSet(json.opt("open_ad_on_clicks")),
                fallback = AdFallback.fromKey(json.optString("fallback")),
                customUi = json.optBoolean("custom_ui", false),
            )
        }

        private fun adIds(json: JSONObject?): Map<String, List<String>> {
            if (json == null) return emptyMap()
            return json.keys().asSequence().associate { name ->
                val value = json.opt(name)
                name.trim().lowercase() to when (value) {
                    is JSONArray -> (0 until value.length()).map { value.optString(it).trim() }
                    null, JSONObject.NULL -> emptyList()
                    else -> listOf(value.toString().trim())
                }.filter { it.isNotEmpty() }.take(MAX_IDS_PER_TYPE)
            }
        }

        /** `[2, 4]` (hoặc chuỗi `"2,4"`) → {2, 4}; bỏ số < 1. */
        internal fun intSet(value: Any?): Set<Int> = when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it)?.toString()?.trim()?.toIntOrNull() }
            null, JSONObject.NULL -> emptyList()
            else -> value.toString().split(",").mapNotNull { it.trim().toIntOrNull() }
        }.filter { it >= 1 }.toSet()
    }
}

/**
 * Tên các placement mà base app gọi (key trong `profiles.<bộ>.placements`). App con đổi / thêm theo màn của mình.
 * Placement không có trong bộ = tắt (app không giữ mặc định).
 */
enum class AdKey(val key: String) {
    ADS_SPLASH("ads_splash"),
    NATIVE_SPLASH("native_splash"),
    /** Native màn Language lần đầu cài app. */
    NATIVE_LANGUAGE("native_language"),
    /** Native thứ 2 của màn Language: native của màn Language mở từ Cài đặt. */
    NATIVE_LANGUAGE_2("native_language_2"),
    NATIVE_LANGUAGE_SMALL("native_language_small"),
    INTER_LANGUAGE("inter_language"),
    /** Native ở chân các trang nội dung Onboarding. */
    NATIVE_ONBOARDING("native_onboarding"),
    /** Trang native toàn màn hình chèn sau các trang `pages` của Onboarding. */
    NATIVE_FULL_ONBOARDING("native_full_onboarding"),
    /** Khi xong Onboarding (thường inter_then_native). */
    INTER_AFTER_ONBOARDING("inter_after_onboarding"),
    /**
     * Khi user quay lại app từ nền. `ad_type` quyết định: aoa = App Open; native = màn Welcome Back có native
     * (ID / template / wait_ms của chính placement này); inter / native_as_inter / inter_then_native = inter.
     */
    ON_RESUME("on_resume"),
    BANNER_HOME("banner_home"),
    /** Native xen trong feed màn chính. */
    NATIVE_HOME_FEED("native_home_feed"),
    /** Inter ở màn chính (app mẫu: bấm Cài đặt). */
    INTER_HOME("inter_home"),
    BANNER_SETTING("banner_setting"),

    // Quảng cáo thưởng (type reward / reward_inter). Hai placement mẫu, base chưa màn nào gọi - app con đổi / thêm
    // theo màn của mình. wait_ms của reward_inter = thời gian màn đếm ngược trước quảng cáo.
    REWARD_SAMPLE("reward_sample"),
    REWARD_INTER_SAMPLE("reward_inter_sample"),
    ;

    companion object {
        fun fromKey(value: String): AdKey? = entries.firstOrNull { it.key == value.trim() }
    }
}

/** Bộ quảng cáo: [FULL] đầy đủ (`profiles.full`), [BASIC] ít quảng cáo (`profiles.basic`). */
enum class AdSet(val key: String) { FULL("full"), BASIC("basic") }

/**
 * File cấu hình quảng cáo xuất từ web:
 * `{ "config_version", "version", "ads_profile", "settings", "profiles": { "full": {...}, "basic": {...} }, "templates" }`.
 * Mỗi bộ gồm `settings` (inter_gap_ms, inter_gap_ignore) và `placements` (đã gộp sẵn mặc định + ghi đè của bộ).
 * Placement không có trong bộ = tắt. JSON hỏng hoặc trống = như `{}` (mọi placement tắt).
 */
class AdConfigDocument(raw: String) {
    private val root: JSONObject = parseObject(raw)

    /** Tên version cấu hình trên web (vd "111"), chỉ để đối chiếu. */
    val configVersion: String = root.optString("config_version")

    /** `ads_profile` ở gốc file (số 0–3), null nếu không có. */
    val adsProfile: String? = root.opt("ads_profile")?.takeIf { it != JSONObject.NULL }?.toString()

    /** `settings` gốc: thiết lập dùng chung cho cả 2 bộ. */
    val settings: JSONObject = root.optJSONObject("settings") ?: JSONObject()

    /** `templates` gốc: template riêng (`{ tên: { base, cta_bg, ... } }`). */
    val templatesJson: JSONObject = root.optJSONObject("templates") ?: JSONObject()

    private val profiles: JSONObject = root.optJSONObject("profiles") ?: JSONObject()
    private val placementCache = HashMap<Pair<AdSet, AdKey>, PlacementConfig>()

    private fun profile(set: AdSet): JSONObject = profiles.optJSONObject(set.key) ?: JSONObject()

    /** `profiles.<bộ>.settings`. */
    fun profileSettings(set: AdSet): JSONObject = profile(set).optJSONObject("settings") ?: JSONObject()

    /** JSON gốc của placement [key] trong [set] (để hiện ở Debug Config), trống nếu không có. */
    fun placementJson(set: AdSet, key: AdKey): String =
        profile(set).optJSONObject("placements")?.optJSONObject(key.key)?.toString().orEmpty()

    @Synchronized
    fun placement(set: AdSet, key: AdKey): PlacementConfig = placementCache.getOrPut(set to key) {
        PlacementConfig.fromJson(profile(set).optJSONObject("placements")?.optJSONObject(key.key))
    }
}

/** Object JSON từ chuỗi; trống hoặc hỏng = `{}`. */
internal fun parseObject(raw: String): JSONObject = try {
    if (raw.isBlank()) JSONObject() else JSONObject(raw)
} catch (e: Exception) {
    JSONObject()
}
