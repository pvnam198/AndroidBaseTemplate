package com.namstd.androidskillhub.core.config

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.namstd.androidskillhub.BuildConfig
import com.namstd.androidskillhub.core.ads.template.NativeAdTemplate
import com.namstd.androidskillhub.core.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Giá trị đã parse của key `inter_delay_time`: gap giữa các interstitial (ms) + các loại inter được miễn gap. */
data class InterDelayConfig(val gapMs: Long, val ignore: Set<String>)

/** Giá trị đã parse của key `language_setup` (`next=1,loading=true`). nextStyle: 1=nút chữ, 2=icon. */
data class LanguageSetup(val nextStyle: Int, val loading: Boolean)

/**
 * Giá trị đã parse của key `intro1_setup` (`next=1,ds=1,delay=0,dot=1`).
 * nextStyle: 1=nút chữ, 2=icon. indicatorPosition: 1=indicator trên nút Next, 0=dưới. delayMs: chờ trước khi hiện Next (ms).
 * showDots: hiện chấm tròn.
 */
data class Intro1Setup(val nextStyle: Int, val indicatorPosition: Int, val delayMs: Long, val showDots: Boolean)

/**
 * Giá trị đã parse của key `enable`: loại quảng cáo cho từng vị trí (`0` = tắt vị trí đó).
 * ads_splash: 1 AOA/2 Inter/3 Inter+Native · inter: 1 Inter/2 Native/3 Inter+Native ·
 * banner: 1 Banner/2 BannerCollap/3 Native/4 NativeCollap · onresume: 1 app-open/2 welcome back ·
 * inter_inapp: inter ở các màn in-app (sau onboarding), cùng mã với inter.
 */
data class EnableConfig(val adsSplash: Int, val inter: Int, val banner: Int, val onresume: Int, val interInApp: Int) {
    companion object {
        val NONE = EnableConfig(adsSplash = 0, inter = 0, banner = 0, onresume = 0, interInApp = 0)
    }
}

/** Một dòng trên màn Debug Config: tên key, giá trị raw hiện tại, và gợi ý format. */
data class DebugConfigEntry(val key: String, val value: String, val hint: String)

/**
 * Các feature flag điều khiển từ xa qua Firebase Remote Config.
 *
 * Tài liệu đầy đủ (mỗi key làm gì, kiểu dữ liệu, fallback, ví dụ giá trị trên Firebase console) nằm ở
 * `docs/remote-config.md`. Thêm một key = 4 chỗ phải sửa trong file này: một hằng `KEY_*`, một dòng seed
 * trong `setDefaultsAsync`, một dòng trong `persistToCache()`, và một getter. Giữ tài liệu đó đồng bộ với file này.
 */
object RemoteConfig {
    // Dùng getter, không phải field: Firebase tự cache instance của nó, giữ instance trong object này sẽ bị lint báo StaticFieldLeak.
    private val remoteConfig get() = Firebase.remoteConfig
    private lateinit var prefs: AppPreferences

    // DEBUG override: nếu có giá trị cục bộ (đặt ở màn Debug Config), nó thắng Firebase/default.
    private fun debugOverride(key: String): String? =
        if (BuildConfig.DEBUG) DebugOverrides.get(key)?.takeIf { it.isNotBlank() } else null

    private fun rcString(key: String): String = debugOverride(key) ?: remoteConfig.getString(key)
    private fun rcLong(key: String): Long = debugOverride(key)?.toLongOrNull() ?: remoteConfig.getLong(key)
    private fun rcBoolean(key: String): Boolean = debugOverride(key)?.toBooleanStrictOrNull() ?: remoteConfig.getBoolean(key)

    private val _isReady = MutableStateFlow(false)

