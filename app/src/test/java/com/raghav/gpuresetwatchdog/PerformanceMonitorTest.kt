package com.raghav.gpuresetwatchdog

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PerformanceMonitor frame-time and FPS calculations.
 * These run on the JVM — no Android device required.
 */
class PerformanceMonitorTest {

    private lateinit var monitor: PerformanceMonitor

    @Before
    fun setUp() {
        monitor = PerformanceMonitor()
    }

    @Test
    fun `getPerformanceStats returns zeros when no frames have been recorded`() {
        val stats = monitor.getPerformanceStats()
        assertEquals(0f, stats.averageFPS, 0.001f)
        assertEquals(0f, stats.minFPS, 0.001f)
        assertEquals(0f, stats.maxFPS, 0.001f)
        assertEquals(0L, stats.totalFrames)
    }

    @Test
    fun `totalFrames increments correctly across multiple endFrame calls`() {
        repeat(7) {
            monitor.startFrame()
            monitor.endFrame()
        }
        assertEquals(7L, monitor.getPerformanceStats().totalFrames)
    }

    @Test
    fun `averageFPS and frameTime are positive after recording frames`() {
        repeat(5) {
            monitor.startFrame()
            Thread.sleep(16) // ~60 FPS
            monitor.endFrame()
        }
        val stats = monitor.getPerformanceStats()
        assertTrue("averageFPS should be > 0", stats.averageFPS > 0f)
        assertTrue("frameTimeMs should be > 0", stats.frameTimeMs > 0f)
        assertEquals(5L, stats.totalFrames)
    }

    @Test
    fun `minFPS is less than or equal to maxFPS`() {
        repeat(10) {
            monitor.startFrame()
            Thread.sleep(if (it % 2 == 0) 16L else 33L) // alternating fast / slow frames
            monitor.endFrame()
        }
        val stats = monitor.getPerformanceStats()
        assertTrue(
            "minFPS (${stats.minFPS}) should be <= maxFPS (${stats.maxFPS})",
            stats.minFPS <= stats.maxFPS
        )
    }

    @Test
    fun `stats remain non-negative for a single recorded frame`() {
        monitor.startFrame()
        Thread.sleep(1)
        monitor.endFrame()
        val stats = monitor.getPerformanceStats()
        assertTrue(stats.averageFPS >= 0f)
        assertTrue(stats.minFPS >= 0f)
        assertTrue(stats.maxFPS >= 0f)
        assertTrue(stats.frameTimeMs >= 0f)
    }
}
