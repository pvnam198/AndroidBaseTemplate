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

/** Giá trị đã parse của `inter_delay_time`: gap giữa các interstitial (ms) + các loại inter được miễn gap. */
data class InterDelayConfig(val gapMs: Long, val ignore: Set<String>)

/** Giá trị đã parse của `language_setup` (`next=1,loading=true`). nextStyle: 1=icon, 2=nút chữ. */
data class LanguageSetup(val nextStyle: Int, val loading: Boolean)

/**
 * Giá trị đã parse của một `onboarding1_setup` … `onboarding4_setup` (`next=1,ds=1,delay=0,dot=1`), mỗi key cho một trang nội dung.
 * nextStyle: 1=nút (button), 2=chữ không nền. indicatorPosition: 1=indicator trên nút Next, 0=dưới. delayMs: chờ trước khi hiện Next (ms).
 * showDots: hiện chấm tròn.
 */
data class OnboardingSetup(val nextStyle: Int, val indicatorPosition: Int, val delayMs: Long, val showDots: Boolean)

/** Một dòng trên màn Debug Config: tên key, giá trị thô hiện tại, và gợi ý định dạng. */
data class DebugConfigEntry(val key: String, val value: String, val hint: String)

/**
 * Cấu hình quảng cáo và luồng mở app từ Firebase Remote Config, theo sheet "Ad Script": toàn bộ nằm trong **một key
 * JSON `ad_config`** - object `settings` (cấu hình chung) + mỗi vị trí quảng cáo một object (xem [AdConfigDocument],
 * [PlacementConfig]). Thời gian tính bằng mili-giây.
 *
 * Bản debug cho override từng giá trị (tên trong `settings`, hoặc key vị trí) ở màn Debug Config - override thắng
 * `ad_config`. Tài liệu đầy đủ ở `docs/remote-config.md`; giữ tài liệu đó đồng bộ với file này.
 */
object RemoteConfig {
    // Dùng getter, không phải field: Firebase tự cache instance của nó, giữ instance trong object này sẽ bị lint báo StaticFieldLeak.
    private val remoteConfig get() = Firebase.remoteConfig
    private lateinit var prefs: AppPreferences

    // DEBUG override: nếu có giá trị cục bộ (đặt ở màn Debug Config), nó thắng Firebase/default.
    private fun debugOverride(key: String): String? =
        if (BuildConfig.DEBUG) DebugOverrides.get(key)?.takeIf { it.isNotBlank() } else null

    /** Parse lại `ad_config` chỉ khi chuỗi thô đổi (vd sau khi fetch, hoặc khi đổi override). */
    private var cachedRaw: String? = null
    private var cachedDocument = AdConfigDocument("")

    @Synchronized
    private fun document(): AdConfigDocument {
        val raw = debugOverride(KEY_AD_CONFIG) ?: remoteConfig.getString(KEY_AD_CONFIG)
        if (raw != cachedRaw) {
            cachedDocument = AdConfigDocument(raw)
            cachedRaw = raw
        }
        return cachedDocument
    }

    /** Một giá trị trong `settings` (override debug nếu có), null nếu không đặt. */
    private fun setting(name: String): String? = debugOverride(name) ?: document().setting(name)

    private val _isReady = MutableStateFlow(false)