    /** True sau khi [fetchAndActivate] hoàn tất (thành công hay thất bại) và các flag bên dưới đã settle. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /**
     * [context] dùng để seed [RemoteConfig] bằng các giá trị đã lưu (trong file [AppPreferences] dùng chung
     * của app) từ lần fetch thành công gần nhất, để các getter bên dưới trả về giá trị tốt gần nhất ngay khi
     * app vừa mở, thay vì fallback cứng, trong lúc [fetchAndActivate] còn đang chạy.
     */
    fun initialize(context: Context) {
        prefs = AppPreferences.getInstance(context)
        if (BuildConfig.DEBUG) DebugOverrides.init(context)
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            },
        )
        remoteConfig.setDefaultsAsync(
            mapOf(
                KEY_ADS_ENABLE to prefs.getCachedLong(KEY_ADS_ENABLE, 1L),
                KEY_ENABLE to prefs.getCachedString(KEY_ENABLE, DEFAULT_ENABLE),
                KEY_BANNER_SLOT_AUTO_RELOAD to prefs.getCachedBoolean(KEY_BANNER_SLOT_AUTO_RELOAD, false),
                KEY_REFRESH_RATE to
                    prefs.getCachedLong(KEY_REFRESH_RATE, 0L),
                KEY_INTER_DELAY_TIME to prefs.getCachedString(KEY_INTER_DELAY_TIME, DEFAULT_INTER_DELAY_TIME),
                KEY_OPEN_OPEN_GAP_MILLIS to prefs.getCachedLong(KEY_OPEN_OPEN_GAP_MILLIS, 0L),
                KEY_INTER_OPEN_GAP_MILLIS to prefs.getCachedLong(KEY_INTER_OPEN_GAP_MILLIS, 0L),
                KEY_POST_INTERSTITIAL_NATIVE_AD_COUNT to prefs.getCachedLong(KEY_POST_INTERSTITIAL_NATIVE_AD_COUNT, 2L),
                KEY_WAIT_TIME to prefs.getCachedLong(KEY_WAIT_TIME, 5_000L),
                KEY_INTRO_SWIPE to prefs.getCachedBoolean(KEY_INTRO_SWIPE, true),
                KEY_LANGUAGE_DELAY_MILLIS to prefs.getCachedLong(KEY_LANGUAGE_DELAY_MILLIS, 800L),
                KEY_STEP_COUNT to prefs.getCachedLong(KEY_STEP_COUNT, 1L),
                KEY_LANGUAGE_SETUP to prefs.getCachedString(KEY_LANGUAGE_SETUP, DEFAULT_LANGUAGE_SETUP),
                KEY_INTRO1_SETUP to prefs.getCachedString(KEY_INTRO1_SETUP, DEFAULT_INTRO1_SETUP),
                KEY_NATIVE_INTRO_USE_ICON_NEXT to
                    prefs.getCachedString(KEY_NATIVE_INTRO_USE_ICON_NEXT, DEFAULT_NATIVE_INTRO_USE_ICON_NEXT),
                KEY_NATIVE_TEMPLATE to prefs.getCachedString(KEY_NATIVE_TEMPLATE, DEFAULT_NATIVE_TEMPLATE),
                KEY_COLLAP_CONFIG to prefs.getCachedLong(KEY_COLLAP_CONFIG, 1L),
                KEY_ID_BANNER to prefs.getCachedString(KEY_ID_BANNER, ""),
                KEY_ID_BANNER_COLLAPSIBLE to prefs.getCachedString(KEY_ID_BANNER_COLLAPSIBLE, ""),
                KEY_ID_INTERSTITIAL to prefs.getCachedString(KEY_ID_INTERSTITIAL, ""),
                KEY_ID_NATIVE to prefs.getCachedString(KEY_ID_NATIVE, ""),
                KEY_ID_APP_OPEN to prefs.getCachedString(KEY_ID_APP_OPEN, ""),
            ),
        )
        remoteConfig.fetchAndActivate().addOnCompleteListener {
            persistToCache()
            _isReady.value = true
        }
    }

    /**
     * Caches the raw Firebase/default values - never the getters, which include [DebugOverrides]:
     * an override cached here would become the seeded default and outlive "Xoá hết override".
     */
    private fun persistToCache() {
        prefs.putCachedLong(KEY_ADS_ENABLE, remoteConfig.getLong(KEY_ADS_ENABLE))
        prefs.putCachedString(KEY_ENABLE, remoteConfig.getString(KEY_ENABLE))
        prefs.putCachedBoolean(KEY_BANNER_SLOT_AUTO_RELOAD, remoteConfig.getBoolean(KEY_BANNER_SLOT_AUTO_RELOAD))
        prefs.putCachedLong(KEY_REFRESH_RATE, remoteConfig.getLong(KEY_REFRESH_RATE))
        prefs.putCachedString(KEY_INTER_DELAY_TIME, remoteConfig.getString(KEY_INTER_DELAY_TIME))
        prefs.putCachedLong(KEY_OPEN_OPEN_GAP_MILLIS, remoteConfig.getLong(KEY_OPEN_OPEN_GAP_MILLIS))
        prefs.putCachedLong(KEY_INTER_OPEN_GAP_MILLIS, remoteConfig.getLong(KEY_INTER_OPEN_GAP_MILLIS))
        prefs.putCachedLong(KEY_POST_INTERSTITIAL_NATIVE_AD_COUNT, remoteConfig.getLong(KEY_POST_INTERSTITIAL_NATIVE_AD_COUNT))
        prefs.putCachedLong(KEY_WAIT_TIME, remoteConfig.getLong(KEY_WAIT_TIME))
        prefs.putCachedBoolean(KEY_INTRO_SWIPE, remoteConfig.getBoolean(KEY_INTRO_SWIPE))
        prefs.putCachedLong(KEY_LANGUAGE_DELAY_MILLIS, remoteConfig.getLong(KEY_LANGUAGE_DELAY_MILLIS))
        prefs.putCachedLong(KEY_STEP_COUNT, remoteConfig.getLong(KEY_STEP_COUNT))
        prefs.putCachedString(KEY_LANGUAGE_SETUP, remoteConfig.getString(KEY_LANGUAGE_SETUP))
        prefs.putCachedString(KEY_INTRO1_SETUP, remoteConfig.getString(KEY_INTRO1_SETUP))
        prefs.putCachedString(KEY_NATIVE_INTRO_USE_ICON_NEXT, remoteConfig.getString(KEY_NATIVE_INTRO_USE_ICON_NEXT))
        prefs.putCachedString(KEY_NATIVE_TEMPLATE, remoteConfig.getString(KEY_NATIVE_TEMPLATE))
        prefs.putCachedLong(KEY_COLLAP_CONFIG, remoteConfig.getLong(KEY_COLLAP_CONFIG))
        prefs.putCachedString(KEY_ID_BANNER, remoteConfig.getString(KEY_ID_BANNER))
        prefs.putCachedString(KEY_ID_BANNER_COLLAPSIBLE, remoteConfig.getString(KEY_ID_BANNER_COLLAPSIBLE))
        prefs.putCachedString(KEY_ID_INTERSTITIAL, remoteConfig.getString(KEY_ID_INTERSTITIAL))
        prefs.putCachedString(KEY_ID_NATIVE, remoteConfig.getString(KEY_ID_NATIVE))
        prefs.putCachedString(KEY_ID_APP_OPEN, remoteConfig.getString(KEY_ID_APP_OPEN))
    }

    /**
     * Công tắc toàn app + chọn kịch bản (key `ads_enable`): 0 tắt, 1 A, 2 theo nguồn user, 3 theo test ad.
     * Xem [com.namstd.androidskillhub.core.ads.resolveAdProfile].
     */
    val adsEnable: Int
        get() = rcLong(KEY_ADS_ENABLE).toInt()

    /**
     * Bật/tắt + chọn loại quảng cáo theo từng vị trí (key `enable`, dạng
     * `ads_splash=2,inter=1,banner=2,onresume=1,inter_inapp=0`; `0` = tắt vị trí đó). Xem [EnableConfig].
     * Giá trị thô trên Firebase - code quảng cáo đọc qua [com.namstd.androidskillhub.core.ads.AdPolicy.enable].
     * Thay cho 2 key cũ use_custom_interstitial & use_native_collapsible. Định tuyến qua
     * [com.namstd.androidskillhub.core.ads.resolveFullScreenPlan] (inter/splash) và [com.namstd.androidskillhub.core.ads.BannerAds] (banner).
     */
    val enable: EnableConfig
        get() {
            val m = parseKeyValues(rcString(KEY_ENABLE))
            return EnableConfig(
                adsSplash = m["ads_splash"]?.toIntOrNull() ?: 2,
                inter = m["inter"]?.toIntOrNull() ?: 1,
                banner = m["banner"]?.toIntOrNull() ?: 2,
                onresume = m["onresume"]?.toIntOrNull() ?: 1,
                interInApp = m["inter_inapp"]?.toIntOrNull() ?: 0,
            )
        }

    /**
     * Banner slot - thứ mà [com.namstd.androidskillhub.core.ads.BannerAds.load] đặt vào ô banner của một màn
     * (loại do [enable] quyết định) - có tự reload mỗi [refreshRateMs] khi màn đang hiển thị hay không. Không ảnh hưởng
     * tới native ad khác trong app (feed, onboarding, post-interstitial).
     */
    val bannerSlotAutoReload: Boolean
        get() = rcBoolean(KEY_BANNER_SLOT_AUTO_RELOAD)

    /**
     * Timer reload của banner slot: thời gian từ lúc ad được hiển thị (hoặc load lỗi) tới lần reload kế tiếp -
     * xem [bannerSlotAutoReload]. Giá trị config tính bằng ms. Mặc định 0 = không có timer. Màn nào reload bằng tay thì bỏ qua giá trị này.
     */
    val refreshRateMs: Long
        get() = rcLong(KEY_REFRESH_RATE).coerceAtLeast(0L)

    /**
     * Cấu hình gap giữa các Interstitial + danh sách loại inter được MIỄN gap đó.
     * Giá trị config dạng chuỗi `time=10000,ignore=ads_splash|inter_language|inter_intro`:
     * `time` = gap giữa 2 inter (ms), `ignore` = các
     * [com.namstd.androidskillhub.core.ads.InterstitialPlacement.key] luôn được hiện bất kể gap.
     * Thay cho key cũ `inter_inter_gap_seconds`. Xem [com.namstd.androidskillhub.core.ads.AdGapGate].
     * Giá trị thô - code quảng cáo đọc qua [com.namstd.androidskillhub.core.ads.AdPolicy.interDelay].
     */
    val interDelay: InterDelayConfig
        get() {
            var timeMs = 0L
            var ignore = emptySet<String>()
            rcString(KEY_INTER_DELAY_TIME).split(",").forEach { part ->
                val pair = part.split("=", limit = 2)
                val value = pair.getOrElse(1) { "" }.trim()
                when (pair[0].trim()) {
                    "time" -> timeMs = value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    "ignore" -> ignore = value.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
            }
            return InterDelayConfig(gapMs = timeMs, ignore = ignore)
        }

    /**
     * Thời gian tối thiểu giữa 2 lần App Open, tính bằng ms. `0` = không có gap.
     * Xem [com.namstd.androidskillhub.core.ads.AdGapGate].
     */
    val openOpenGapMs: Long
        get() = rcLong(KEY_OPEN_OPEN_GAP_MILLIS).coerceAtLeast(0L)

    /**
     * Thời gian tối thiểu giữa 1 lần Interstitial và 1 lần App Open, cả 2 chiều (tránh 2 ad full-screen
     * hiện liên tiếp khác định dạng), tính bằng ms. `0` = không có gap.
     */
    val interOpenGapMs: Long
        get() = rcLong(KEY_INTER_OPEN_GAP_MILLIS).coerceAtLeast(0L)

    /** Số native ad hiển thị liên tiếp trong break sau interstitial. Clamp 1-2 - [com.namstd.androidskillhub.feature.ads.PostInterstitialNativeAdDialogFragment] chỉ hỗ trợ flow 2 stage. */
    val postInterstitialNativeAdCount: Int
        get() = rcLong(KEY_POST_INTERSTITIAL_NATIVE_AD_COUNT).toInt().coerceIn(1, 2)

    /**
     * Thời gian đếm ngược (ms) trên màn native fullscreen sau interstitial
     * ([com.namstd.androidskillhub.feature.ads.PostInterstitialNativeAdDialogFragment]) trước khi
     * icon X (đóng) hiện ra - và cũng là thời gian mỗi stage đứng trước khi nút Next khả dụng.
     * Giá trị config tính bằng ms.
     */
    val waitTimeMs: Long
        get() = rcLong(KEY_WAIT_TIME).coerceAtLeast(0L)

    /**
     * Thời gian chờ (mili-giây) trước khi nút xác nhận ở màn chọn ngôn ngữ
     * ([com.namstd.androidskillhub.feature.language.LanguageActivity]) hiện ra. `0` = hiện ngay.
     * Giá trị config đã tính bằng ms (không nhân thêm).
     */
    val languageDelayMillis: Long
        get() = rcLong(KEY_LANGUAGE_DELAY_MILLIS).coerceAtLeast(0L)

    /**
     * Tần suất hiển thị interstitial theo số lần yêu cầu: chỉ hiện ở lần yêu cầu chia hết cho giá trị này
     * (`1` = mọi lần; `2` = cứ 2 lần hiện 1 lần...). Bộ đếm lưu qua các lần mở app
     * ([com.namstd.androidskillhub.core.preferences.AppPreferences.interstitialStepCounter]). Clamp tối thiểu 1.
     * Giá trị thô - code quảng cáo đọc qua [com.namstd.androidskillhub.core.ads.AdPolicy.stepCount].
     */
    val stepCount: Int
        get() = rcLong(KEY_STEP_COUNT).toInt().coerceAtLeast(1)

    /**
     * Màn Onboarding ([com.namstd.androidskillhub.feature.onboarding.OnboardingActivity]) có cho phép vuốt
     * (`ViewPager2.isUserInputEnabled`) hay không. `true` (mặc định) = vuốt giữa các trang được;
     * `false` = chỉ chuyển trang bằng nút Next. Fallback mặc định là `true` để giữ đúng hành vi gốc của ViewPager2.
     */
    val introSwipe: Boolean
        get() = rcBoolean(KEY_INTRO_SWIPE)

    /**
     * Cấu hình nút Next màn chọn ngôn ngữ (key `language_setup`, dạng `next=1,loading=true`):
     * `next` = kiểu nút (1=nút chữ, 2=icon), `loading` = có hiện vòng loading trong lúc chờ
     * [languageDelayMillis] trước khi hiện Next hay không.
     */
    val languageSetup: LanguageSetup
        get() {
            val m = parseKeyValues(rcString(KEY_LANGUAGE_SETUP))
            return LanguageSetup(
                nextStyle = m["next"]?.toIntOrNull() ?: 1,
                loading = m["loading"]?.toBoolean() ?: false,
            )
        }

    /**
     * Cấu hình màn Onboarding (key `intro1_setup`, dạng `next=1,ds=1,delay=0,dot=1`):
     * `next` = kiểu nút (1=nút chữ, 2=icon), `ds` = vị trí indicator (1=trên nút Next, 0=dưới),
     * `delay` = số **mili-giây** chờ trước khi hiện nút Next mỗi trang,
     * `dot` = 1 hiện chấm tròn / 0 hiện text "x/n".
     */
    val intro1Setup: Intro1Setup
        get() {
            val m = parseKeyValues(rcString(KEY_INTRO1_SETUP))
            return Intro1Setup(
                nextStyle = m["next"]?.toIntOrNull() ?: 1,
                indicatorPosition = m["ds"]?.toIntOrNull() ?: 1,
                delayMs = m["delay"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                showDots = m["dot"]?.toIntOrNull() == 1,
            )
        }

    /**
     * Cấu hình control trang native fullscreen trong Onboarding (key `native_intro_use_icon_next`,
     * dạng `next=1,loading=true`): `next` = kiểu control (`1` = mũi tên Next, `2` = icon X),
     * `loading` = có hiện spinner chờ [waitTimeMs] trước khi hiện control hay không. Dùng chung kiểu [LanguageSetup].
     */
    val nativeIntroUseIconNext: LanguageSetup
        get() {
            val m = parseKeyValues(rcString(KEY_NATIVE_INTRO_USE_ICON_NEXT))
            return LanguageSetup(
                nextStyle = m["next"]?.toIntOrNull() ?: 1,
                loading = m["loading"]?.toBoolean() ?: false,
            )
        }

    /**
     * Layout cho native card render qua [com.namstd.androidskillhub.core.ads.NativeAds] renderDefaultCard
     * (key `native_template`): `native media` (có ảnh), `native no media` (chỉ chữ), `native banner` (dải nhỏ),
     * `native fullscreen` (card lớn), hoặc một mẫu Figma (`big1`…`big9`, `small1`…`small4`, `tiny1`, `tiny2`,
     * `collap1`…`collap3`, `full1`…`full5`, `lang1_a`, `lang1_b`, `lang2_a`, `lang2_b`) - xem [NativeAdTemplate].
     * Áp cho các placement render mặc định (Language, Onboarding, Welcome back, banner-native);
     * feed/post-interstitial/collapsible giữ layout riêng.
     */
    val nativeTemplate: NativeAdTemplate
        get() = NativeAdTemplate.fromKey(rcString(KEY_NATIVE_TEMPLATE))

    /**
     * Click-bait cho native collapsible ([com.namstd.androidskillhub.core.ads.NativeCollapseAd]): chỉ **tap thứ N**
     * mới bắn click thật (mở trang quảng cáo); các tap trước bị nuốt. `1` = click đi thẳng như thường (không gating).
     */
    val collapConfig: Int
        get() = rcLong(KEY_COLLAP_CONFIG).toInt().coerceAtLeast(1)

    /** Danh sách ID theo tầng cho waterfall (thứ tự highfloor → medium → all_price). Rỗng = để caller fallback test id. */
    val idBanner: List<String> get() = tieredIds(KEY_ID_BANNER)
    val idBannerCollapsible: List<String> get() = tieredIds(KEY_ID_BANNER_COLLAPSIBLE)
    val idInterstitial: List<String> get() = tieredIds(KEY_ID_INTERSTITIAL)
    val idNative: List<String> get() = tieredIds(KEY_ID_NATIVE)
    val idAppOpen: List<String> get() = tieredIds(KEY_ID_APP_OPEN)

    /**
     * Parse chuỗi ID theo tầng `highfloor=id1|id2,medium=id3,all_price=id4` thành 1 list đã xếp ưu tiên
     * highfloor → medium → all_price (nhiều id/tầng ngăn bằng `|`; tầng thiếu thì bỏ qua). Rỗng nếu chưa set.
     */
    private fun tieredIds(key: String): List<String> {
        val m = parseKeyValues(rcString(key))
        return listOf("highfloor", "medium", "all_price").flatMap { tier ->
            m[tier]?.split("|")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        }
    }

    /**
     * [DEBUG] Mọi key + giá trị raw hiện tại (override nếu có, không thì Firebase/default) + gợi ý format,
     * cho màn Debug Config. Firebase lưu mọi kiểu dạng string nên [rcString] đọc được cả Long/Boolean.
     */
    fun debugEntries(): List<DebugConfigEntry> =
        DEBUG_HINTS.map { (key, hint) -> DebugConfigEntry(key, rcString(key), hint) }

    private val DEBUG_HINTS: List<Pair<String, String>> = listOf(
        KEY_ADS_ENABLE to "0 tắt · 1 REMOTE · 2 marketing→REMOTE, còn lại BASIC · 3 test ad→BASIC, còn lại REMOTE",
        KEY_ENABLE to "ads_splash=2,inter=1,banner=2,onresume=1,inter_inapp=0 (0=off)",
        KEY_INTER_DELAY_TIME to "time=ms,ignore=ads_splash|inter_language|inter_intro",
        KEY_WAIT_TIME to "[ms] đếm ngược trước khi hiện X native fullscreen",
        KEY_STEP_COUNT to "số: inter hiện lần chia hết cho N (1=mọi lần)",
        KEY_REFRESH_RATE to "[ms] reload banner (0=không)",
        KEY_BANNER_SLOT_AUTO_RELOAD to "true/false",
        KEY_OPEN_OPEN_GAP_MILLIS to "[ms] gap App Open↔App Open",
        KEY_INTER_OPEN_GAP_MILLIS to "[ms] gap Inter↔App Open",
        KEY_POST_INTERSTITIAL_NATIVE_AD_COUNT to "1-2 native sau inter",
        KEY_INTRO_SWIPE to "true/false (vuốt Onboarding)",
        KEY_LANGUAGE_DELAY_MILLIS to "[ms] chờ trước khi hiện nút Language",
        KEY_LANGUAGE_SETUP to "next=1/2,loading=true/false",
        KEY_INTRO1_SETUP to "next=1/2,ds=0/1,delay=ms,dot=0/1",
        KEY_NATIVE_INTRO_USE_ICON_NEXT to "next=1/2,loading=true/false",
        KEY_NATIVE_TEMPLATE to NativeAdTemplate.keys,
        KEY_COLLAP_CONFIG to "N: tap thứ N mới click ad collap (1=click ngay)",
        KEY_ID_BANNER to "highfloor=..|..,medium=..,all_price=.. (rỗng=test id)",
        KEY_ID_BANNER_COLLAPSIBLE to "highfloor=..,medium=..,all_price=.. (rỗng=test id)",
        KEY_ID_INTERSTITIAL to "highfloor=..,medium=..,all_price=.. (rỗng=test id)",
        KEY_ID_NATIVE to "highfloor=..,medium=..,all_price=.. (áp mọi native; rỗng=test id)",
        KEY_ID_APP_OPEN to "highfloor=..,medium=..,all_price=.. (rỗng=test id)",
    )

    /** Tách chuỗi config dạng `k=v,k=v` (bỏ khoảng trắng thừa) thành map. */
    private fun parseKeyValues(raw: String): Map<String, String> =
        raw.split(",").mapNotNull { part ->
            val pair = part.split("=", limit = 2)
            if (pair.size == 2) pair[0].trim() to pair[1].trim() else null
        }.toMap()

    private const val KEY_ADS_ENABLE = "ads_enable"
    private const val KEY_ENABLE = "enable"
    private const val DEFAULT_ENABLE = "ads_splash=2,inter=1,banner=2,onresume=1,inter_inapp=0"
    private const val KEY_BANNER_SLOT_AUTO_RELOAD = "banner_slot_auto_reload"
    private const val KEY_REFRESH_RATE = "refresh_rate"
    private const val KEY_INTER_DELAY_TIME = "inter_delay_time"
    private const val DEFAULT_INTER_DELAY_TIME = "time=0,ignore="
    private const val KEY_OPEN_OPEN_GAP_MILLIS = "open_open_gap_millis"
    private const val KEY_INTER_OPEN_GAP_MILLIS = "inter_open_gap_millis"
    private const val KEY_POST_INTERSTITIAL_NATIVE_AD_COUNT = "post_interstitial_native_ad_count"
    private const val KEY_WAIT_TIME = "wait_time"
    private const val KEY_INTRO_SWIPE = "intro_swipe"
    private const val KEY_LANGUAGE_DELAY_MILLIS = "language_delay_millis"
    private const val KEY_STEP_COUNT = "step_count"
    private const val KEY_LANGUAGE_SETUP = "language_setup"
    private const val DEFAULT_LANGUAGE_SETUP = "next=1,loading=false"
    private const val KEY_INTRO1_SETUP = "intro1_setup"
    private const val DEFAULT_INTRO1_SETUP = "next=1,ds=1,delay=0,dot=0"
    private const val KEY_NATIVE_INTRO_USE_ICON_NEXT = "native_intro_use_icon_next"
    private const val DEFAULT_NATIVE_INTRO_USE_ICON_NEXT = "next=1,loading=false"
    private const val KEY_NATIVE_TEMPLATE = "native_template"
    private const val DEFAULT_NATIVE_TEMPLATE = "native media"
    private const val KEY_COLLAP_CONFIG = "collap_config"
    private const val KEY_ID_BANNER = "id_banner"
    private const val KEY_ID_BANNER_COLLAPSIBLE = "id_banner_collapsible"
    private const val KEY_ID_INTERSTITIAL = "id_interstitial"
    private const val KEY_ID_NATIVE = "id_native"
    private const val KEY_ID_APP_OPEN = "id_app_open"
}
