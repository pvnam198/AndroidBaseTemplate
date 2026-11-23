package com.namstd.androidskillhub.core.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.core.ads.AdsAvailability
import com.namstd.androidskillhub.core.ads.InterstitialPlacement
import com.namstd.androidskillhub.core.ads.NetworkRegain
import com.namstd.androidskillhub.core.ads.logInit
import com.namstd.androidskillhub.core.ads.retryDelay
import com.namstd.androidskillhub.core.ads.template.AdTemplatesDocument
import com.namstd.androidskillhub.core.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Khoảng cách tối thiểu giữa 2 lần hiện interstitial (ms) + các placement được miễn. */
data class InterDelayConfig(val gapMs: Long, val ignore: Set<String>)

/** Khoảng cách App Open ↔ App Open và App Open ↔ inter của một bộ quảng cáo. */
data class OpenGapConfig(val openGapMs: Long, val openInterGapMs: Long)

/** Nút xác nhận màn Language. nextStyle: 1 = icon dấu tích, 2 = nút chữ. */
data class LanguageSetup(val nextStyle: Int, val loading: Boolean)

/**
 * Một trang nội dung Onboarding. nextStyle: 1 = nút, 2 = chữ "Next". indicatorPosition: 1 = chấm phía trên nút Next
 * (Next ở dưới), 0 = chấm phía dưới. delayMs: chờ trước khi hiện Next. showDots: hiện chấm tròn.
 */
data class OnboardingSetup(val nextStyle: Int, val indicatorPosition: Int, val delayMs: Long, val showDots: Boolean)

/**
 * `ads_profile`: user nào dùng bộ full, còn lại dùng bộ basic. Trên web / Firebase ghi số [code]:
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
 * Cấu hình quảng cáo và luồng mở app: nguyên file JSON xuất từ web "Cấu hình quảng cáo" (xem [AdConfigDocument]):
 * `ads_profile`, `settings` (dùng chung), `profiles.full` / `profiles.basic` (settings + placements, `ad_ids` nằm trong
 * từng placement), `templates` (template native riêng). File nằm trong parameter Remote Config của đúng bản này -
 * [VERSION_KEY], vd `ad_ver10` (xem `docs/BASE_GUIDE.md` mục 5.3). Không có key đó trên Firebase / hỏng = mọi placement
 * tắt. Thời gian tính bằng ms.
 *
 * Bản debug cho override ở màn Debug Config (thắng Firebase, xem [debugEntries]): cả file ([VERSION_KEY]), `ads_profile`,
 * một key trong `settings` gốc (vd `splash_timeout_ms`), `settings` của một bộ (`full.settings` / `basic.settings`),
 * một placement bộ full (tên placement, vd `inter_home`) hoặc bộ basic (`basic.inter_home`). Giá trị placement: JSON
 * object (thay cả placement), `true` / `false` (= enable), hoặc tên ad_type.
 */
object RemoteConfig {
    // Dùng getter, không phải field: Firebase tự cache instance của nó, giữ instance trong object này sẽ bị lint báo StaticFieldLeak.
    private val remoteConfig get() = Firebase.remoteConfig
    private lateinit var prefs: AppPreferences

    private fun debugOverride(key: String): String? =
        if (BuildConfig.DEBUG) DebugOverrides.get(key)?.takeIf { it.isNotBlank() } else null

    /**
     * Key Remote Config chứa file cấu hình của bản này: `ad_ver` + các chữ số của versionName (1.0 → `ad_ver10`,
     * 1.1.2 → `ad_ver112`). **Mỗi bản release đổi key này** (và thêm key đó trên Firebase). Cache theo tên key nên bản
     * mới không dùng cache của bản trước.
     */
    private const val VERSION_KEY = "ad_ver10"

    /** Giá trị thô của parameter [VERSION_KEY] trên Firebase (hoặc override cả file ở bản debug). */
    private fun remoteRaw(): String = debugOverride(VERSION_KEY) ?: remoteConfig.getString(VERSION_KEY)

