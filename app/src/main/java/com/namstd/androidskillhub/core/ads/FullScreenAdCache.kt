package com.namstd.androidskillhub.core.ads

import android.util.Log
import com.namstd.androidskillhub.BuildConfig

/**
 * Cache quảng cáo toàn màn hình (inter / App Open / reward) theo ID thật trong `ad_ids` ([AdIdPool], mỗi ID tối đa 1 ad
 * chờ), dùng chung giữa các placement: inter tải cho `inter_home` mà chưa dùng thì `inter_language` cùng ID lấy được.
 * [load] chỉ tải khi chuỗi ID của unit chưa có ad chờ và không có lượt tải nào đang chạm tới các ID đó (đang tải thì đợi).
 * [loadOne] gửi một request cho một ID (ID gửi đi - bản debug là ID test của Google).
 */
internal class FullScreenAdCache<T : Any>(
    private val tag: String,
    ttlMs: Long,
    destroy: (T) -> Unit,
    private val loadOne: (unit: AdUnit, requestId: String, onLoaded: (T) -> Unit, onFailed: (String) -> Unit) -> Unit,
) {
    private val pool = AdIdPool(ttlMs, destroy = destroy)

    /** Có ad chờ (của một ID bất kỳ trong chuỗi của [unit]) để hiện ngay không. */
    fun isReady(unit: AdUnit): Boolean = pool.hasFresh(unit.configIds())

    fun load(unit: AdUnit, onStatus: (String) -> Unit = {}) {
        val chain = unit.configIds()
        if (chain.isEmpty() || !pool.needsLoad(chain, preload = true)) return
        pool.startLoad(chain)
        WaterfallLoader<T>(chain).load(
            attempt = { id, loaded, failed -> loadOne(unit, unit.requestId(id), loaded, failed) },
            onLoaded = { ad, id ->
                pool.onLoaded(chain, id, ad)
                if (BuildConfig.DEBUG) Log.d(tag, "${unit.name}: load xong ${id.takeLast(10)} → cất pool")
                onStatus("${unit.name}: ready")
            },
            onFailed = { error ->
                pool.onFailed(chain)
                if (BuildConfig.DEBUG) Log.d(tag, "${unit.name}: load lỗi ($error)")
                onStatus("${unit.name}: $error")
            },
        )
    }

    /** Lấy ad chờ của [unit] ra để hiện (ID ưu tiên trước); null nếu chưa có. */
    fun take(unit: AdUnit): T? {
        val loaded = pool.take(unit.configIds()) ?: return null
        if (BuildConfig.DEBUG) Log.d(tag, "${unit.name}: lấy ad sẵn của ${loaded.id.takeLast(10)}")
        return loaded.ad
    }

    fun clear() = pool.clear()
}
