# GPU Reset Watchdog

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue.svg)](https://kotlinlang.org)
[![OpenGL ES](https://img.shields.io/badge/OpenGL%20ES-3.0-orange.svg)](https://www.khronos.org/opengles/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-purple.svg)](https://developer.android.com/jetpack/compose)

> A low-level Android utility that stabilizes GPU glitches by driving a custom OpenGL ES 3.0 rendering pipeline through five hand-written GLSL shader programs — forcing a complete GPU pipeline flush in under 5 seconds.

---

<!-- Add a demo GIF or screenshot here — it dramatically increases recruiter engagement
     Recommended: screen-record the app running, convert to GIF, drop it in /assets/ -->

## Download

**[Latest APK → Releases page](../../releases/latest)**

Sideload on any Android 8.0+ device with OpenGL ES 3.0 support. No root required.

---

## What It Does

Android GPU artifacts (screen tearing, rendering glitches, stuck frames) are caused by corrupt state in the GPU driver pipeline. A reboot clears it — but that's slow. This app achieves the same result without rebooting: it forces the GPU driver to flush its entire pipeline by submitting a burst of compute-intensive draw calls across five different shader programs, then using `glReadPixels` to create a hard CPU/GPU synchronization point that drains all pending GPU work.

Accessible in two ways:
- **In-app** — full-screen shader stress sequence with progress indicator
- **Quick Settings tile** — one-tap from the notification shade, no app launch needed

---

## Technical Highlights

### Custom OpenGL ES 3.0 Rendering Pipeline

Five hand-written GLSL fragment shaders, each targeting a different GPU subsystem. They rotate every 60 frames to prevent driver-level caching:

| Shader | Technique | GPU Target |
|---|---|---|
| **Geometry Stress** | Fractal iteration (Mandelbrot variant) + polar trig | ALU / math units |
| **Texture Stress** | 16-layer procedural noise, manual mipmap level computation | Texture samplers |
| **Lighting Stress** | Phong shading with 8 dynamic point lights | Varying interpolation |
| **Post-Processing** | 7×7 Gaussian blur + chromatic aberration | Memory bandwidth |
| **Ray Marching** | 64-step SDF sphere/box intersection with normals | Compute / branching |

The ray marching shader is the most compute-intensive: per-fragment ray–surface intersection against a signed distance field, with analytical normal computation via central differences, all in `highp float` precision.

```glsl
// SDF ray march — 64 steps per fragment, per frame
for(int i = 0; i < 64; i++) {
    float d = sdf(p);          // sphere + box union + displacement field
    if(d < 0.001) break;
    dist += d;
    p += rd * d;
    if(dist > 10.0) break;
}
```

### Per-Frame CPU/GPU Synchronization

After every 10th draw call, a `glReadPixels` call forces a CPU–GPU pipeline sync point — the key mechanism that actually flushes pending GPU work and clears driver-level state:

```kotlin
if (frameCount % 10 == 0L) {
    GLES30.glReadPixels(0, 0, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
}
```

### Correct OpenGL Resource Lifecycle

VBO, VAO, and all shader programs are explicitly released before each surface recreation cycle, preventing GPU memory leaks on repeated pause/resume:

```kotlin
private fun releaseGLResources() {
    shaderPrograms.forEach { if (it != 0) GLES30.glDeleteProgram(it) }
    shaderPrograms.clear()
    GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
    GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
}
```

### System-Level Android Integration

- **[TileService](https://developer.android.com/reference/android/service/quicksettings/TileService)** — registers a Quick Settings tile that starts the reset flow directly from the notification shade, using `PendingIntent` for API 34+ compatibility
- **Fullscreen immersive mode** — `WindowInsetsControllerCompat` hides system bars for the stress duration
- **WakeLock** — keeps the screen on during the reset sequence

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material3 |
| Graphics | OpenGL ES 3.0 · GLSL 300 es |
| Architecture | Single-activity, state hoisting via `mutableStateOf` |
| System APIs | TileService · WindowInsetsController · GLSurfaceView |
| Ads | AdMob (banner) |
| Analytics | Firebase Analytics |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 (Android 15) |

---

## Project Structure

```
app/src/main/java/com/raghav/gpuresetwatchdog/
├── MainActivity.kt          # Activity lifecycle, fullscreen, progress animation
├── GPUStressView.kt         # GLSurfaceView host + 5 GLSL shader programs
├── PerformanceMonitor.kt    # FPS tracking, frame time histogram, GPU capability queries
├── GPUResetTileService.kt   # Quick Settings tile — system-level integration
└── ui/
    ├── MainScreen.kt        # Compose UI: idle state / stress state / banner ad
    └── theme/               # Material3 dynamic color theme
```

---

## Build & Run

### Prerequisites
- Android Studio Hedgehog or later
- Android device / emulator with OpenGL ES 3.0

### Clone & build

```bash
git clone https://github.com/lamenting-hawthorn/GPUresetwatchdog.git
cd GPUresetwatchdog
./gradlew assembleDebug
./gradlew installDebug
```

### Firebase setup (optional)

Firebase Analytics is integrated but not required to build. To enable it:

```bash
cp app/google-services.json.example app/google-services.json
# Fill in your Firebase project values from https://console.firebase.google.com
```

To build **without** Firebase, comment out one line in `app/build.gradle.kts`:

```kotlin
// id("com.google.gms.google-services")
```

### AdMob setup (optional)

The app runs without real AdMob IDs — debug builds automatically use Google's test ad unit IDs. To enable real ads, replace the placeholders in `app/src/main/res/values/strings.xml`:

```xml
<string name="admob_app_id">ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX</string>
<string name="banner_ad_unit_id">ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX</string>
```

### Release signing (optional)

Add to `gradle.properties` (not committed):

```properties
myReleaseStorePassword=your_keystore_password
myReleaseKeyAlias=your_key_alias
myReleaseKeyPassword=your_key_password
```

Place `GpuResetWatchdog.jks` in `app/`. Then: `./gradlew assembleRelease`

---

## Running Tests

```bash
# Unit tests — runs on JVM, no device needed
./gradlew test

# Instrumented tests — requires connected device or emulator
./gradlew connectedAndroidTest
```

Unit tests cover `PerformanceMonitor` FPS and frame-time calculations (pure Kotlin, no Android dependencies).

---

## Known Limitations & Future Work

- [ ] Stress duration is hardcoded at 5 seconds — should be user-configurable
- [ ] Performance metrics (FPS, frame times) are Logcat-only — should surface in UI
- [ ] No graceful fallback for devices that incorrectly report OpenGL ES 3.0 support
- [ ] Firebase Analytics is initialized but no custom events are wired up
- [ ] Instrumented test coverage is minimal

---

## License

[MIT](LICENSE) © Raghav