    // Parse lại chỉ khi chuỗi thô (hoặc override debug) đổi.
    private var documentCache: Pair<String, AdConfigDocument> = "" to AdConfigDocument("")
    private var templatesCache: Pair<String, AdTemplatesDocument> = "" to AdTemplatesDocument("")

    @Synchronized
    private fun document(): AdConfigDocument {
        val raw = remoteRaw()
        val cacheKey = if (BuildConfig.DEBUG) "${DebugOverrides.version}|$raw" else raw
        if (cacheKey != documentCache.first) {
            documentCache = cacheKey to AdConfigDocument(if (BuildConfig.DEBUG) withDebugOverrides(raw) else raw)
        }
        return documentCache.second
    }

    /** [DEBUG] Áp các override từng phần lên file cấu hình [raw]. */
    private fun withDebugOverrides(raw: String): String {
        // Bỏ override cả file của mọi bản (`ad_ver*`), kể cả key của bản trước còn sót lại sau khi đổi VERSION_KEY.
        val keys = DebugOverrides.keys().filterNot { it.startsWith(VERSION_KEY_PREFIX) }
        if (keys.isEmpty()) return raw
        val root = parseObject(raw)
        fun child(parent: JSONObject, name: String): JSONObject =
            parent.optJSONObject(name) ?: JSONObject().also { parent.put(name, it) }
        val settings = child(root, "settings")
        keys.forEach { key ->
            val text = DebugOverrides.get(key)?.trim().orEmpty()
            if (text.isEmpty()) return@forEach
            when {
                key == "ads_profile" -> root.put(key, jsonValue(text))
                key == "full.settings" || key == "basic.settings" ->
                    child(child(root, "profiles"), key.substringBefore('.')).put("settings", parseObject(text))
                AdKey.fromKey(key.removePrefix("basic.")) != null -> {
                    val set = if (key.startsWith("basic.")) AdSet.BASIC else AdSet.FULL
                    val name = key.removePrefix("basic.")
                    val placements = child(child(child(root, "profiles"), set.key), "placements")
                    val current = placements.optJSONObject(name) ?: JSONObject()
                    placements.put(name, placementOverride(current, text))
                }
                else -> settings.put(key, jsonValue(text))
            }
        }
        return root.toString()
    }

    /** Override một placement: JSON object thay cả placement; `true` / `false` / `off` = enable; còn lại = tên ad_type. */
    private fun placementOverride(current: JSONObject, text: String): JSONObject = when {
        text.startsWith("{") -> parseObject(text)
        text.equals("true", true) -> JSONObject(current.toString()).put("enable", true)
        text.equals("false", true) || text.equals("off", true) -> JSONObject(current.toString()).put("enable", false)
        AdType.fromKey(text) != null -> JSONObject(current.toString()).put("enable", true).put("ad_type", text.lowercase())
        else -> current
    }

    /** Chuỗi gõ ở màn Debug Config → giá trị JSON (số, true/false, mảng, object), không parse được thì giữ chuỗi. */
    private fun jsonValue(text: String): Any = try {
        JSONTokener(text).nextValue().takeIf { it !is String } ?: text
    } catch (e: Exception) {
        text
    }

    /** `templates` gốc file: mẫu native tuỳ biến / style đè lên mẫu có sẵn - xem [AdTemplatesDocument]. */
    @Synchronized
    fun templates(): AdTemplatesDocument {
        val raw = document().templatesJson.toString()
        if (raw != templatesCache.first) templatesCache = raw to AdTemplatesDocument(raw)
        return templatesCache.second
    }

    /** Một giá trị trong `settings` gốc dạng chuỗi, null nếu không đặt. */
    private fun setting(name: String): String? = document().settings.stringOrNull(name)

    private val _isReady = MutableStateFlow(false)

