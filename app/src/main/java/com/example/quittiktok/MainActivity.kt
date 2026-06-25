package com.example.quittiktok

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.quittiktok.service.MonitorService
import com.example.quittiktok.state.TimeStateMachine
import com.example.quittiktok.data.TimeState

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ACCESSIBILITY = 1001
        private const val REQUEST_OVERLAY = 1002
    }

    private lateinit var timeStateMachine: TimeStateMachine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        timeStateMachine = TimeStateMachine(this)

        findViewById<Button>(R.id.start_button).setOnClickListener {
            startMonitoring()
        }

        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopMonitoring()
        }

        findViewById<Button>(R.id.reset_button).setOnClickListener {
            resetAllData()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun startMonitoring() {
        if (!checkPermissions()) {
            return
        }

        MonitorService.requestStart(this)
        Toast.makeText(this, "监控服务已启动", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun stopMonitoring() {
        MonitorService.requestStop(this)
        Toast.makeText(this, "监控服务已停止", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun resetAllData() {
        timeStateMachine.resetSessionLock()
        timeStateMachine.resetCycle()
        Toast.makeText(this, "数据已重置", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun checkPermissions(): Boolean {
        if (!checkAccessibilityPermission()) {
            requestAccessibilityPermission()
            return false
        }

        if (!checkOverlayPermission()) {
            requestOverlayPermission()
            return false
        }

        return true
    }

    private fun checkAccessibilityPermission(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == "$packageName.service.MonitorService"
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivityForResult(intent, REQUEST_ACCESSIBILITY)
    }

    private fun checkOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_OVERLAY)
    }

    private fun updateStatus() {
        val isServiceRunning = MonitorService.isRunning()

        findViewById<TextView>(R.id.status_text).text =
            if (isServiceRunning) "监控状态：运行中" else "监控状态：已停止"

        val sessionUsed = formatTime(timeStateMachine.getSessionAccumulatedMs())
        val sessionLimit = formatTime(TimeState.SESSION_LIMIT_MS)
        findViewById<TextView>(R.id.session_usage_text).text =
            "单次会话：$sessionUsed / $sessionLimit"

        val cycleUsed = formatTime(timeStateMachine.getCycleAccumulatedMs())
        val cycleLimit = formatTime(TimeState.DAILY_LIMIT_MS)
        findViewById<TextView>(R.id.cycle_usage_text).text =
            "24小时周期：$cycleUsed / $cycleLimit"

        val remaining = timeStateMachine.getCycleRemainingTime()
        findViewById<TextView>(R.id.cycle_remaining_text).text =
            "周期剩余：${formatTime(remaining)}"
    }

    private fun isServiceRunning(serviceName: String): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == serviceName
        }
    }

    private fun formatTime(millis: Long): String {
        val hours = (millis / (1000 * 60 * 60)).toInt()
        val minutes = ((millis % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        val seconds = ((millis % (1000 * 60)) / 1000).toInt()

        return if (hours > 0) {
            String.format("%d小时%d分%d秒", hours, minutes, seconds)
        } else if (minutes > 0) {
            String.format("%d分%d秒", minutes, seconds)
        } else {
            String.format("%d秒", seconds)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ACCESSIBILITY -> {
                if (checkAccessibilityPermission()) {
                    Toast.makeText(this, "已开启无障碍服务权限", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "需要开启无障碍服务权限", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_OVERLAY -> {
                if (checkOverlayPermission()) {
                    Toast.makeText(this, "已获取悬浮窗权限", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}