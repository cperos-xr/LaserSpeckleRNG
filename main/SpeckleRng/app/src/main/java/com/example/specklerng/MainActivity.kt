package com.example.specklerng

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.specklerng.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var rngService: RngService? = null
    private var camera: Camera? = null
    private var isPowerSaveEnabled = false
    private var originalBrightness: Float = 0.5f
    private val resultDisplayHandler = Handler(Looper.getMainLooper())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as RngService.RngBinder
            rngService = binder.getService()
            observeCameraReady()
            observeStatus()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            rngService = null
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (isPowerSaveEnabled) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryPct = level * 100 / scale.toFloat()

                if (batteryPct < 20) {
                    setScreenBrightness(0.05f) // Dim screen
                } else {
                    setScreenBrightness(originalBrightness) // Restore brightness
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        originalBrightness = Settings.System.getFloat(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f

        Intent(this, RngService::class.java).also { intent ->
            ContextCompat.startForegroundService(this, intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        binding.overlayText.text = "Waiting for service..."
        binding.urlText.text = getDeviceIpAddress()
        binding.pinText.text = "Admin PIN: 123456"
        binding.diceResultText.setShadowLayer(10f, 0f, 0f, Color.BLACK)

        setupDiceButtons()

        binding.apiHelpButton.setOnClickListener {
            showApiHelpDialog()
        }

        binding.toggleOverlayButton.setOnClickListener {
            binding.towerOverlay.visibility = if (binding.towerOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        binding.powerSaveSwitch.setOnCheckedChangeListener { _, isChecked ->
            isPowerSaveEnabled = isChecked
            if (!isChecked) {
                setScreenBrightness(originalBrightness) // Restore brightness when disabled
            }
        }

        binding.focusSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setManualFocus(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun setupDiceButtons() {
        binding.d4Button.setOnClickListener { rollDice(4) }
        binding.d6Button.setOnClickListener { rollDice(6) }
        binding.d8Button.setOnClickListener { rollDice(8) }
        binding.d10Button.setOnClickListener { rollDice(10) }
        binding.d12Button.setOnClickListener { rollDice(12) }
        binding.d20Button.setOnClickListener { rollDice(20) }
        binding.d100Button.setOnClickListener { rollDice(100) }
        binding.coinButton.setOnClickListener { rollDice(2) } // 1 for heads, 2 for tails
    }

    private fun rollDice(sides: Int) {
        rngService?.let {
            val result = it.getNumber(1, sides)
            binding.diceResultText.text = result.toString()
            binding.diceResultText.visibility = View.VISIBLE

            // Hide the result after a few seconds
            resultDisplayHandler.removeCallbacksAndMessages(null)
            resultDisplayHandler.postDelayed({ binding.diceResultText.visibility = View.GONE }, 3000)
        }
    }

    private fun setScreenBrightness(brightness: Float) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setManualFocus(progress: Int) {
        camera?.let { cam ->
            val camera2CameraInfo = Camera2CameraInfo.from(cam.cameraInfo)
            val minFocusDist = camera2CameraInfo.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f

            val focusDiopter = (progress / 100f) * minFocusDist

            val camera2CameraControl = Camera2CameraControl.from(cam.cameraControl)
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDiopter)
                .build()
            
            camera2CameraControl.setCaptureRequestOptions(captureRequestOptions)
        }
    }

    private fun showApiHelpDialog() {
        val helpText = rngService?.apiHelpText ?: "Service not connected."
        AlertDialog.Builder(this)
            .setTitle("API Help")
            .setMessage(helpText)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun observeCameraReady() {
        lifecycleScope.launch {
            rngService?.isCameraReady?.filter { it }?.collect { isReady ->
                if (isReady) {
                    camera = rngService?.bindCameraUseCases(binding.viewFinder.surfaceProvider)
                }
            }
        }
    }

    private fun observeStatus() {
        lifecycleScope.launch {
            rngService?.status?.collect { status ->
                status?.let {
                    val text = "fps: %.1f | diffStd: %.2f | uptime: %ds | sha256: %s".format(
                        it.fps, it.diff_std, it.uptime_ms / 1000, it.last_sha256_diff_prefix
                    )
                    runOnUiThread {
                        binding.overlayText.text = text
                    }
                }
            }
        }
    }

    private fun getDeviceIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ipAddress: Int = wifiManager.connectionInfo.ipAddress
        @Suppress("DEPRECATION")
        return Formatter.formatIpAddress(ipAddress)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
        unregisterReceiver(batteryReceiver)
        resultDisplayHandler.removeCallbacksAndMessages(null)
    }
}