package com.example.quittiktok.data

import android.content.Context
import android.content.SharedPreferences

class TimeState(context: Context) {

    companion object {
        private const val PREF_NAME = "QuitTikTok_Prefs"
        private const val KEY_SESSION_ACCUMULATED = "session_accumulated"
        private const val KEY_SESSION_LAST_FOREGROUND_TIME = "session_last_foreground_time"
        private const val KEY_SESSION_LAST_BACKGROUND_TIME = "session_last_background_time"
        private const val KEY_CYCLE_ANCHOR_TIME = "cycle_anchor_time"
        private const val KEY_CYCLE_ACCUMULATED = "cycle_accumulated"
        private const val KEY_IS_IN_FOREGROUND = "is_in_foreground"
        private const val KEY_LOCK_END_TIME = "lock_end_time"

        const val SESSION_LIMIT_MS = 15 * 60 * 1000L
//        const val SESSION_LIMIT_MS = 10*1000L
        const val SESSION_RESET_TIMEOUT_MS = 5 * 60 * 1000L
        const val DAILY_LIMIT_MS = 45 * 60 * 1000L
//        const val DAILY_LIMIT_MS = 20*1000L
        const val CYCLE_DURATION_MS = 24 * 60 * 60 * 1000L
        const val LOCK_DURATION_MS = 60 * 60 * 1000L
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var sessionAccumulatedMs: Long
        get() = prefs.getLong(KEY_SESSION_ACCUMULATED, 0)
        set(value) = prefs.edit().putLong(KEY_SESSION_ACCUMULATED, value).apply()

    var sessionLastForegroundTime: Long
        get() = prefs.getLong(KEY_SESSION_LAST_FOREGROUND_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_SESSION_LAST_FOREGROUND_TIME, value).apply()

    var sessionLastBackgroundTime: Long
        get() = prefs.getLong(KEY_SESSION_LAST_BACKGROUND_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_SESSION_LAST_BACKGROUND_TIME, value).apply()

    var cycleAnchorTime: Long
        get() = prefs.getLong(KEY_CYCLE_ANCHOR_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_CYCLE_ANCHOR_TIME, value).apply()

    var cycleAccumulatedMs: Long
        get() = prefs.getLong(KEY_CYCLE_ACCUMULATED, 0)
        set(value) = prefs.edit().putLong(KEY_CYCLE_ACCUMULATED, value).apply()

    var isInForeground: Boolean
        get() = prefs.getBoolean(KEY_IS_IN_FOREGROUND, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_IN_FOREGROUND, value).apply()

    var lockEndTime: Long
        get() = prefs.getLong(KEY_LOCK_END_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LOCK_END_TIME, value).apply()

    fun resetSession() {
        sessionAccumulatedMs = 0
        sessionLastForegroundTime = 0
        sessionLastBackgroundTime = 0
    }

    fun resetCycle() {
        cycleAnchorTime = System.currentTimeMillis()
        cycleAccumulatedMs = 0
    }

    fun isCycleExpired(): Boolean {
        if (cycleAnchorTime == 0L) return true
        return System.currentTimeMillis() > cycleAnchorTime + CYCLE_DURATION_MS
    }

    fun getCycleRemainingTime(): Long {
        if (cycleAnchorTime == 0L) return CYCLE_DURATION_MS
        val cycleEndTime = cycleAnchorTime + CYCLE_DURATION_MS
        return Math.max(0, cycleEndTime - System.currentTimeMillis())
    }

    fun clearLockEndTime() {
        prefs.edit().remove(KEY_LOCK_END_TIME).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}