package com.example.quittiktok.state

import android.content.Context
import com.example.quittiktok.data.TimeState

class TimeStateMachine(context: Context) {

    companion object {
        const val TARGET_PACKAGE = "com.ss.android.ugc.aweme"
        const val CHECK_INTERVAL_MS = 1000L
    }

    enum class LockType {
        NONE, SESSION_LOCK, DAILY_CYCLE_LOCK
    }

    private val timeState = TimeState(context.applicationContext)
    private var lastUpdateTime: Long = 0
    private var isMonitoring = false

    @Synchronized
    fun onTargetForeground() {
        val now = System.currentTimeMillis()

        if (timeState.isCycleExpired()) {
            timeState.resetCycle()
        }

        if (timeState.isInForeground) {
            val delta = now - lastUpdateTime
            updateAccumulatedTime(delta)
            lastUpdateTime = now
            return
        }

        timeState.isInForeground = true

        val backgroundDuration = if (timeState.sessionLastBackgroundTime > 0) {
            now - timeState.sessionLastBackgroundTime
        } else {
            Long.MAX_VALUE
        }

        if (backgroundDuration > TimeState.SESSION_RESET_TIMEOUT_MS) {
            timeState.resetSession()
        }

        timeState.sessionLastForegroundTime = now
        lastUpdateTime = now
    }

    @Synchronized
    fun onTargetBackground() {
        if (!timeState.isInForeground) return

        val now = System.currentTimeMillis()
        val delta = now - lastUpdateTime
        updateAccumulatedTime(delta)

        timeState.isInForeground = false
        timeState.sessionLastBackgroundTime = now
    }

    @Synchronized
    fun updateAccumulatedTime(deltaMs: Long) {
        if (deltaMs <= 0) return

        timeState.sessionAccumulatedMs += deltaMs
        timeState.cycleAccumulatedMs += deltaMs
    }

    @Synchronized
    fun checkLockStatus(): LockType {
        if (timeState.cycleAccumulatedMs >= TimeState.DAILY_LIMIT_MS) {
            return LockType.DAILY_CYCLE_LOCK
        }
        if (timeState.sessionAccumulatedMs >= TimeState.SESSION_LIMIT_MS) {
            return LockType.SESSION_LOCK
        }
        return LockType.NONE
    }

    @Synchronized
    fun resetSessionLock() {
        timeState.resetSession()
        // 同时清除单次会话锁定的持久化时间戳，防止幽灵拦截
        timeState.clearLockEndTime()
    }

    @Synchronized
    fun resetCycle() {
        timeState.resetCycle()
    }

    @Synchronized
    fun getSessionRemainingTime(): Long {
        return Math.max(0, TimeState.SESSION_LIMIT_MS - timeState.sessionAccumulatedMs)
    }

    @Synchronized
    fun getCycleRemainingTime(): Long {
        return timeState.getCycleRemainingTime()
    }

    @Synchronized
    fun getSessionAccumulatedMs(): Long {
        return timeState.sessionAccumulatedMs
    }

    @Synchronized
    fun getCycleAccumulatedMs(): Long {
        return timeState.cycleAccumulatedMs
    }

    @Synchronized
    fun getLockRemainingTime(lockType: LockType): Long {
        return when (lockType) {
            LockType.SESSION_LOCK -> TimeState.LOCK_DURATION_MS
            LockType.DAILY_CYCLE_LOCK -> {
                // 直接复用"周期剩余"逻辑，与 MainActivity 保持一致
                getCycleRemainingTime()
            }
            LockType.NONE -> 0
        }
    }

    @Synchronized
    fun getLockEndTime(): Long {
        return timeState.lockEndTime
    }

    @Synchronized
    fun setLockEndTime(endTime: Long) {
        timeState.lockEndTime = endTime
    }

    @Synchronized
    fun clearLockEndTime() {
        timeState.clearLockEndTime()
    }

    @Synchronized
    fun isInForeground(): Boolean {
        return timeState.isInForeground
    }

    fun startMonitoring() {
        isMonitoring = true
        lastUpdateTime = System.currentTimeMillis()
    }

    fun stopMonitoring() {
        isMonitoring = false
    }

    fun isMonitoring(): Boolean {
        return isMonitoring
    }
}