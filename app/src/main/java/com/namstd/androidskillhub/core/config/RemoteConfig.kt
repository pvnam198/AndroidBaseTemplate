package com.namstd.androidskillhub.core.config

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.namstd.androidskillhub.core.ads.template.AdTemplatesDocument
import org.json.JSONObject

/** Khoảng cách tối thiểu giữa 2 lần hiện interstitial (ms) + các placement được miễn. */
data class InterDelayConfig(val gapMs: Long, val ignore: Set<String>)

/** Nút xác nhận màn Language. nextStyle: 1 = icon dấu tích, 2 = nút chữ. */
data class LanguageSetup(val nextStyle: Int, val loading: Boolean)

/**
 * Một trang nội dung Onboarding. nextStyle: 1 = nút, 2 = chữ "Next". indicatorPosition: 1 = chấm phía trên nút Next
 * (Next ở dưới), 0 = chấm phía dưới. delayMs: chờ trước khi hiện Next. showDots: hiện chấm tròn.
 */
data class OnboardingSetup(val nextStyle: Int, val indicatorPosition: Int, val delayMs: Long, val showDots: Boolean)

/**
 * `ads_profile`: user nào dùng bộ full, còn lại dùng bộ basic. Trên sheet / Firebase ghi số [code]:
 * 0 tắt · 1 mọi user full · 2 user từ marketing full, còn lại basic · 3 máy test ad basic, còn lại full.
 * Cũng nhận tên [key] (vd `full_for_paid_users`).
 */
enum class AdsProfileRule(val code: Int, val key: String) {
    OFF(0, "off"),
    FULL_FOR_ALL(1, "full_for_all"),
    FULL_FOR_PAID_USERS(2, "full_for_paid_users"),
    FULL_EXCEPT_TEST_DEVICES(3, "full_except_test_devices"),
    ;

    companion object {
        fun fromKey(value: String?): AdsProfileRule? {
            val v = value?.trim()?.lowercase() ?: return null
            val code = v.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()
            return entries.firstOrNull { if (code != null) it.code == code else it.key == v }
        }
    }
}

/** Một dòng trên màn Debug Config: tên key, giá trị thô hiện tại, và gợi ý định dạng. */
data class DebugConfigEntry(val key: String, val value: String, val hint: String)

/**
 * Cấu hình quảng cáo và luồng mở app từ Firebase Remote Config - 3 parameter JSON:
 * - `ad_settings`: cấu hình chung (`ads_profile`, khoảng cách inter, nút Language / Onboarding...);
 * - `ad_placements`: `{ "full": {...}, "basic": {...} }` - xem [AdPlacementsDocument];
 * - `ad_ids`: `{ ad_unit: [id_1, id_2, id_3] }` - xem [AdIdsDocument].
 * - `ad_templates` (tuỳ chọn): mẫu native tuỳ biến - màu / hình dạng đè lên mẫu có sẵn, xem [AdTemplatesDocument].
 * Cũng nhận một parameter `ad_config` chứa cả 3 phần (`{ "ad_settings": {...}, "ad_placements": {...}, "ad_ids": {...} }`,
 * đúng file "Tải tất cả" của sheet); parameter riêng nào có giá trị thì thắng phần tương ứng trong `ad_config`.
 * Chỉ ghi chỗ khác mặc định; mặc định nằm ở [AdKey.fullDefault] và các getter dưới đây. Thời gian tính bằng ms.
 *
 * Bản debug cho override ở màn Debug Config (thắng Firebase): cả parameter (`ad_settings`, `ad_placements`, `ad_ids`,
 * `ad_config`), một setting (tên key, vd `ads_profile`), một placement của bộ full (tên placement, vd `inter_home`)
 * hoặc bộ basic (`basic.inter_home`). Giá trị placement: JSON object, `true` / `false` (= enable), hoặc tên format.
 * Tài liệu: `docs/remote-config-v3/README.md`.
 */
object RemoteConfig {
    // Dùng getter, không phải field: Firebase tự cache instance của nó, giữ instance trong object này sẽ bị lint báo StaticFieldLeak.
    private val remoteConfig get() = Firebase.remoteConfig
    private lateinit var prefs: AppPreferences

    private fun debugOverride(key: String): String? =
        if (BuildConfig.DEBUG) DebugOverrides.get(key)?.takeIf { it.isNotBlank() } else null

