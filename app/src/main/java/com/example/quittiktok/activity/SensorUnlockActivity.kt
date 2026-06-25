package com.example.quittiktok.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.quittiktok.R
import com.example.quittiktok.state.TimeStateMachine

class SensorUnlockActivity : Activity(), SensorEventListener {

    companion object {
        private const val TAG = "SensorUnlockActivity"
        private const val SWING_TARGET_COUNT = 50
        private const val MEDITATE_DURATION_MS = 5 * 60 * 1000L
        private const val SWING_THRESHOLD = 15f
        private const val MEDITATE_THRESHOLD = 0.5f
        private const val BREATH_CYCLE_MS = 4000L

        // 任务页面背景图资源数组
        private val backgroundImages = listOf(
            R.drawable.bg_1,
            R.drawable.bg_2,
            R.drawable.bg_3,
        )
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var swingCount = 0
    private var lastSwingTime = 0L
    private var lastYAccel = 0f
    private var lastZAccel = 0f

    private var meditationStartTime = 0L
    private var meditationPaused = false
    private var meditationRemaining = MEDITATE_DURATION_MS
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f

    private lateinit var handler: Handler
    private var breathRunnable: Runnable? = null
    private var meditationRunnable: Runnable? = null

    private var taskType: SensorTaskType = SensorTaskType.SWING

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

        setContentView(R.layout.sensor_unlock)

        taskType = SensorTaskType.valueOf(
            intent.getStringExtra("task_type") ?: "SWING"
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        handler = Handler(Looper.getMainLooper())

        setupBackgroundImage()
        setupBackButton()

        if (taskType == SensorTaskType.SWING) {
            setupSwingTask()
        } else {
            setupMeditateTask()
        }
    }

    private fun setupBackButton() {
        findViewById<View>(R.id.back_button).setOnClickListener {
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(lockIntent)
            finish()
        }
    }

    private fun setupBackgroundImage() {
        try {
            val backgroundImageView = findViewById<ImageView>(R.id.background_image)
            if (backgroundImages.isNotEmpty()) {
                val randomIndex = backgroundImages.indices.random()
                val selectedBg = backgroundImages[randomIndex]
                backgroundImageView?.setImageResource(selectedBg)
                Log.d(TAG, "任务页面随机选择背景图: bg_${randomIndex + 1}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置背景图失败", e)
        }
    }

    private fun setupSwingTask() {
        findViewById<TextView>(R.id.task_title).text = "动量重置"
        findViewById<TextView>(R.id.task_description).text =
            "紧握手机，完成标准挥剑动作\n连续完成 50 次达标挥击"
        findViewById<View>(R.id.breath_circle).visibility = View.GONE
        //findViewById<TextView>(R.id.meditation_hint).visibility = View.GONE

        updateSwingProgress()
    }

    private fun setupMeditateTask() {
        findViewById<TextView>(R.id.task_title).text = "冥想缓冲"
        findViewById<TextView>(R.id.task_description).text =
            "将手机水平放置在桌面上\n保持绝对静止 5 分钟"
        findViewById<TextView>(R.id.swing_count).visibility = View.GONE

        updateMeditationProgress()
        startBreathAnimation()
        startMeditationTimer()
    }

    private fun startBreathAnimation() {
        breathRunnable = object : Runnable {
            override fun run() {
                val circle = findViewById<View>(R.id.breath_circle)
                val scale = if (circle.scaleX == 1f) 1.5f else 1f
                circle.animate().scaleX(scale).scaleY(scale).setDuration(2000).start()
                handler.postDelayed(this, BREATH_CYCLE_MS)
            }
        }
        breathRunnable?.let { handler.post(it) }
    }

    private fun startMeditationTimer() {
        meditationRunnable = object : Runnable {
            override fun run() {
                if (!meditationPaused && meditationRemaining > 0) {
                    meditationRemaining -= 1000
                    updateMeditationProgress()
                }

                if (meditationRemaining <= 0) {
                    onUnlockSuccess()
                    return
                }

                handler.postDelayed(this, 1000)
            }
        }
        meditationRunnable?.let { handler.post(it) }
    }

    private fun updateSwingProgress() {
        findViewById<TextView>(R.id.swing_count).text = "$swingCount / $SWING_TARGET_COUNT"
    }

    private fun updateMeditationProgress() {
        val minutes = (meditationRemaining / (1000 * 60)).toInt()
        val seconds = ((meditationRemaining % (1000 * 60)) / 1000).toInt()
        findViewById<TextView>(R.id.task_description).text =
            "将手机水平放置在桌面上\n保持绝对静止\n${String.format("%02d:%02d", minutes, seconds)}"
    }

    override fun onResume() {
        super.onResume()
        if (taskType == SensorTaskType.SWING) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        } else {
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            sensorManager.unregisterListener(this)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Sensor listener not registered, ignore unregister error")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        breathRunnable?.let { handler.removeCallbacks(it) }
        meditationRunnable?.let { handler.removeCallbacks(it) }
        try {
            sensorManager.unregisterListener(this)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Sensor listener not registered, ignore unregister error")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val y = event.values[1]
        val z = event.values[2]

        val deltaY = Math.abs(y - lastYAccel)
        val deltaZ = Math.abs(z - lastZAccel)

        val now = System.currentTimeMillis()
        if (now - lastSwingTime > 300) {
            if (deltaY > SWING_THRESHOLD || deltaZ > SWING_THRESHOLD) {
                swingCount++
                lastSwingTime = now
                updateSwingProgress()
                Log.d(TAG, "Swing detected! Count: $swingCount")

                if (swingCount >= SWING_TARGET_COUNT) {
                    onUnlockSuccess()
                }
            }
        }

        lastYAccel = y
        lastZAccel = z
    }

    private fun handleGyroscope(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = Math.abs(x - lastGyroX)
        val deltaY = Math.abs(y - lastGyroY)
        val deltaZ = Math.abs(z - lastGyroZ)

        val totalMovement = deltaX + deltaY + deltaZ

        if (totalMovement > MEDITATE_THRESHOLD) {
            meditationRemaining = MEDITATE_DURATION_MS
            meditationPaused = true
            Toast.makeText(this, "检测到移动！重新开始", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Movement detected! Resetting meditation")
        } else {
            meditationPaused = false
        }

        lastGyroX = x
        lastGyroY = y
        lastGyroZ = z
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun onUnlockSuccess() {
        Toast.makeText(this, "解锁成功！", Toast.LENGTH_SHORT).show()

        val timeStateMachine = TimeStateMachine(this)
        // 重置单次会话时间并清除持久化的锁定时间戳
        timeStateMachine.resetSessionLock()

        // 重置 MonitorService 的锁定标志，允许下次拦截
        com.example.quittiktok.service.MonitorService.resetLockScreenFlag()

        finish()
    }

    override fun onBackPressed() {
        // Block back button
    }

    enum class SensorTaskType {
        SWING, MEDITATE
    }
}