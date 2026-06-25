package com.example.quittiktok.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.quittiktok.R
import com.example.quittiktok.service.MonitorService
import com.example.quittiktok.state.TimeStateMachine

class LockActivity : Activity() {

    companion object {
        private const val TAG = "LockActivity"
        private const val EXTRA_LOCK_TYPE = "lock_type"

        // 背景图资源数组 - 请将图片放入 res/drawable 目录并按此格式命名
        // 例如: bg_1.jpg, bg_2.png, bg_3.webp 等
        // 图片命名规则: bg_1, bg_2, bg_3, ... (不含扩展名)
        private val backgroundImages = listOf(
            R.drawable.bg_1,
            R.drawable.bg_2,
            R.drawable.bg_3,
        )

        fun start(context: Context, lockType: TimeStateMachine.LockType) {
            val intent = Intent(context, LockActivity::class.java).apply {
                putExtra(EXTRA_LOCK_TYPE, lockType.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var timeStateMachine: TimeStateMachine
    private lateinit var handler: Handler
    private var countdownRunnable: Runnable? = null
    private var lockEndTime: Long = 0
    private var lockType: TimeStateMachine.LockType = TimeStateMachine.LockType.SESSION_LOCK

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制开启 Edge-to-Edge 沉浸式模式，必须在 setContentView 之前调用
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }

        // 设置状态栏文字为亮色（白色），适配暗色背景
        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        setContentView(R.layout.lock_activity)

        lockType = TimeStateMachine.LockType.valueOf(
            intent.getStringExtra(EXTRA_LOCK_TYPE) ?: "SESSION_LOCK"
        )

        timeStateMachine = TimeStateMachine(this)
        handler = Handler(Looper.getMainLooper())

        setupBackgroundImage()
        setupUI()
        startCountdown()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // singleTask 模式下被复用时，重新获取最新的锁定结束时间
        lockType = TimeStateMachine.LockType.valueOf(
            intent?.getStringExtra(EXTRA_LOCK_TYPE) ?: this.intent.getStringExtra(EXTRA_LOCK_TYPE) ?: "SESSION_LOCK"
        )
        startCountdown()
    }

    private fun setupBackgroundImage() {
        try {
            val backgroundImageView = findViewById<ImageView>(R.id.background_image)
            if (backgroundImages.isNotEmpty()) {
                val randomIndex = backgroundImages.indices.random()
                val selectedBg = backgroundImages[randomIndex]
                backgroundImageView?.setImageResource(selectedBg)
                Log.d(TAG, "随机选择背景图: bg_${randomIndex + 1}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置背景图失败", e)
        }
    }

    private fun setupUI() {
        val lockTypeText = findViewById<TextView>(R.id.lock_type_text)
        val countdownText = findViewById<TextView>(R.id.countdown_text)
        val taskAButton = findViewById<Button>(R.id.task_a_button)
        val taskBButton = findViewById<Button>(R.id.task_b_button)

        if (lockType == TimeStateMachine.LockType.SESSION_LOCK) {
            lockTypeText.text = "单次会话已达上限"
            taskAButton.visibility = View.VISIBLE
            taskBButton.visibility = View.VISIBLE

            taskAButton.setOnClickListener {
                startSensorTask(SensorTaskType.SWING)
            }

            taskBButton.setOnClickListener {
                startSensorTask(SensorTaskType.MEDITATE)
            }
        } else {
            lockTypeText.text = "日总时长已达上限"
            taskAButton.visibility = View.GONE
            taskBButton.visibility = View.GONE
        }

        val remainingTime = timeStateMachine.getLockRemainingTime(lockType)
        countdownText.text = formatTime(remainingTime)

        countdownText.setOnLongClickListener {
            Log.w(TAG, "紧急后门触发！长按解除锁定")
            Toast.makeText(this, "紧急解除！", Toast.LENGTH_SHORT).show()
            forceUnlockAndFinish()
            true
        }
    }

    private fun startCountdown() {
        val now = System.currentTimeMillis()
        
        if (lockType == TimeStateMachine.LockType.DAILY_CYCLE_LOCK) {
            // 日总时长拦截：直接复用"周期剩余"逻辑，与 MainActivity 保持一致
            // getCycleRemainingTime() 基于 cycleAnchorTime 计算，本身就是持久化的
            val remainingTime = timeStateMachine.getLockRemainingTime(lockType)
            lockEndTime = now + remainingTime
        } else {
            // 单次会话拦截：使用独立的持久化时间戳
            val savedLockEndTime = timeStateMachine.getLockEndTime()
            lockEndTime = if (savedLockEndTime > now) {
                savedLockEndTime
            } else {
                val newEndTime = now + timeStateMachine.getLockRemainingTime(lockType)
                timeStateMachine.setLockEndTime(newEndTime)
                newEndTime
            }
        }

        // 先移除旧的回调，避免重复（空安全检查）
        countdownRunnable?.let { handler.removeCallbacks(it) }

        countdownRunnable = object : Runnable {
            override fun run() {
                val remaining = Math.max(0, lockEndTime - System.currentTimeMillis())

                findViewById<TextView>(R.id.countdown_text)?.text = formatTime(remaining)

                if (remaining <= 0) {
                    onLockExpired()
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        }

        countdownRunnable?.let { handler.post(it) }
    }

    private fun onLockExpired() {
        // 只有单次会话拦截需要清除持久化时间戳
        // 日总时长拦截的时间由 cycleAnchorTime 管理，不需要额外清除
        if (lockType == TimeStateMachine.LockType.SESSION_LOCK) {
            timeStateMachine.clearLockEndTime()
            timeStateMachine.resetSessionLock()
        }
        finish()
    }

    private fun forceUnlockAndFinish() {
        Log.w(TAG, "执行强制解锁：重置所有计时器并退出")
        timeStateMachine.clearLockEndTime()
        timeStateMachine.resetSessionLock()
        timeStateMachine.resetCycle()
        finish()
    }

    private fun startSensorTask(taskType: SensorTaskType) {
        val intent = Intent(this, SensorUnlockActivity::class.java).apply {
            putExtra("task_type", taskType.name)
        }
        startActivity(intent)
    }

    private fun formatTime(millis: Long): String {
        val hours = (millis / (1000 * 60 * 60)).toInt()
        val minutes = ((millis % (1000 * 60 * 60)) / (1000 * 60)).toInt()
        val seconds = ((millis % (1000 * 60)) / 1000).toInt()

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onBackPressed() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
        MonitorService.resetLockScreenFlag()
    }

    enum class SensorTaskType {
        SWING, MEDITATE
    }
}