    /** Chuỗi JSON của một phần ([PARAM_SETTINGS] / [PARAM_PLACEMENTS] / [PARAM_IDS]): parameter riêng, không thì lấy trong `ad_config`. */
    private fun rawSection(name: String): String =
        debugOverride(name)
            ?: remoteConfig.getString(name).takeIf { it.isNotBlank() }
            ?: parseObject(debugOverride(PARAM_BUNDLE) ?: remoteConfig.getString(PARAM_BUNDLE)).optJSONObject(name)?.toString()
            ?: ""

    // Parse lại một phần chỉ khi chuỗi thô của nó đổi (sau khi fetch, hoặc khi đổi override).
    private var settingsCache: Pair<String, JSONObject> = "" to JSONObject()
    private var placementsCache: Pair<String, AdPlacementsDocument> = "" to AdPlacementsDocument("")
    private var idsCache: Pair<String, AdIdsDocument> = "" to AdIdsDocument("")
    private var templatesCache: Pair<String, AdTemplatesDocument> = "" to AdTemplatesDocument("")

    /** Parameter `ad_templates`: mẫu native tuỳ biến / style đè lên mẫu có sẵn - xem [AdTemplatesDocument]. */
    @Synchronized
    fun templates(): AdTemplatesDocument {
        val raw = rawSection(PARAM_TEMPLATES)
        if (raw != templatesCache.first) templatesCache = raw to AdTemplatesDocument(raw)
        return templatesCache.second
    }

    @Synchronized
    private fun settings(): JSONObject {
        val raw = rawSection(PARAM_SETTINGS)
        if (raw != settingsCache.first) settingsCache = raw to parseObject(raw)
        return settingsCache.second
    }

    @Synchronized
    private fun placements(): AdPlacementsDocument {
        val raw = rawSection(PARAM_PLACEMENTS)
        if (raw != placementsCache.first) placementsCache = raw to AdPlacementsDocument(raw)
        return placementsCache.second
    }

    @Synchronized
    private fun idsDocument(): AdIdsDocument {
        val raw = rawSection(PARAM_IDS)
        if (raw != idsCache.first) idsCache = raw to AdIdsDocument(raw)
        return idsCache.second
    }

    /** Một giá trị trong `ad_settings` dạng chuỗi (override debug nếu có), null nếu không đặt. */
    private fun setting(name: String): String? =
        debugOverride(name) ?: settings().opt(name)?.takeIf { it != JSONObject.NULL }?.toString()

    private val _isReady = MutableStateFlow(false)

