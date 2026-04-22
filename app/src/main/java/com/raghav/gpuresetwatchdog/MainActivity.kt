package com.raghav.gpuresetwatchdog

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.raghav.gpuresetwatchdog.ui.MainScreen
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private var gpuStressView: GPUStressView? = null

    private var isProcessing by mutableStateOf(false)
    private var statusText by mutableStateOf("")
    private var progress by mutableStateOf(0f)

    private val defaultDuration = 5000L // 5 seconds
    private var progressAnimator: ValueAnimator? = null
    private var resetJob: Job? = null
    private var statusResetJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this) { initializationStatus ->
            android.util.Log.d("MainActivity", "AdMob init: $initializationStatus")
        }

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
            lifecycleScope.launch {
                delay(500)
                if (!isProcessing) {
                    startGPUReset()
                }
            }
        }
    }

    private fun startGPUReset() {
        // Runtime OpenGL ES 3.0 capability check — some OEMs incorrectly
        // report support in the manifest filter.
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val glEsVersion = am.deviceConfigurationInfo.reqGlEsVersion
        if (glEsVersion < 0x30000) {
            Toast.makeText(this, getString(R.string.toast_opengl_error), Toast.LENGTH_LONG).show()
            return
        }

        isProcessing = true

        progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = defaultDuration
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
            }
            start()
        }

        resetJob = lifecycleScope.launch {
            delay(defaultDuration)
            completeGPUReset()
        }

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

        statusResetJob = lifecycleScope.launch {
            delay(3000)
            statusText = getString(R.string.status_ready)
        }
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
            resetJob?.cancel()
            statusResetJob?.cancel()
            progressAnimator?.cancel()
            completeGPUReset()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resetJob?.cancel()
        statusResetJob?.cancel()
        progressAnimator?.cancel()
    }
}