    /** Là true sau lần fetch đầu tiên hoàn tất (thành công hay thất bại) - lỗi thì vẫn thử lại ngầm, xem [fetch]. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * Seed parameter bằng bản đã lưu từ lần fetch thành công gần nhất, để ngay khi app vừa mở các getter đã trả
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
        remoteConfig.setDefaultsAsync(mapOf(VERSION_KEY to prefs.getCachedString(CACHE_PREFIX + VERSION_KEY, "")))
        fetch(context.applicationContext, attempt = 0)
    }

    /**
     * Đã có cấu hình quảng cáo chưa (fetch được, có cache lần trước, hoặc override cả file ở màn Debug config) - khác
     * với có config nhưng placement tắt. Không có config mặc định nhúng trong app.
     */
    fun hasAdConfig(): Boolean = remoteRaw().isNotBlank()

    private val main = Handler(Looper.getMainLooper())

    /**
     * Fetch + activate. Lỗi (mạng chập chờn) thì vẫn báo [isReady] để Splash không chờ, rồi thử lại sau 2s / 4s / 8s và
     * khi mạng có lại ([NetworkRegain]) - không có config mặc định nhúng trong app, nên lần cài đầu chỉ có quảng cáo khi
     * fetch được. Fetch thành công mà file cấu hình đổi thì báo [AdsAvailability] để màn đang mở load quảng cáo.
     */
    private fun fetch(context: Context, attempt: Int) {
        val before = remoteRaw()
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            // Cache giá trị thô của Firebase - không qua getter, vì getter đã tính cả override debug.
            prefs.putCachedString(CACHE_PREFIX + VERSION_KEY, remoteConfig.getString(VERSION_KEY))
            _isReady.value = true
            if (BuildConfig.DEBUG) {
                it.exception?.let { e -> android.util.Log.w(LOG_TAG, "fetch lỗi: $e", e) }
                logConfig(fetchOk = it.isSuccessful)
            }
            if (it.isSuccessful) {
                if (attempt > 0) logInit("rc ok sau ${attempt + 1} lần, có config quảng cáo: ${hasAdConfig()}")
                if (remoteRaw() != before) AdsAvailability.notifyChanged()
                return@addOnCompleteListener
            }
            val delay = retryDelay(attempt)
            logInit("rc fetch lỗi lần ${attempt + 1}" + if (delay != null) ", thử lại sau ${delay}ms" else ", hết lượt")
            if (delay != null) main.postDelayed({ fetch(context, attempt + 1) }, delay)
            // Hết lượt: chờ mạng có lại rồi chạy lại lịch thử.
            else NetworkRegain.awaitOnce(context, "rc") { fetch(context, attempt = 1) }
        }
    }

    /** `ads_profile`: ai dùng bộ full. Xem [com.namstd.androidskillhub.core.ads.resolveAdProfile]. */
    val adsProfileRule: AdsProfileRule
        get() = AdsProfileRule.fromKey(document().adsProfile) ?: AdsProfileRule.FULL_FOR_ALL

    /**
     * Cấu hình placement [key] trong bộ [set]. Giá trị thô - code quảng cáo đọc qua
     * [com.namstd.androidskillhub.core.ads.AdPolicy.config], nơi chọn bộ theo user.
     */
    fun placement(set: AdSet, key: AdKey): PlacementConfig = document().placement(set, key)

    /**
     * Khoảng cách inter của bộ [set] (`profiles.<bộ>.settings`): `inter_gap_ms` (không có: full 0, basic 90 giây) và
     * `inter_gap_ignore` (placement không bị chặn bởi khoảng cách).
     */
    fun interDelay(set: AdSet): InterDelayConfig {
        val json = document().profileSettings(set)
        return InterDelayConfig(
            gapMs = json.longOrNull("inter_gap_ms") ?: if (set == AdSet.BASIC) 90_000L else 0L,
            ignore = parseList(json.stringOrNull("inter_gap_ignore")).toSet(),
        )
    }

    /**
     * Khoảng cách App Open của bộ [set] (`profiles.<bộ>.settings`). Mặc định (full / basic): 30 s / 90 s giữa hai App Open,
     * 15 s / 30 s giữa App Open và inter - để user vừa xem / bấm một inter rồi quay lại app không gặp App Open ngay, và
     * chuyển app qua lại nhanh không bị App Open liên tục. Đặt 0 để bỏ giới hạn.
     * - `openGapMs`: giữa hai App Open lúc quay lại app (`open_gap_ms`);
     * - `openInterGapMs`: giữa App Open và inter, cả hai chiều (`open_inter_gap_ms`) - inter vừa hiện thì App Open lúc
     *   quay lại app phải chờ, và ngược lại.
     */
    fun openGaps(set: AdSet): OpenGapConfig {
        val json = document().profileSettings(set)
        val basic = set == AdSet.BASIC
        return OpenGapConfig(
            openGapMs = json.longOrNull("open_gap_ms") ?: if (basic) 90_000L else 30_000L,
            openInterGapMs = json.longOrNull("open_inter_gap_ms") ?: if (basic) 30_000L else 15_000L,
        )
    }

    /**
     * Splash chờ tối thiểu (`splash_min_ms`, mặc định 3 s, 0–10 s) / tối đa chờ quảng cáo (`splash_timeout_ms`, mặc
     * định 8 s, min–30 s). Splash chạy trước khi fetch xong nên dùng giá trị của lần fetch trước (lần mở app đầu tiên =
     * mặc định).
     */
    val splashMinMs: Long
        get() = long("splash_min_ms", 3_000L).coerceIn(0L, 10_000L)

    val splashMaxMs: Long
        get() = long("splash_timeout_ms", 8_000L).coerceIn(splashMinMs, 30_000L)

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
     * `onboarding_indicator_position` (above / below), `onboarding_next_delay_ms`, `onboarding_show_dots` áp cho mọi
     * trang; `onboarding_pages` (tuỳ chọn) đè riêng một trang, vd `{ "2": { "next_delay_ms": 3000 } }`.
     */
    fun onboardingSetup(page: Int): OnboardingSetup {
        val perPage = document().settings.optJSONObject("onboarding_pages")?.optJSONObject(page.toString())
        fun pick(field: String): String? = perPage?.stringOrNull(field) ?: setting("onboarding_$field")
        return OnboardingSetup(
            nextStyle = if (pick("next_style")?.trim()?.lowercase() == "text") 2 else 1,
            indicatorPosition = if (pick("indicator_position")?.trim()?.lowercase() == "below") 0 else 1,
            delayMs = pick("next_delay_ms")?.trim()?.toDoubleOrNull()?.toLong()?.coerceAtLeast(0L) ?: 0L,
            showDots = pick("show_dots")?.let(::parseBoolean) ?: true,
        )
    }

    /**
     * Placement inter bù khi Splash không kịp hiện quảng cáo (`splash_fallback`, mảng tên placement inter, vd
     * `["inter_language"]`): placement đầu tiên trong danh sách mà app có. Trống / không khớp = tắt. Code quảng cáo đọc
     * qua [com.namstd.androidskillhub.core.ads.AdPolicy.splashFallback].
     */
    val splashFallback: InterstitialPlacement?
        get() = parseList(setting("splash_fallback")).firstNotNullOfOrNull { key ->
            InterstitialPlacement.entries.firstOrNull { it.key == key && it != InterstitialPlacement.SPLASH }
        }

    /** Nút trên trang native toàn màn hình trong Onboarding (`onboarding_full_close_button`): true = icon X, false = Next. */
    val onboardingFullCloseWithX: Boolean
        get() = setting("onboarding_full_close_button")?.trim()?.lowercase() == "x"

    /** [DEBUG] Các dòng cho màn Debug Config, mỗi dòng hiện giá trị đang có hiệu lực. */
    fun debugEntries(): List<DebugConfigEntry> {
        val doc = document()
        return listOf(
            DebugConfigEntry(VERSION_KEY, remoteRaw(), "Cả file JSON xuất từ web (thay parameter $VERSION_KEY). Các dòng bên dưới đè lên file này."),
            DebugConfigEntry("ads_profile", doc.adsProfile.orEmpty(), ADS_PROFILE_HINT),
        ) + SETTING_HINTS.map { (name, hint) -> DebugConfigEntry(name, setting(name).orEmpty(), hint) } +
            AdSet.entries.map { set ->
                DebugConfigEntry("${set.key}.settings", doc.profileSettings(set).toString(), "${set.key} · $PROFILE_SETTINGS_HINT")
            } +
            AdKey.entries.map {
                DebugConfigEntry(it.key, doc.placementJson(AdSet.FULL, it), "full · $PLACEMENT_HINT")
            } +
            AdKey.entries.map {
                DebugConfigEntry("basic.${it.key}", doc.placementJson(AdSet.BASIC, it), "basic · $PLACEMENT_HINT")
            }
    }

    private const val ADS_PROFILE_HINT =
        "0 tắt · 1 mọi user full · 2 user marketing full, còn lại basic · 3 máy test ad basic, còn lại full"
    private const val PROFILE_SETTINGS_HINT =
        "JSON {\"inter_gap_ms\":10000,\"inter_gap_ignore\":[\"ads_splash\"],\"open_gap_ms\":30000,\"open_inter_gap_ms\":15000}"
    private const val PLACEMENT_HINT =
        "JSON {\"enable\":true,\"ad_type\":\"inter\",\"ad_ids\":{\"inter\":[\"id\"]}}, true/false, hoặc tên ad_type"

    /** Các key trong `settings` gốc hiện trên màn Debug Config. */
    private val SETTING_HINTS: List<Pair<String, String>> = listOf(
        "splash_timeout_ms" to "[ms] Splash chờ quảng cáo tối đa (mặc định 8000)",
        "splash_min_ms" to "[ms] Splash chờ tối thiểu (mặc định 3000)",
        "splash_fallback" to "[\"inter_language\"] - Splash không kịp hiện QC thì inter này bù (trống = tắt)",
        "language_next_style" to "icon / text",
        "language_next_delay_ms" to "[ms] chờ trước khi hiện nút Language",
        "language_show_loading" to "true / false",
        "onboarding_swipe" to "true / false",
        "onboarding_next_style" to "icon / text (mọi trang)",
        "onboarding_indicator_position" to "above / below",
        "onboarding_next_delay_ms" to "[ms] chờ trước khi hiện Next",
        "onboarding_show_dots" to "true / false",
        "onboarding_pages" to "{\"2\":{\"next_delay_ms\":3000}} - đè riêng từng trang",
        "onboarding_full_close_button" to "x / next",
    )

    /**
     * [DEBUG] In ra logcat mọi giá trị app đọc được từ cấu hình: nguồn, gốc file, `settings` (thô + giá trị app hiểu),
     * từng bộ (settings + mọi placement app dùng, kèm placement có trong file mà app không biết), `templates`.
     * Xem: `adb logcat -s AdsRC`.
     */
    private fun logConfig(fetchOk: Boolean) {
        fun log(message: String) = android.util.Log.d(LOG_TAG, message)
        val raw = remoteRaw()
        val doc = document()
        log("===== Remote Config: fetch ok=$fetchOk · nguồn: ${if (debugOverride(VERSION_KEY) != null) "override Debug config" else "Firebase $VERSION_KEY (${remoteConfig.getValue(VERSION_KEY).sourceName()})"} · ${raw.length} ký tự")
        if (raw.isBlank()) {
            log("cấu hình trống → mọi placement tắt")
            return
        }
        log("config_version=${doc.configVersion} · ads_profile=${doc.adsProfile} → $adsProfileRule")
        doc.settings.keys().asSequence().sorted().forEach { log("settings.$it = ${doc.settings.opt(it)}") }
        log("→ splashFallback=$splashFallback · onboardingSwipe=$onboardingSwipe · onboardingFullCloseWithX=$onboardingFullCloseWithX")
        log("→ languageSetup=$languageSetup · languageDelayMillis=$languageDelayMillis")
        (1..4).forEach { log("→ onboardingSetup($it) = ${onboardingSetup(it)}") }
        DebugOverrides.keys().takeIf { it.isNotEmpty() }?.let { log("override Debug config đang bật: ${it.sorted()}") }
        log("(placement không có ID: không có quảng cáo · bản debug: unit có ID sẽ dùng test ID của Google thay cho ID dưới đây)")
        for (set in AdSet.entries) {
            log("----- ${set.key}: settings=${doc.profileSettings(set)} → ${interDelay(set)} · ${openGaps(set)}")
            AdKey.entries.forEach { key ->
                val c = doc.placement(set, key)
                log("${set.key}.${key.key}: " + if (c == PlacementConfig.OFF) "không có trong file (tắt)" else describe(c))
            }
            (doc.placementNames(set) - AdKey.entries.map { it.key }.toSet()).sorted().forEach {
                log("${set.key}.$it: có trong file nhưng app không dùng")
            }
        }
        log("templates: ${doc.templatesJson}")
        log("===== hết Remote Config")
    }

    private fun describe(c: PlacementConfig): String = buildList {
        add(if (c.enable) "BẬT" else "tắt")
        add("type=${c.type?.key}")
        add("ad_type=${c.adType?.key}")
        c.adIds.forEach { (type, ids) -> add("$type=$ids") }
        if (c.template.isNotEmpty()) add("template=${c.template}")
        if (c.pages.isNotEmpty()) add("pages=${c.pages}")
        if (c.step != 1) add("step=${c.step}")
        if (c.waitMs > 0) add("wait_ms=${c.waitMs}")
        if (c.refreshMs > 0) add("refresh_ms=${c.refreshMs}")
        if (c.openAdOnClicks.isNotEmpty()) add("open_ad_on_clicks=${c.openAdOnClicks}")
        if (c.adType == AdType.INTER_THEN_NATIVE) add("fallback=${c.fallback.key}")
        if (c.customUi) add("custom_ui")
    }.joinToString(" ")

    private fun com.google.firebase.remoteconfig.FirebaseRemoteConfigValue.sourceName(): String = when (source) {
        com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_REMOTE -> "remote"
        com.google.firebase.remoteconfig.FirebaseRemoteConfig.VALUE_SOURCE_DEFAULT -> "cache lần fetch trước"
        else -> "không có giá trị"
    }

    private fun long(name: String, default: Long): Long = document().settings.longOrNull(name) ?: default

    private fun bool(name: String, default: Boolean): Boolean = setting(name)?.let(::parseBoolean) ?: default

    /** Mảng JSON `["a","b"]` hoặc chuỗi `a, b` → danh sách. */
    private fun parseList(value: String?): List<String> {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        val items = if (raw.startsWith("[")) {
            try {
                JSONArray(raw).let { a -> (0 until a.length()).map { a.optString(it) } }
            } catch (e: Exception) {
                emptyList()
            }
        } else raw.split(",", "|")
        return items.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun JSONObject.stringOrNull(name: String): String? = opt(name)?.takeIf { it != JSONObject.NULL }?.toString()

    private fun JSONObject.longOrNull(name: String): Long? =
        stringOrNull(name)?.trim()?.toDoubleOrNull()?.toLong()?.coerceAtLeast(0L)

    private fun parseBoolean(value: String): Boolean = value.trim().lowercase() in TRUE_VALUES

    private const val VERSION_KEY_PREFIX = "ad_ver"

    /** Tiền tố cache trong [AppPreferences] - khác `cache_v3_` (format sheet cũ) để không đọc nhầm format cũ đã lưu. */
    private const val CACHE_PREFIX = "cache_v4_"
    private const val LOG_TAG = "AdsRC"
    private val TRUE_VALUES = setOf("true", "1", "t", "yes", "y", "on")
}
