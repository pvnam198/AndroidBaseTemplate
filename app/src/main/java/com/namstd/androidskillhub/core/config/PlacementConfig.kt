package com.namstd.androidskillhub.core.config

import org.json.JSONObject

/**
 * Các key vị trí quảng cáo trong sheet "Ad Script" (cột `key`) - mỗi key là một object trong JSON `ad_config`
 * (xem [AdConfigDocument]). [defaultEnable] là `enable` khi `ad_config` chưa có vị trí này.
 */
enum class AdKey(val key: String, val defaultEnable: String) {
    ADS_SPLASH("ads_splash", "2"),
    NATIVE_SPLASH("native_splash", "0"),
    NATIVE_LANGUAGE("native_language", "1"),
    NATIVE_LANGUAGE_SMALL("native_language_small", "0"),
    INTER_LANGUAGE("inter_language", "1"),
    NATIVE_INTRO("native_intro", "1234"),
    NATIVE_INTRO_FULL("native_intro_full", "0"),
    INTER_INTRO("inter_intro", "1"),

    /** Nằm trong `settings` của `ad_config` (`on_resume`, `on_resume_id`), không phải một object riêng. */
    ON_RESUME("on_resume", "1"),
    INTER_WELCOME_BACK("inter_welcome_back", "0"),
    NATIVE_WELCOME_BACK("native_welcome_back", "1"),
    BANNER_HOME("banner_home", "2"),
    INTER_HOME("inter_home", "0"),
    BANNER_SETTING("banner_setting", "2"),
    INTER_BACK("inter_back", "0"),
}

/**
 * Cấu hình một vị trí quảng cáo - một object trong `ad_config`, vd:
 * ```
 * "ads_splash": {
 *   "enable": 3, "wait_time": 5000,
 *   "inter":        { "name1": "INTER_SPLASH", "id1": "ca-app-pub-…", "id2": "…", "id3": "…" },
 *   "native_inter": { "name1": "NATIVE_INTER_SPLASH", "id1": "…" }
 * }
 * ```
 * Mỗi object con có `name1` là một ad unit; tên object con (`inter`, `native_inter`, `native_intro1`…) không quan
 * trọng, app nhận ra unit qua `name1`. Cột nào thiếu thì dùng mặc định. Thời gian tính bằng **ms**.
 *
 * - [enable]: giữ nguyên chuỗi, vì có vị trí đọc theo từng chữ số (`native_intro` = `24`, `native_intro_full` = `123`).
 * - [stepCount]: inter chỉ hiện ở lần chia hết cho N (tối thiểu 1).
 * - [refreshRateMs]: tự reload banner/native sau bấy nhiêu ms, `0` = không tự reload.
 * - [collapConfig]: lần bấm nút X của native collapsible mở quảng cáo (xem `CollapClickGate`).
 * - [waitTimeMs]: đếm ngược trên màn native toàn màn hình trước khi cho đóng/đi tiếp.
 * - [nativeTemplate]: tên template native (Big5, Tiny1…), trống = mặc định.
 * - [ids]: ID theo tên ad unit (`name1`) → `[id1, id2, id3]` cho waterfall.
 */
data class PlacementConfig(
    val enable: String,
    val stepCount: Int = 1,
    val refreshRateMs: Long = 0L,
    val collapConfig: String = "",
    val waitTimeMs: Long = 0L,
    val nativeTemplate: String = "",
    val ids: Map<String, List<String>> = emptyMap(),
) {
    /** `enable` dạng số: `0` = tắt, các số khác theo bảng mã của từng vị trí. */
    val enableCode: Int get() = enable.trim().toIntOrNull() ?: 0

    /** `enable` đọc theo từng chữ số (`24` = {2, 4}) - cho vị trí liệt kê trang. */
    val enableDigits: Set<Int>
        get() = enable.filter { it.isDigit() }.map { it.digitToInt() }.filter { it >= 1 }.toSet()

    companion object {
        /** Vị trí tắt hẳn - dùng khi `ads_enable = 0` hoặc user Premium. */
        val OFF = PlacementConfig(enable = "0")

        /** Đọc một object vị trí trong `ad_config`; null thì dùng [AdKey.defaultEnable]. */
        fun fromJson(json: JSONObject?, key: AdKey): PlacementConfig {
            if (json == null) return PlacementConfig(enable = key.defaultEnable)
            return PlacementConfig(
                enable = json.opt("enable")?.toString() ?: key.defaultEnable,
                stepCount = json.optInt("step_count", 1).coerceAtLeast(1),
                refreshRateMs = json.optLong("refresh_rate", 0L).coerceAtLeast(0L),
                collapConfig = json.opt("collap_config")?.toString().orEmpty(),
                waitTimeMs = json.optLong("wait_time", 0L).coerceAtLeast(0L),
                nativeTemplate = json.optString("native_template", ""),
                ids = unitIds(json),
            )
        }

        /**
         * Giá trị override một vị trí ở màn Debug Config: một object JSON như trong `ad_config`, hoặc chỉ một số
         * (= `enable`). JSON hỏng thì coi như vị trí đó tắt.
         */
        fun parseOverride(raw: String, key: AdKey): PlacementConfig {
            val text = raw.trim()
            if (!text.startsWith("{")) return PlacementConfig(enable = text)
            return try {
                fromJson(JSONObject(text), key)
            } catch (e: Exception) {
                OFF
            }
        }

        /** Mọi object con có `name1` → danh sách `id1`, `id2`, `id3` của nó (bỏ id trống). */
        private fun unitIds(json: JSONObject): Map<String, List<String>> =
            json.keys().asSequence().mapNotNull { json.optJSONObject(it) }
                .filter { it.optString("name1").isNotBlank() }
                .associate { unit ->
                    unit.optString("name1").trim() to
                        listOf("id1", "id2", "id3").map { unit.optString(it).trim() }.filter(String::isNotEmpty)
                }
    }
}

/**
 * Toàn bộ JSON của key Remote Config `ad_config`: object `settings` (cấu hình chung) + mỗi vị trí quảng cáo một
 * object (xem [PlacementConfig]). Hỏng hoặc trống thì coi như rỗng - mọi giá trị về mặc định.
 */
class AdConfigDocument(raw: String) {
    private val root: JSONObject = try {
        if (raw.isBlank()) JSONObject() else JSONObject(raw)
    } catch (e: Exception) {
        JSONObject()
    }

    private val settings: JSONObject = root.optJSONObject("settings") ?: JSONObject()

    /** Giá trị trong `settings` dạng chuỗi (số / boolean cũng đọc được), null nếu không có. */
    fun setting(name: String): String? = settings.opt(name)?.takeIf { it != JSONObject.NULL }?.toString()

    /** Cấu hình vị trí [key]. Riêng `on_resume`: lấy từ `settings.on_resume` và `settings.on_resume_id`. */
    fun placement(key: AdKey): PlacementConfig {
        if (key == AdKey.ON_RESUME && !root.has(key.key)) {
            val id = setting("on_resume_id")?.trim().orEmpty()
            return PlacementConfig(
                enable = setting("on_resume") ?: key.defaultEnable,
                ids = if (id.isEmpty()) emptyMap() else mapOf("ON_RESUME" to listOf(id)),
            )
        }
        return PlacementConfig.fromJson(root.optJSONObject(key.key), key)
    }

    /** JSON của riêng vị trí [key] (để hiện ở Debug Config), trống nếu `ad_config` không có. */
    fun placementJson(key: AdKey): String = root.optJSONObject(key.key)?.toString().orEmpty()
}
