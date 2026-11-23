package com.namstd.androidskillhub.core.ads

/**
 * Cache quảng cáo theo **ID** (ID thật trong `ad_ids`), dùng chung cho mọi placement có ID đó. Mỗi placement hỏi bằng
 * một chuỗi ID theo thứ tự ưu tiên (`chain`, vd [A, B, C] của waterfall).
 * - [take]: lấy ad rảnh còn hạn của ID đầu tiên trong chain có sẵn (có sẵn ID thấp hơn thì dùng luôn, không chờ ID cao).
 * - Người chờ ([addWaiter]) xếp hàng FIFO; ad load xong từ ID X giao cho người chờ cũ nhất có X trong chain của mình
 *   (kể cả placement khác); không ai chờ thì cất vào pool của X ([onLoaded]).
 * - Mỗi ID giữ tối đa 1 ad rảnh ([cache]).
 * - Mỗi người chờ có lượt load riêng ([needsLoad]) nên hai màn cùng ID không giành ad của nhau. Preload thì không load
 *   thêm khi đã có ad rảnh hoặc đang có lượt load chạm tới một ID của chuỗi - đợi lượt đó.
 * Không phụ thuộc SDK (thread: caller tự đồng bộ - mọi hàm đều @Synchronized).
 */
internal class AdIdPool<T : Any>(
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
    private val destroy: (T) -> Unit = {},
) {
    class Loaded<T>(val ad: T, val id: String)

    class Waiter<T> internal constructor(
        val chain: List<String>,
        internal val onReady: (Loaded<T>) -> Unit,
        internal val onFailed: () -> Unit,
    )

    private class Entry<T>(val ad: T, val loadedAt: Long)

    private val free = mutableMapOf<String, Entry<T>>()
    private val waiters = ArrayDeque<Waiter<T>>()
    private val inFlight = mutableMapOf<String, Int>()
    private val chains = mutableMapOf<String, List<String>>()
    private val lastFailed = mutableSetOf<String>()

    private fun key(chain: List<String>) = chain.joinToString("|")

    /** Ad rảnh còn hạn của [id]; quá hạn thì destroy và bỏ. */
    private fun fresh(id: String): Entry<T>? {
        val entry = free[id] ?: return null
        if (now() - entry.loadedAt < ttlMs) return entry
        free.remove(id)
        destroy(entry.ad)
        return null
    }

    @Synchronized
    fun take(chain: List<String>): Loaded<T>? {
        for (id in chain) {
            val entry = fresh(id) ?: continue
            free.remove(id)
            return Loaded(entry.ad, id)
        }
        return null
    }

    @Synchronized
    fun hasFresh(chain: List<String>): Boolean = chain.any { fresh(it) != null }

    /** Cất [ad] (của ID [id]) làm ad rảnh. Mỗi ID giữ 1 ad: đã có thì giữ ad mới hơn (sống lâu hơn), destroy ad cũ. */
    @Synchronized
    fun cache(id: String, ad: T) {
        free.put(id, Entry(ad, now()))?.let { old -> if (old.ad !== ad) destroy(old.ad) }
    }

    /** Lượt load gần nhất của chain lỗi, không còn lượt nào đang chạy và không có ad rảnh: tạm thời không có ad. */
    @Synchronized
    fun failedAndIdle(chain: List<String>): Boolean {
        val k = key(chain)
        return k in lastFailed && (inFlight[k] ?: 0) == 0 && !hasFresh(chain)
    }

    @Synchronized
    fun addWaiter(chain: List<String>, onReady: (Loaded<T>) -> Unit, onFailed: () -> Unit): Waiter<T> =
        Waiter(chain, onReady, onFailed).also { waiters.addLast(it) }

    @Synchronized
    fun removeWaiter(waiter: Waiter<T>) {
        waiters.remove(waiter)
    }

    /**
     * Có cần chạy thêm một lượt load cho [chain] không.
     * - Người chờ: mỗi người một lượt (đếm các lượt đang chạy của đúng chuỗi này).
     * - [preload]: chỉ khi chưa có ad rảnh nào của chuỗi và không có lượt load nào (của bất kỳ chuỗi nào) đang chạy chạm
     *   tới một ID của chuỗi - đang load rồi thì đợi kết quả, không load thêm.
     */
    @Synchronized
    fun needsLoad(chain: List<String>, preload: Boolean = false): Boolean {
        if (preload) {
            return !hasFresh(chain) && chains.values.none { running -> running.any { it in chain } }
        }
        val k = key(chain)
        return (inFlight[k] ?: 0) < waiters.count { key(it.chain) == k }
    }

    /** Đánh dấu bắt đầu một lượt load của [chain]. */
    @Synchronized
    fun startLoad(chain: List<String>) {
        val k = key(chain)
        inFlight[k] = (inFlight[k] ?: 0) + 1
        chains[k] = chain
        lastFailed.remove(k)
    }

    private fun finishLoad(chain: List<String>) {
        val k = key(chain)
        val left = (inFlight[k] ?: 1) - 1
        if (left <= 0) { inFlight.remove(k); chains.remove(k) } else inFlight[k] = left
    }

    /**
     * Lượt load của [chain] ra ad [ad] từ ID [id]. Trả người chờ nhận ad (caller gọi [deliver] ngoài khoá) hoặc null
     * nếu ad đã được cất vào pool.
     */
    @Synchronized
    fun onLoaded(chain: List<String>, id: String, ad: T): Pair<Waiter<T>, Loaded<T>>? {
        finishLoad(chain)
        val waiter = waiters.firstOrNull { id in it.chain }
        if (waiter == null) {
            cache(id, ad)
            return null
        }
        waiters.remove(waiter)
        return waiter to Loaded(ad, id)
    }

    /** Lượt load của [chain] không ra ad: trả người chờ cũ nhất của đúng chain đó để báo lỗi (hoặc null). */
    @Synchronized
    fun onFailed(chain: List<String>): Waiter<T>? {
        finishLoad(chain)
        val k = key(chain)
        lastFailed += k
        // Còn lượt khác của chain đang chạy cho đủ người chờ thì người chờ vẫn đợi lượt đó.
        val waiting = waiters.filter { key(it.chain) == k }
        if (waiting.size <= (inFlight[k] ?: 0)) return null
        return waiting.first().also { waiters.remove(it) }
    }

    @Synchronized
    fun clear() {
        free.values.forEach { destroy(it.ad) }
        free.clear()
        chains.clear()
        waiters.clear()
        inFlight.clear()
        lastFailed.clear()
    }
}
