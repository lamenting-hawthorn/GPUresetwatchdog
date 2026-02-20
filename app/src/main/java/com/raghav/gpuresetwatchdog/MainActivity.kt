package com.raghav.gpuresetwatchdog

import android.animation.ValueAnimator
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.raghav.gpuresetwatchdog.ui.MainScreen
import com.google.android.gms.ads.MobileAds

class MainActivity : AppCompatActivity() {

    private var gpuStressView: GPUStressView? = null
    private lateinit var handler: Handler

    private var isProcessing by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var progress by mutableStateOf(0f)

    private val defaultDuration = 5000L // 5 seconds
    private var progressAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this) {}

        handler = Handler(Looper.getMainLooper())
        statusText = getString(R.string.status_ready)

        setContent {
            MainScreen(
                isProcessing = isProcessing,
                statusText = statusText,
                progress = progress,
                onStartReset = { startGPUReset() },
                onGpuStressViewCreated = { view ->
                    gpuStressView = view
                    if (isProcessing) {
                        gpuStressView?.startStressSequence()
                    }
                }
            )
        }

        if (intent.getBooleanExtra("auto_start_reset", false)) {
            handler.postDelayed({
                if (!isProcessing) {
                    startGPUReset()
                }
            }, 500)
        }
    }

    private fun startGPUReset() {
        isProcessing = true

        progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = defaultDuration
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
            }
            start()
        }

        handler.postDelayed({
            completeGPUReset()
        }, defaultDuration)

        enterFullscreenMode()
        gpuStressView?.startStressSequence()

        Toast.makeText(this, getString(R.string.toast_starting), Toast.LENGTH_SHORT).show()
    }

    private fun completeGPUReset() {
        if (!isProcessing) return
        gpuStressView?.stopStressSequence()

        exitFullscreenMode()

        isProcessing = false
        statusText = getString(R.string.status_completed)

        Toast.makeText(this, getString(R.string.toast_completed), Toast.LENGTH_SHORT).show()

        handler.postDelayed({
            statusText = getString(R.string.status_ready)
        }, 3000)
    }

    private fun enterFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private fun exitFullscreenMode() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onResume() {
        super.onResume()
        gpuStressView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        gpuStressView?.onPause()

        if (isProcessing) {
            handler.removeCallbacksAndMessages(null)
            progressAnimator?.cancel()
            completeGPUReset()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        progressAnimator?.cancel()
    }
}