    /** Là true sau khi [fetchAndActivate] hoàn tất (thành công hay thất bại). */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * Seed các parameter bằng bản đã lưu từ lần fetch thành công gần nhất, để ngay khi app vừa mở các getter đã trả
     * giá trị tốt gần nhất thay vì mặc định cứng, trong lúc fetch còn chạy.
     */
    fun initialize(context: Context) {
        prefs = AppPreferences.getInstance(context)
        if (BuildConfig.DEBUG) DebugOverrides.init(context)
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            },
        )
        remoteConfig.setDefaultsAsync(ALL_PARAMS.associateWith { prefs.getCachedString(CACHE_PREFIX + it, "") })
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            // Cache giá trị thô của Firebase - không qua getter, vì getter đã tính cả override debug.
            ALL_PARAMS.forEach { prefs.putCachedString(CACHE_PREFIX + it, remoteConfig.getString(it)) }
            _isReady.value = true
        }
    }

    /** `ads_profile`: ai dùng bộ full. Xem [com.namstd.androidskillhub.core.ads.resolveAdProfile]. */
    val adsProfileRule: AdsProfileRule
        get() = AdsProfileRule.fromKey(setting("ads_profile")) ?: AdsProfileRule.FULL_FOR_ALL

    /**
     * Cấu hình placement [key] trong bộ [set]. Giá trị thô - code quảng cáo đọc qua
     * [com.namstd.androidskillhub.core.ads.AdPolicy.config], nơi chọn bộ theo user.
     */
    fun placement(set: AdSet, key: AdKey): PlacementConfig {
        val overrideKey = if (set == AdSet.FULL) key.key else "basic.${key.key}"
        val override = debugOverride(overrideKey) ?: return placements().placement(set, key)
        val base = if (set == AdSet.FULL) key.fullDefault else key.fullDefault.copy(enable = true, step = 1)
        val text = override.trim()
        return when {
            text.startsWith("{") -> base.mergedWith(parseObject(text))
            text.equals("true", true) -> base.copy(enable = true)
            text.equals("false", true) || text.equals("off", true) -> base.copy(enable = false)
            else -> PlacementFormat.fromKey(text)?.let { base.copy(enable = true, format = it) } ?: base
        }
    }

    /** ID của ad unit [unitName] (`ad_ids`) theo thứ tự thử; rỗng nếu chưa đặt. */
    fun unitIds(unitName: String): List<String> = idsDocument().ids(unitName)

    /** Khoảng cách inter của bộ full (`full_inter_gap_ms`, `full_inter_gap_ignore`). */
    val fullInterDelay: InterDelayConfig
        get() = InterDelayConfig(
            gapMs = long("full_inter_gap_ms", 0L),
            ignore = list("full_inter_gap_ignore").toSet(),
        )

    /** Khoảng cách inter của bộ basic (`basic_inter_gap_ms`), mặc định 90 giây. */
    val basicInterDelay: InterDelayConfig
        get() = InterDelayConfig(gapMs = long("basic_inter_gap_ms", 90_000L), ignore = emptySet())

    /** Nút xác nhận màn Language: `language_next_style` (icon / text), `language_show_loading`. */
    val languageSetup: LanguageSetup
        get() = LanguageSetup(
            nextStyle = if (setting("language_next_style")?.trim()?.lowercase() == "icon") 1 else 2,
            loading = bool("language_show_loading", false),
        )

    /** Chờ (ms) trước khi nút xác nhận màn Language hiện ra (`language_next_delay_ms`). */
    val languageDelayMillis: Long
        get() = long("language_next_delay_ms", 0L)

    /** Cho vuốt chuyển trang Onboarding (`onboarding_swipe`). */
    val onboardingSwipe: Boolean
        get() = bool("onboarding_swipe", true)

    /**
     * Trang nội dung thứ [page] (từ 1) của Onboarding: `onboarding_next_style` (icon / text),
     * `onboarding_next_position` (bottom / right), `onboarding_next_delay_ms`, `onboarding_show_dots` áp cho mọi
     * trang; `onboarding_pages` (tuỳ chọn) đè riêng một trang, vd `{ "4": { "next_style": "text" } }`.
     */
    fun onboardingSetup(page: Int): OnboardingSetup {
        val perPage = parseObject(setting("onboarding_pages").orEmpty()).optJSONObject(page.toString())
        fun pick(field: String): String? =
            perPage?.opt(field)?.takeIf { it != JSONObject.NULL }?.toString() ?: setting("onboarding_$field")
        return OnboardingSetup(
            nextStyle = if (pick("next_style")?.trim()?.lowercase() == "text") 2 else 1,
            // "right" chưa có layout riêng: hiện vẫn đặt nút Next dưới cụm chấm như "bottom".
            indicatorPosition = 1,
            delayMs = pick("next_delay_ms")?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            showDots = pick("show_dots")?.let(::parseBoolean) ?: true,
        )
    }

    /**
     * Placement inter bù khi Splash không kịp hiện quảng cáo (`splash_fallback`, vd `inter_language`). Trống / không
     * khớp = tắt. Code quảng cáo đọc qua [com.namstd.androidskillhub.core.ads.AdPolicy.splashFallback].
     */
    val splashFallback: InterstitialPlacement?
        get() = setting("splash_fallback")?.trim().let { key ->
            InterstitialPlacement.entries.firstOrNull { it.key == key && it != InterstitialPlacement.SPLASH }
        }

    /** Nút trên trang native toàn màn hình trong Onboarding (`onboarding_full_close_button`): true = icon X, false = Next. */
    val onboardingFullCloseWithX: Boolean
        get() = setting("onboarding_full_close_button")?.trim()?.lowercase() == "x"

    /** [DEBUG] Các dòng cho màn Debug Config, mỗi dòng hiện giá trị đang có hiệu lực. */
    fun debugEntries(): List<DebugConfigEntry> {
        val doc = placements()
        return listOf(
            DebugConfigEntry(PARAM_BUNDLE, debugOverride(PARAM_BUNDLE) ?: remoteConfig.getString(PARAM_BUNDLE), BUNDLE_HINT),
            DebugConfigEntry(PARAM_SETTINGS, rawSection(PARAM_SETTINGS), "JSON cả parameter ad_settings"),
            DebugConfigEntry(PARAM_PLACEMENTS, rawSection(PARAM_PLACEMENTS), "JSON cả parameter ad_placements {\"full\":{…},\"basic\":{…}}"),
            DebugConfigEntry(PARAM_IDS, rawSection(PARAM_IDS), "JSON cả parameter ad_ids {\"aoa_splash\":[\"id\",…]}"),
            DebugConfigEntry(PARAM_TEMPLATES, rawSection(PARAM_TEMPLATES), "JSON ad_templates {\"cta_top_purple\":{\"base\":\"Big5\",\"cta_bg\":\"#7B2FA6\"}}"),
        ) + SETTING_HINTS.map { (name, hint) -> DebugConfigEntry(name, setting(name).orEmpty(), hint) } +
            AdKey.entries.map {
                DebugConfigEntry(it.key, debugOverride(it.key) ?: doc.placementJson(AdSet.FULL, it), "full · $PLACEMENT_HINT")
            } +
            AdKey.entries.map {
                val key = "basic.${it.key}"
                DebugConfigEntry(key, debugOverride(key) ?: doc.placementJson(AdSet.BASIC, it), "basic · trống = tắt · $PLACEMENT_HINT")
            }
    }

    private val SETTING_HINTS: List<Pair<String, String>> = listOf(
        "ads_profile" to "0 tắt · 1 mọi user full · 2 user marketing full, còn lại basic · 3 máy test ad basic, còn lại full",
        "splash_fallback" to "Splash không kịp hiện QC thì inter này bù, vd inter_language (trống = tắt)",
        "language_next_style" to "icon / text",
        "language_next_delay_ms" to "[ms] chờ trước khi hiện nút Language",
        "language_show_loading" to "true / false",
        "onboarding_swipe" to "true / false",
        "onboarding_next_style" to "icon / text (mọi trang)",
        "onboarding_next_position" to "bottom / right",
        "onboarding_next_delay_ms" to "[ms] chờ trước khi hiện Next",
        "onboarding_show_dots" to "true / false",
        "onboarding_pages" to "{\"4\":{\"next_style\":\"text\"}} - đè riêng từng trang",
        "onboarding_full_close_button" to "x / next",
        "full_inter_gap_ms" to "[ms] khoảng cách inter bộ full",
        "full_inter_gap_ignore" to "[\"ads_splash\",\"inter_language\"] hoặc ads_splash,inter_language",
        "basic_inter_gap_ms" to "[ms] khoảng cách inter bộ basic (mặc định 90000)",
    )

    private const val BUNDLE_HINT =
        "Cả 3 phần trong 1 JSON {\"ad_settings\":{…},\"ad_placements\":{…},\"ad_ids\":{…}}. Các dòng bên dưới thắng phần tương ứng."
    private const val PLACEMENT_HINT = "JSON {\"enable\":true,\"format\":\"inter\",\"wait_ms\":3000}, true/false, hoặc tên format"

    private fun long(name: String, default: Long): Long =
        setting(name)?.trim()?.toDoubleOrNull()?.toLong()?.coerceAtLeast(0L) ?: default

    private fun bool(name: String, default: Boolean): Boolean = setting(name)?.let(::parseBoolean) ?: default

    /** Mảng JSON `["a","b"]` hoặc chuỗi `a, b` → danh sách. */
    private fun list(name: String): List<String> {
        val raw = setting(name)?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val items = if (raw.startsWith("[")) {
            try {
                org.json.JSONArray(raw).let { a -> (0 until a.length()).map { a.optString(it) } }
            } catch (e: Exception) {
                emptyList()
            }
        } else raw.split(",", "|")
        return items.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseBoolean(value: String): Boolean = value.trim().lowercase() in TRUE_VALUES

    private const val PARAM_SETTINGS = "ad_settings"
    private const val PARAM_PLACEMENTS = "ad_placements"
    private const val PARAM_IDS = "ad_ids"
    private const val PARAM_TEMPLATES = "ad_templates"
    private const val PARAM_BUNDLE = "ad_config"
    private val ALL_PARAMS = listOf(PARAM_SETTINGS, PARAM_PLACEMENTS, PARAM_IDS, PARAM_TEMPLATES, PARAM_BUNDLE)

    /** Tiền tố cache trong [AppPreferences] - khác `cache_ad_config` cũ để không đọc nhầm format cũ đã lưu. */
    private const val CACHE_PREFIX = "cache_v3_"
    private val TRUE_VALUES = setOf("true", "1", "t", "yes", "y", "on")
}
