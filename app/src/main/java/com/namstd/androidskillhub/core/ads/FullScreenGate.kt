package com.namstd.androidskillhub.core.ads

/** Chốt chặn toàn app để ads và dialog không che lên nhau. */
object FullScreenGate {
    private var owner: Any? = null

    @Synchronized
    fun acquire(candidate: Any): Boolean {
        if (owner != null) return false
        owner = candidate
        return true
    }

    @Synchronized
    fun release(candidate: Any) {
        if (owner === candidate) owner = null
    }

    @Synchronized
    fun isBusy(): Boolean = owner != null

    internal fun clearForTest() {
        owner = null
    }
}
