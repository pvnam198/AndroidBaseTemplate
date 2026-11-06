package com.namstd.androidskillhub.core.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Loại quảng cáo của một placement (`format` trong `ad_placements`). [key] là đúng giá trị ghi trong JSON / sheet.
 * Placement nào nhận format nào: xem `docs/remote-config-v3/README.md`.
 */
enum class PlacementFormat(val key: String) {
    AOA("aoa"),
    INTER("inter"),
    NATIVE_FULL("native_full"),
    INTER_THEN_NATIVE_FULL("inter_then_native_full"),
    BANNER("banner"),
    BANNER_COLLAPSIBLE("banner_collapsible"),
    NATIVE("native"),
    NATIVE_COLLAPSIBLE("native_collapsible"),
    WELCOME_BACK("welcome_back"),
    ;

    companion object {
        fun fromKey(value: String?): PlacementFormat? =
            value?.trim()?.lowercase()?.let { v -> entries.firstOrNull { it.key == v } }
    }
}

/**
 * Cấu hình một placement - một object trong `ad_placements.full` / `ad_placements.basic`, vd
 * `"ads_splash": { "format": "inter_then_native_full", "wait_ms": 5000 }`. Thời gian tính bằng ms.
 *
 * - [enable]: bật / tắt, chỉ nghĩa này.
 * - [format]: loại quảng cáo (placement không chọn loại thì null).
 * - [template]: tên mẫu native (Big1…, Tiny1…), trống = mẫu mặc định của placement.
 * - [pages]: `native_onboarding` = trang có native; `native_full_onboarding` = chèn trang native full sau các trang này.
 * - [step]: inter chỉ hiện ở lần yêu cầu chia hết cho [step].
 * - [waitMs]: chờ / đếm ngược trước khi đóng được native.
 * - [refreshMs]: tự tải lại ô banner, 0 = không.
 * - [openAdOnClicks]: native collapsible - lần click thứ mấy trên quảng cáo thì mở trang quảng cáo.
 */
data class PlacementConfig(
    val enable: Boolean,
    val format: PlacementFormat? = null,
    val template: String = "",
    val pages: Set<Int> = emptySet(),
    val step: Int = 1,
    val waitMs: Long = 0L,
    val refreshMs: Long = 0L,
    val openAdOnClicks: Set<Int> = emptySet(),
) {
    /** Ghi đè các trường có trong [json] lên cấu hình này; trường thiếu hoặc sai kiểu giữ nguyên. */
    fun mergedWith(json: JSONObject?): PlacementConfig {
        if (json == null) return this
        return PlacementConfig(
            enable = if (json.has("enable")) json.optBoolean("enable", enable) else enable,
            format = if (json.has("format")) PlacementFormat.fromKey(json.optString("format")) ?: format else format,
            template = if (json.has("template")) json.optString("template").trim() else template,
            pages = if (json.has("pages")) intSet(json.opt("pages")) else pages,
            step = if (json.has("step")) json.optInt("step", step).coerceAtLeast(1) else step,
            waitMs = if (json.has("wait_ms")) json.optLong("wait_ms", waitMs).coerceAtLeast(0L) else waitMs,
            refreshMs = if (json.has("refresh_ms")) json.optLong("refresh_ms", refreshMs).coerceAtLeast(0L) else refreshMs,
            openAdOnClicks = if (json.has("open_ad_on_clicks")) intSet(json.opt("open_ad_on_clicks")) else openAdOnClicks,
        )
    }

    companion object {
        /** Placement tắt hẳn - khi tắt quảng cáo hoặc user Premium. */
        val OFF = PlacementConfig(enable = false)

        /** `[2, 4]` (hoặc chuỗi `"2,4"`) → {2, 4}; bỏ số < 1. */
        internal fun intSet(value: Any?): Set<Int> = when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it)?.toString()?.trim()?.toIntOrNull() }
            null, JSONObject.NULL -> emptyList()
            else -> value.toString().split(",").mapNotNull { it.trim().toIntOrNull() }
        }.filter { it >= 1 }.toSet()
    }
}

/**
 * Các placement quảng cáo của base app (key trong `ad_placements`) kèm cấu hình mặc định của bộ full - dùng khi
 * JSON không ghi. Mặc định ở đây phải khớp bảng "Mặc định của bộ full" trong `docs/remote-config-v3/README.md`.
 */
