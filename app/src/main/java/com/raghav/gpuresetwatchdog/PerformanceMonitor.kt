package com.raghav.gpuresetwatchdog

import android.opengl.GLES30
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Performance monitoring utility for GPU Reset Watchdog.
 * Tracks frame rates, GPU utilization, and shader performance.
 */
class PerformanceMonitor {

    private val frameTimeHistory = ArrayList<Long>()
    private val maxHistorySize = 300 // 5 seconds at 60fps

    private var frameStartTime = 0L
    private var totalFrames = 0L
    private var sessionStartTime = 0L

    private var cachedGpuInfo: GPUInfo? = null

    data class PerformanceStats(
        val averageFPS: Float,
        val minFPS: Float,
        val maxFPS: Float,
        val frameTimeMs: Float,
        val totalFrames: Long,
        val sessionDurationMs: Long,
        val gpuInfo: GPUInfo?
    )

    data class GPUInfo(
        val vendor: String,
        val renderer: String,
        val version: String,
        val extensions: List<String>,
        val maxTextureSize: Int,
        val maxVertexAttribs: Int,
        val maxFragmentUniforms: Int
    )

    fun startSession() {
        sessionStartTime = System.nanoTime()
        totalFrames = 0L
        frameTimeHistory.clear()
        Log.d(TAG, "Performance monitoring session started")
    }

    fun startFrame() {
        frameStartTime = System.nanoTime()
    }

    fun endFrame() {
        val frameEndTime = System.nanoTime()
        val frameTimeNs = frameEndTime - frameStartTime
        val frameTimeMs = frameTimeNs / 1_000_000L

        frameTimeHistory.add(frameTimeMs)
        if (frameTimeHistory.size > maxHistorySize) {
            frameTimeHistory.removeAt(0)
        }

        totalFrames++

        if (totalFrames % 60 == 0L) {
            logCurrentPerformance()
        }
    }

    fun getPerformanceStats(): PerformanceStats {
        if (frameTimeHistory.isEmpty()) {
            return PerformanceStats(0f, 0f, 0f, 0f, totalFrames, 0L, getGPUInfo())
        }

        val frameTimesMs = frameTimeHistory.map { it.toFloat() }
        val avgFrameTime = frameTimesMs.average().toFloat()
        val minFrameTime = frameTimesMs.minOrNull() ?: 0f
        val maxFrameTime = frameTimesMs.maxOrNull() ?: 0f

        val avgFPS = if (avgFrameTime > 0) 1000f / avgFrameTime else 0f
        val maxFPS = if (minFrameTime > 0) 1000f / minFrameTime else 0f
        val minFPS = if (maxFrameTime > 0) 1000f / maxFrameTime else 0f

        val sessionDuration = (System.nanoTime() - sessionStartTime) / 1_000_000L

        return PerformanceStats(
            avgFPS, minFPS, maxFPS, avgFrameTime,
            totalFrames, sessionDuration, getGPUInfo()
        )
    }

    fun logPerformanceReport() {
        val stats = getPerformanceStats()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())

        val report = buildString {
            appendLine("=== GPU Reset Watchdog Performance Report ===")
            appendLine("Timestamp: $timestamp")
            appendLine("Session Duration: ${stats.sessionDurationMs / 1000.0}s")
            appendLine("Total Frames: ${stats.totalFrames}")
            appendLine("Average FPS: ${"%.1f".format(stats.averageFPS)}")
            appendLine("Min FPS: ${"%.1f".format(stats.minFPS)}")
            appendLine("Max FPS: ${"%.1f".format(stats.maxFPS)}")
            appendLine("Average Frame Time: ${"%.2f".format(stats.frameTimeMs)}ms")
            appendLine("")
            stats.gpuInfo?.let {
                appendLine("GPU Information:")
                appendLine("Vendor: ${it.vendor}")
                appendLine("Renderer: ${it.renderer}")
                appendLine("OpenGL Version: ${it.version}")
                appendLine("Max Texture Size: ${it.maxTextureSize}")
                appendLine("Max Vertex Attributes: ${it.maxVertexAttribs}")
                appendLine("Max Fragment Uniforms: ${it.maxFragmentUniforms}")
                appendLine("Extensions Count: ${it.extensions.size}")
            }
            appendLine("===========================================")
        }

        Log.i(TAG, report)
    }

    private fun logCurrentPerformance() {
        if (frameTimeHistory.size >= 60) {
            val recent = frameTimeHistory.takeLast(60)
            val avgFPS = 1000f / (recent.average().toFloat())
            val minFrameTime = recent.minOrNull()?.toFloat() ?: 0f
            val maxFPS = if (minFrameTime > 0) 1000f / minFrameTime else 0f

            Log.d(TAG, "Performance - Avg FPS: ${"%.1f".format(avgFPS)}, " +
                    "Peak FPS: ${"%.1f".format(maxFPS)}, " +
                    "Frame: $totalFrames")
        }
    }

    fun initializeGpuInfo() {
        if (cachedGpuInfo == null) {
            val vendor = GLES30.glGetString(GLES30.GL_VENDOR) ?: "Unknown"
            val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "Unknown"
            val version = GLES30.glGetString(GLES30.GL_VERSION) ?: "Unknown"
            val extensionsString = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
            val extensions = if (extensionsString.isNotEmpty()) {
                extensionsString.split(" ").filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val maxTextureSize = IntArray(1).also {
                GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, it, 0)
            }[0]

            val maxVertexAttribs = IntArray(1).also {
                GLES30.glGetIntegerv(GLES30.GL_MAX_VERTEX_ATTRIBS, it, 0)
            }[0]

            val maxFragmentUniforms = IntArray(1).also {
                GLES30.glGetIntegerv(GLES30.GL_MAX_FRAGMENT_UNIFORM_VECTORS, it, 0)
            }[0]

            cachedGpuInfo = GPUInfo(
                vendor, renderer, version, extensions,
                maxTextureSize, maxVertexAttribs, maxFragmentUniforms
            )
            Log.d(TAG, "GPU info cached.")
        }
    }

    private fun getGPUInfo(): GPUInfo? {
        return cachedGpuInfo
    }

    companion object {
        private const val TAG = "PerformanceMonitor"
    }
}
