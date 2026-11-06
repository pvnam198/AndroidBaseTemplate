package com.namstd.androidskillhub.core.ads

/**
 * `splash_fallback`: khi Splash không kịp hiện quảng cáo full-screen, placement ghi ở key này (vd
 * `inter_language`) sẽ hiện một interstitial thường ở lần chạy kế tiếp - ở Language là lúc user bấm Apply -
 * bất kể plan riêng, `step` hay gap của nó nói gì. Chỉ trong phiên mở app này, và chỉ khi màn đó xuất hiện:
 * user cũ đi thẳng Splash → Home, không qua màn Language, nên không hiện gì.
 */
object SplashFallback {
    @Volatile private var pending: InterstitialPlacement? = null

    /** Splash lỡ quảng cáo: kích hoạt placement đã cấu hình, và bắt đầu load sẵn interstitial mà nó sẽ cần. */
    fun arm() {
        pending = AdPolicy.splashFallback
        pending?.let { InterstitialAds.load(it.interUnit) }
    }

    /** Màn sau Splash không có placement fallback (vd Home với user cũ). */
    fun clear() {
        pending = null
    }

    /** Trả true đúng một lần, nếu [placement] là fallback đã kích hoạt - khi đó caller hiện interstitial. */
    @Synchronized
    fun take(placement: InterstitialPlacement): Boolean = (pending == placement).also { if (it) pending = null }
}