    /** Là true sau khi [fetchAndActivate] hoàn tất (thành công hay thất bại) và các flag bên dưới đã ổn định. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * Seed `ad_config` bằng bản đã lưu (trong [AppPreferences]) từ lần fetch thành công gần nhất, để ngay khi app vừa
     * mở các getter đã trả giá trị tốt gần nhất thay vì mặc định cứng, trong lúc [fetchAndActivate] còn chạy.
     */
    fun initialize(context: Context) {
        prefs = AppPreferences.getInstance(context)
        if (BuildConfig.DEBUG) DebugOverrides.init(context)
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            },
        )
        remoteConfig.setDefaultsAsync(mapOf(KEY_AD_CONFIG to prefs.getCachedString(CACHE_AD_CONFIG, "")))
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            // Cache giá trị thô của Firebase - không bao giờ cache qua getter, vì getter đã tính cả override debug.
            prefs.putCachedString(CACHE_AD_CONFIG, remoteConfig.getString(KEY_AD_CONFIG))
            _isReady.value = true
        }
    }

    /**
     * Công tắc toàn app + chọn kịch bản (`settings.ads_enable`): 0 tắt, 1 A, 2 theo nguồn user, 3 theo test ad.
     * Xem [com.namstd.androidskillhub.core.ads.resolveAdProfile].
     */
    val adsEnable: Int
        get() = setting("ads_enable")?.trim()?.toIntOrNull() ?: 1

    /**
     * Cấu hình vị trí quảng cáo [key] (object cùng tên trong `ad_config`, xem [PlacementConfig]). Giá trị thô - code
     * quảng cáo đọc qua [com.namstd.androidskillhub.core.ads.AdPolicy.config], nơi áp kịch bản BASIC / OFF.
     */
    fun placement(key: AdKey): PlacementConfig =
        debugOverride(key.key)?.let { PlacementConfig.parseOverride(it, key) } ?: document().placement(key)

    /**
     * Gap giữa các Interstitial + danh sách loại inter được MIỄN gap đó (`settings.inter_delay_time`, dạng
     * `time=10000,ignore=ads_splash|inter_language|inter_onboarding`): `time` = gap (ms), `ignore` = các
     * [com.namstd.androidskillhub.core.ads.InterstitialPlacement.key] luôn được hiện bất kể gap.
     * Giá trị thô - code quảng cáo đọc qua [com.namstd.androidskillhub.core.ads.AdPolicy.interDelay].
     */
    val interDelay: InterDelayConfig
        get() {
            var timeMs = 0L
            var ignore = emptySet<String>()
            setting("inter_delay_time").orEmpty().split(",").forEach { part ->
                val pair = part.split("=", limit = 2)
                val value = pair.getOrElse(1) { "" }.trim()
                when (pair[0].trim()) {
                    "time" -> timeMs = value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    "ignore" -> ignore = value.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
            }
            return InterDelayConfig(gapMs = timeMs, ignore = ignore)
        }

    /** Chờ (ms) trước khi nút xác nhận màn Language hiện ra (`settings.language_delay_millis`). `0` = hiện ngay. */
    val languageDelayMillis: Long
        get() = (setting("language_delay_millis")?.trim()?.toLongOrNull() ?: 800L).coerceAtLeast(0L)

    /** Cho vuốt chuyển trang ở Onboarding (`settings.onboarding_swipe`), mặc định `true`. */
    val onboardingSwipe: Boolean
        get() = setting("onboarding_swipe")?.let(::parseBoolean) ?: true

    /**
     * Nút Next màn Language (`settings.language_setup`, dạng `next=1,loading=true`): `next` = 1 icon dấu check trên
     * thanh tiêu đề / 2 nút chữ, `loading` = vòng loading trong lúc chờ [languageDelayMillis].
     */
    val languageSetup: LanguageSetup
        get() {
            val m = parseKeyValues(setting("language_setup") ?: DEFAULT_LANGUAGE_SETUP)
            return LanguageSetup(
                nextStyle = m["next"]?.toIntOrNull() ?: 2,
                loading = m["loading"]?.toBoolean() ?: false,
            )
        }

    /**
     * Cấu hình trang nội dung thứ [page] (1-4) của Onboarding (`settings.onboarding{page}_setup`, dạng `next=1,ds=1,delay=0,dot=1`):
     * `next` 1 nút button / 2 chữ "Next" không nền, `ds` 1 chấm trên nút Next / 0 dưới, `delay` ms chờ trước khi hiện
     * Next, `dot` 1 hiện chấm / 0 ẩn. Trang native toàn màn hình không tính. Trang nào không có thì dùng `onboarding1_setup`.
     */
    fun onboardingSetup(page: Int): OnboardingSetup {
        val raw = setting("onboarding${page}_setup")?.takeIf { it.isNotBlank() }
            ?: setting("onboarding1_setup")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_ONBOARDING1_SETUP
        val m = parseKeyValues(raw)
        return OnboardingSetup(
            nextStyle = m["next"]?.toIntOrNull() ?: 1,
            indicatorPosition = m["ds"]?.toIntOrNull() ?: 1,
            delayMs = m["delay"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            showDots = m["dot"]?.toIntOrNull() == 1,
        )
    }

    /**
     * Vị trí inter bù khi Splash không kịp hiện quảng cáo (`settings.splash_fallback`, vd `inter_language`): khớp
     * [com.namstd.androidskillhub.core.ads.InterstitialPlacement.key]. Trống hoặc không khớp = tắt.
     * Code quảng cáo đọc qua [com.namstd.androidskillhub.core.ads.AdPolicy.splashFallback].
     */
    val splashFallback: InterstitialPlacement?
        get() = setting("splash_fallback")?.trim().let { key ->
            InterstitialPlacement.entries.firstOrNull { it.key == key && it != InterstitialPlacement.SPLASH }
        }

    /**
     * Nút trên trang native toàn màn hình trong Onboarding (`settings.native_onboarding_use_icon_next`): `true` = icon X,
     * `false` = mũi tên Next. Nhận cả `1`/`0`.
     */
    val nativeOnboardingUseIconNext: Boolean
        get() = setting("native_onboarding_use_icon_next")?.let(::parseBoolean) ?: false

    /**
     * [DEBUG] Các dòng cho màn Debug Config: `ad_config` (cả khối), rồi từng giá trị `settings`, rồi từng vị trí -
     * mỗi dòng hiện giá trị đang có hiệu lực (override nếu có, không thì từ `ad_config`).
     */
    fun debugEntries(): List<DebugConfigEntry> {
        val doc = document()
        return listOf(
            DebugConfigEntry(KEY_AD_CONFIG, debugOverride(KEY_AD_CONFIG) ?: remoteConfig.getString(KEY_AD_CONFIG), AD_CONFIG_HINT),
        ) + SETTING_HINTS.map { (name, hint) -> DebugConfigEntry(name, setting(name).orEmpty(), hint) } +
            AdKey.entries.filter { it != AdKey.ON_RESUME }.map {
                DebugConfigEntry(it.key, debugOverride(it.key) ?: doc.placementJson(it), "$PLACEMENT_HINT · mặc định enable=${it.defaultEnable}")
            }
    }

    private val SETTING_HINTS: List<Pair<String, String>> = listOf(
        "ads_enable" to "0 tắt · 1 REMOTE · 2 marketing→REMOTE, còn lại BASIC · 3 test ad→BASIC, còn lại REMOTE",
        "language_delay_millis" to "[ms] chờ trước khi hiện nút Language",
        "splash_fallback" to "Splash không kịp hiện QC thì inter này bù, vd inter_language (trống = tắt)",
        "language_setup" to "next=1 icon/2 nút chữ,loading=true/false",
        "onboarding1_setup" to "trang nội dung 1: next=1 button/2 chữ,ds=0/1,delay=ms,dot=0 ẩn/1 hiện",
        "onboarding2_setup" to "trang nội dung 2, cùng dạng onboarding1 (trống = dùng onboarding1)",
        "onboarding3_setup" to "trang nội dung 3, cùng dạng onboarding1 (trống = dùng onboarding1)",
        "onboarding4_setup" to "trang nội dung 4, cùng dạng onboarding1 (trống = dùng onboarding1)",
        "onboarding_swipe" to "true/false (vuốt Onboarding)",
        "inter_delay_time" to "time=ms,ignore=ads_splash|inter_language|inter_onboarding",
        "native_onboarding_use_icon_next" to "true = icon X / false = Next",
        "on_resume" to "0 tắt · 1 App Open · 2 Welcome back",
        "on_resume_id" to "ID App Open khi quay lại app (trống = test id)",
    )

    private const val AD_CONFIG_HINT =
        "Cả khối JSON {\"settings\":{…},\"ads_splash\":{…},…}. Các dòng bên dưới ghi đè từng phần."
    private const val PLACEMENT_HINT =
        "số = enable, hoặc JSON {\"enable\":3,\"wait_time\":5000,\"inter\":{\"name1\":\"INTER_SPLASH\",\"id1\":\"…\"}}"

    private fun parseBoolean(value: String): Boolean = value.trim().lowercase() in TRUE_VALUES

    /** Tách chuỗi config dạng `k=v,k=v` (bỏ khoảng trắng thừa) thành map. */
    private fun parseKeyValues(raw: String): Map<String, String> =
        raw.split(",").mapNotNull { part ->
            val pair = part.split("=", limit = 2)
            if (pair.size == 2) pair[0].trim() to pair[1].trim() else null
        }.toMap()

    private const val KEY_AD_CONFIG = "ad_config"

    /** Tên cache trong [AppPreferences] - khác tên mọi key cũ để không đọc nhầm kiểu dữ liệu bản cũ đã lưu. */
    private const val CACHE_AD_CONFIG = "cache_ad_config"
    private const val DEFAULT_LANGUAGE_SETUP = "next=2,loading=false"
    private const val DEFAULT_ONBOARDING1_SETUP = "next=1,ds=1,delay=0,dot=1"
    private val TRUE_VALUES = setOf("true", "1", "t", "yes", "y", "on")
}
