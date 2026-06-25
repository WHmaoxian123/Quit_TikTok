package com.example.quittiktok.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.quittiktok.activity.LockActivity
import com.example.quittiktok.receiver.BootReceiver
import com.example.quittiktok.state.TimeStateMachine

class MonitorService : AccessibilityService() {

    companion object {
        private const val TAG = "MonitorService"
        private const val TARGET_PACKAGE = "com.ss.android.ugc.aweme"

        private var instance: MonitorService? = null
        @Volatile
        private var isLockScreenShowing = false

        fun isRunning(): Boolean {
            return instance != null
        }

        fun requestStart(context: android.content.Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.startService(intent)
        }

        fun requestStop(context: android.content.Context) {
            val intent = Intent(context, MonitorService::class.java)
            context.stopService(intent)
        }

        fun resetLockScreenFlag() {
            isLockScreenShowing = false
            Log.d(TAG, "Lock screen flag reset to false")
        }

        fun start(context: Context) {}
    }

    private lateinit var timeStateMachine: TimeStateMachine
    private var lastForegroundPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MonitorService created")
        instance = this

        timeStateMachine = TimeStateMachine(this)
        timeStateMachine.startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MonitorService onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MonitorService destroyed")
        instance = null

        timeStateMachine.stopMonitoring()

        isLockScreenShowing = false

        val restartIntent = Intent(BootReceiver.ACTION_RESTART_MONITOR)
        restartIntent.setPackage(packageName)
        sendBroadcast(restartIntent)
        Log.d(TAG, "Sent restart broadcast: ${BootReceiver.ACTION_RESTART_MONITOR}")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            notificationTimeout = 100
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "检测到前台变化: ${event?.packageName}")

        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()

            if (packageName == null) return

            val isTargetApp = (packageName == TARGET_PACKAGE)
            val isOurApp = (packageName == this.packageName)

            if (!isTargetApp && !isOurApp) {
                isLockScreenShowing = false
                Log.d(TAG, "前台既不是抖音也不是本应用，强制重置锁定标志")
            }

            if (isTargetApp) {
                Log.d(TAG, "目标应用切换到前台")
                timeStateMachine.onTargetForeground()

                val lockType = timeStateMachine.checkLockStatus()
                if (lockType != TimeStateMachine.LockType.NONE) {
                    if (!isLockScreenShowing) {
                        try {
                            Log.d(TAG, "时间超限，启动 LockActivity 拦截: $lockType")
                            isLockScreenShowing = true
                            LockActivity.start(this, lockType)
                        } catch (e: Exception) {
                            Log.e(TAG, "启动 LockActivity 失败", e)
                            isLockScreenShowing = false
                        }
                    } else {
                        Log.d(TAG, "锁定界面已显示，跳过重复启动")
                    }
                }
            } else {
                if (lastForegroundPackage == TARGET_PACKAGE) {
                    Log.d(TAG, "目标应用切换到后台")
                    timeStateMachine.onTargetBackground()
                }
            }

            lastForegroundPackage = packageName
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }
}