enum class AdKey(val key: String, val fullDefault: PlacementConfig) {
    ADS_SPLASH("ads_splash", PlacementConfig(enable = true, format = PlacementFormat.INTER)),
    NATIVE_SPLASH("native_splash", PlacementConfig(enable = false)),
    NATIVE_LANGUAGE("native_language", PlacementConfig(enable = true)),
    NATIVE_LANGUAGE_SMALL("native_language_small", PlacementConfig(enable = false)),
    INTER_LANGUAGE("inter_language", PlacementConfig(enable = true, format = PlacementFormat.INTER)),
    NATIVE_ONBOARDING("native_onboarding", PlacementConfig(enable = true, pages = setOf(1, 2, 3, 4))),
    NATIVE_FULL_ONBOARDING("native_full_onboarding", PlacementConfig(enable = false)),
    INTER_ONBOARDING("inter_onboarding", PlacementConfig(enable = true, format = PlacementFormat.INTER)),
    ON_RESUME("on_resume", PlacementConfig(enable = true, format = PlacementFormat.AOA)),
    NATIVE_WELCOME_BACK("native_welcome_back", PlacementConfig(enable = true)),
    BANNER_HOME("banner_home", PlacementConfig(enable = true, format = PlacementFormat.BANNER_COLLAPSIBLE)),
    NATIVE_HOME_FEED("native_home_feed", PlacementConfig(enable = true)),
    INTER_HOME("inter_home", PlacementConfig(enable = false, format = PlacementFormat.INTER)),
    BANNER_SETTING("banner_setting", PlacementConfig(enable = true, format = PlacementFormat.BANNER_COLLAPSIBLE)),
    ;

    companion object {
        fun fromKey(value: String): AdKey? = entries.firstOrNull { it.key == value.trim() }
    }
}

/** Bộ quảng cáo: [FULL] đầy đủ (`ad_placements.full`), [BASIC] cơ bản (`ad_placements.basic`). */
enum class AdSet { FULL, BASIC }

/**
 * Parameter `ad_placements`: `{ "full": { placement: {...} }, "basic": { placement: {...} } }`.
 * - full: placement không ghi = [AdKey.fullDefault]; có ghi thì đè từng trường lên mặc định.
 * - basic: **chỉ placement có mặt mới chạy** (mặc định bật, `"enable": false` để tắt), các trường đè lên mặc định
 *   của full (format...), `step` mặc định 1. Placement không có trong basic = tắt.
 * JSON hỏng hoặc trống = như `{}`.
 */
class AdPlacementsDocument(raw: String) {
    private val root: JSONObject = parseObject(raw)
    private val full: JSONObject = root.optJSONObject("full") ?: JSONObject()
    private val basic: JSONObject = root.optJSONObject("basic") ?: JSONObject()

    fun placement(set: AdSet, key: AdKey): PlacementConfig = when (set) {
        AdSet.FULL -> key.fullDefault.mergedWith(full.optJSONObject(key.key))
        AdSet.BASIC -> basic.optJSONObject(key.key)
            ?.let { key.fullDefault.copy(enable = true, step = 1).mergedWith(it) }
            ?: PlacementConfig.OFF
    }

    /** JSON của [key] trong [set] (để hiện ở Debug Config), trống nếu không có. */
    fun placementJson(set: AdSet, key: AdKey): String =
        (if (set == AdSet.FULL) full else basic).optJSONObject(key.key)?.toString().orEmpty()
}

/** Parameter `ad_ids`: `{ ad_unit: [id_1, id_2, id_3] }`. Tên ad unit không phân biệt hoa thường. */
class AdIdsDocument(raw: String) {
    private val ids: Map<String, List<String>> = parseObject(raw).let { json ->
        json.keys().asSequence().associate { name ->
            val value = json.opt(name)
            name.trim().lowercase() to when (value) {
                is JSONArray -> (0 until value.length()).map { value.optString(it).trim() }
                else -> listOf(value?.toString().orEmpty().trim())
            }.filter { it.isNotEmpty() }
        }
    }

    /** ID của ad unit [name] theo thứ tự thử (cao → thấp); rỗng nếu chưa đặt. */
    fun ids(name: String): List<String> = ids[name.lowercase()].orEmpty()
}

/** Object JSON từ chuỗi; trống hoặc hỏng = `{}`. */
internal fun parseObject(raw: String): JSONObject = try {
    if (raw.isBlank()) JSONObject() else JSONObject(raw)
} catch (e: Exception) {
    JSONObject()
}
