package com.raghav.gpuresetwatchdog

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
class GPUStressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer: GPUStressRenderer
    var onSurfaceCreatedCallback: (() -> Unit)? = null

    init {
        setEGLContextClientVersion(3) // OpenGL ES 3.0
        renderer = GPUStressRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun startStressSequence() {
        renderer.startStressSequence()
    }

    fun stopStressSequence() {
        renderer.stopStressSequence()
    }

    inner class GPUStressRenderer : Renderer {

        private var shaderPrograms = mutableListOf<Int>()
        private var currentShaderIndex = 0
        private var frameCount = 0L
        private var startTime = 0L
        private var isStressing = false
        private val performanceMonitor = PerformanceMonitor()

        private val quadVertices = floatArrayOf(
            -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f, 1.0f
        )

        private lateinit var vertexBuffer: FloatBuffer
        private var vbo = 0
        private var vao = 0

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

            releaseGLResources()
            setupBuffers()
            createShaderPrograms()
            performanceMonitor.initializeGpuInfo()
            onSurfaceCreatedCallback?.invoke()

            Log.d("GPUStressRenderer", "OpenGL ES ${GLES30.glGetString(GLES30.GL_VERSION)}")
            Log.d("GPUStressRenderer", "GPU: ${GLES30.glGetString(GLES30.GL_RENDERER)}")
        }

        private fun setupBuffers() {
            vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            vertexBuffer.put(quadVertices)
            vertexBuffer.position(0)

            val buffers = IntArray(2)
            GLES30.glGenBuffers(1, buffers, 0)
            vbo = buffers[0]

            GLES30.glGenVertexArrays(1, buffers, 1)
            vao = buffers[1]

            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                quadVertices.size * 4,
                vertexBuffer,
                GLES30.GL_STATIC_DRAW
            )

            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 5 * 4, 0)
            GLES30.glEnableVertexAttribArray(0)

            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 5 * 4, 3 * 4)
            GLES30.glEnableVertexAttribArray(1)

            GLES30.glBindVertexArray(0)
        }

        private fun createShaderPrograms() {
            shaderPrograms.add(createShaderProgram(vertexShader, geometryStressFragment))
            shaderPrograms.add(createShaderProgram(vertexShader, textureStressFragment))
            shaderPrograms.add(createShaderProgram(vertexShader, lightingStressFragment))
            shaderPrograms.add(createShaderProgram(vertexShader, postProcessingStressFragment))
            shaderPrograms.add(createShaderProgram(vertexShader, computeIntensiveFragment))

            Log.d("GPUStressRenderer", "Created ${shaderPrograms.size} shader programs")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0, 0, width, height)

            shaderPrograms.forEach { program ->
                GLES30.glUseProgram(program)
                val resolutionLocation = GLES30.glGetUniformLocation(program, "u_resolution")
                if (resolutionLocation != -1) {
                    GLES30.glUniform2f(resolutionLocation, width.toFloat(), height.toFloat())
                }
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            if (isStressing) {
                performanceMonitor.startFrame()
            }

            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

            if (!isStressing) return

            frameCount++
            val currentTime = System.currentTimeMillis()

            if (startTime == 0L) {
                startTime = currentTime
            }

            if (frameCount % 60 == 0L) {
                currentShaderIndex = (currentShaderIndex + 1) % shaderPrograms.size
            }

            val program = shaderPrograms[currentShaderIndex]
            GLES30.glUseProgram(program)

            updateUniforms(program, currentTime)

            GLES30.glBindVertexArray(vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glBindVertexArray(0)

            if (frameCount % 10 == 0L) {
                val pixel = ByteArray(4)
                GLES30.glReadPixels(0, 0, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.wrap(pixel))
            }

            if (isStressing) {
                performanceMonitor.endFrame()
            }
        }

        private fun updateUniforms(program: Int, currentTime: Long) {
            val time = (currentTime - startTime) / 1000.0f

            val timeLocation = GLES30.glGetUniformLocation(program, "u_time")
            if (timeLocation != -1) {
                GLES30.glUniform1f(timeLocation, time)
            }

            val frameLocation = GLES30.glGetUniformLocation(program, "u_frame")
            if (frameLocation != -1) {
                GLES30.glUniform1i(frameLocation, frameCount.toInt())
            }
        }

        fun startStressSequence() {
            isStressing = true
            startTime = 0L
            frameCount = 0L
            currentShaderIndex = 0
            performanceMonitor.startSession()
        }

        fun stopStressSequence() {
            isStressing = false
            performanceMonitor.logPerformanceReport()
        }

        private fun releaseGLResources() {
            if (shaderPrograms.isNotEmpty()) {
                shaderPrograms.forEach { program ->
                    if (program != 0) GLES30.glDeleteProgram(program)
                }
                shaderPrograms.clear()
            }
            if (vbo != 0) {
                GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
                vbo = 0
            }
            if (vao != 0) {
                GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
                vao = 0
            }
        }

        private fun createShaderProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

            val program = GLES30.glCreateProgram()
            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)
            GLES30.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val error = GLES30.glGetProgramInfoLog(program)
                Log.e("GPUStressRenderer", "Program linking failed: $error")
                GLES30.glDeleteProgram(program)
                return 0
            }

            GLES30.glDeleteShader(vertexShader)
            GLES30.glDeleteShader(fragmentShader)

            return program
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val error = GLES30.glGetShaderInfoLog(shader)
                Log.e("GPUStressRenderer", "Shader compilation failed: $error")
                GLES30.glDeleteShader(shader)
                return 0
            }

            return shader
        }

        private val vertexShader = """
    #version 300 es
    layout(location = 0) in vec3 a_position;
    layout(location = 1) in vec2 a_texCoord;
    
    out vec2 v_texCoord;
    out vec2 v_fragCoord;
    
    uniform vec2 u_resolution;
    
    void main() {
        gl_Position = vec4(a_position, 1.0);
        v_texCoord = a_texCoord;
        v_fragCoord = a_texCoord * u_resolution;
    }
""".trimIndent()

        private val geometryStressFragment = """
    #version 300 es
    precision highp float;
    
    in vec2 v_texCoord;
    in vec2 v_fragCoord;
    out vec4 fragColor;
    
    uniform float u_time;
    uniform int u_frame;
    uniform vec2 u_resolution;
    
    void main() {
        vec2 uv = v_fragCoord / u_resolution;
        uv = uv * 2.0 - 1.0;
        
        float r = length(uv);
        float a = atan(uv.y, uv.x);
        
        float pattern = 0.0;
        for (int i = 0; i < 20; i++) {
            float fi = float(i);
            pattern += sin(r * 10.0 + u_time * 2.0 + fi) * 
                      cos(a * 8.0 + u_time * 1.5 + fi) * 
                      (1.0 / (fi + 1.0));
        }
        
        vec2 z = uv;
        float fractal = 0.0;
        for (int i = 0; i < 15; i++) {
            z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + uv * 0.5;
            fractal += 1.0 / (1.0 + dot(z, z));
        }
        
        vec3 color = vec3(
            sin(pattern + u_time) * 0.5 + 0.5,
            cos(pattern + u_time + 2.094) * 0.5 + 0.5,
            sin(fractal + u_time + 4.188) * 0.5 + 0.5
        );
        
        fragColor = vec4(color, 1.0);
    }
""".trimIndent()

        private val textureStressFragment = """
    #version 300 es
    precision highp float;
    
    in vec2 v_texCoord;
    in vec2 v_fragCoord;
    out vec4 fragColor;
    
    uniform float u_time;
    uniform int u_frame;
    uniform vec2 u_resolution;
    
    vec4 sampleNoise(vec2 coord) {
        return fract(sin(vec4(
            dot(coord, vec2(12.9898, 78.233)),
            dot(coord, vec2(93.9898, 67.345)),
            dot(coord, vec2(45.1234, 89.567)),
            dot(coord, vec2(67.8901, 23.456))
        )) * 43758.5453);
    }
    
    void main() {
        vec2 uv = v_fragCoord / u_resolution;
        
        vec4 color = vec4(0.0);
        
        for (int i = 0; i < 16; i++) {
            float scale = pow(2.0, float(i) * 0.5);
            vec2 offset = vec2(sin(u_time + float(i)), cos(u_time + float(i))) * 0.1;
            
            vec4 sample = sampleNoise((uv + offset) * scale);
            color += sample * (1.0 / scale);
        }
        
        vec2 dx = dFdx(uv * 64.0);
        vec2 dy = dFdy(uv * 64.0);
        float mipLevel = 0.5 * log2(max(dot(dx, dx), dot(dy, dy)));
        
        color *= (1.0 + mipLevel * 0.1);
        
        fragColor = vec4(color.rgb, 1.0);
    }
""".trimIndent()

        private val lightingStressFragment = """
    #version 300 es
    precision highp float;
    
    in vec2 v_texCoord;
    in vec2 v_fragCoord;
    out vec4 fragColor;
    
    uniform float u_time;
    uniform int u_frame;
    uniform vec2 u_resolution;
    
    vec3 calculateLighting(vec3 pos, vec3 normal, vec3 lightPos, vec3 lightColor) {
        vec3 lightDir = normalize(lightPos - pos);
        vec3 viewDir = normalize(-pos);
        vec3 reflectDir = reflect(-lightDir, normal);
        
        float diff = max(dot(normal, lightDir), 0.0);
        float spec = pow(max(dot(viewDir, reflectDir), 0.0), 64.0);
        
        return lightColor * (diff + spec * 0.5);
    }
    
    void main() {
        vec2 uv = v_fragCoord / u_resolution;
        uv = uv * 2.0 - 1.0;
        
        vec3 pos = vec3(uv, sin(length(uv) * 10.0 - u_time * 3.0) * 0.1);
        
        float h = 0.01;
        vec3 normal = normalize(vec3(
            sin(length(uv + vec2(h, 0.0)) * 10.0 - u_time * 3.0) - pos.z,
            sin(length(uv + vec2(0.0, h)) * 10.0 - u_time * 3.0) - pos.z,
            2.0 * h
        ));
        
        vec3 totalLight = vec3(0.0);
        
        for (int i = 0; i < 8; i++) {
            float angle = float(i) * 0.785398 + u_time;
            vec3 lightPos = vec3(cos(angle) * 2.0, sin(angle) * 2.0, 1.5);
            vec3 lightColor = vec3(
                sin(angle + u_time) * 0.5 + 0.5,
                cos(angle + u_time + 2.094) * 0.5 + 0.5,
                sin(angle + u_time + 4.188) * 0.5 + 0.5
            );
            
            totalLight += calculateLighting(pos, normal, lightPos, lightColor);
        }
        
        totalLight += vec3(0.1, 0.1, 0.15);
        
        fragColor = vec4(totalLight, 1.0);
    }
""".trimIndent()

        private val postProcessingStressFragment = """
    #version 300 es
    precision highp float;
    
    in vec2 v_texCoord;
    in vec2 v_fragCoord;
    out vec4 fragColor;
    
    uniform float u_time;
    uniform int u_frame;
    uniform vec2 u_resolution;
    
    void main() {
        vec2 uv = v_fragCoord / u_resolution;
        
        vec3 color = vec3(
            sin(uv.x * 10.0 + u_time) * 0.5 + 0.5,
            cos(uv.y * 10.0 + u_time + 2.094) * 0.5 + 0.5,
            sin((uv.x + uv.y) * 7.0 + u_time + 4.188) * 0.5 + 0.5
        );
        
        vec3 blurred = vec3(0.0);
        float totalWeight = 0.0;
        
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                vec2 offset = vec2(float(x), float(y)) / u_resolution * 2.0;
                vec2 sampleUV = uv + offset;
                
                if (sampleUV.x >= 0.0 && sampleUV.x <= 1.0 && sampleUV.y >= 0.0 && sampleUV.y <= 1.0) {
                    float weight = exp(-(float(x*x + y*y)) * 0.5);
                    vec3 sampleColor = vec3(
                        sin(sampleUV.x * 10.0 + u_time) * 0.5 + 0.5,
                        cos(sampleUV.y * 10.0 + u_time + 2.094) * 0.5 + 0.5,
                        sin((sampleUV.x + sampleUV.y) * 7.0 + u_time + 4.188) * 0.5 + 0.5
                    );
                    blurred += sampleColor * weight;
                    totalWeight += weight;
                }
            }
        }
        if (totalWeight > 0.0) {
            blurred /= totalWeight;
        }
        
        color = mix(color, blurred, 0.5);
        color = pow(color, vec3(0.8));
        color = color * 1.2 - 0.1;
        
        vec2 center = uv - 0.5;
        float aberration = length(center) * 0.02;
        color.r = mix(color.r, sin((uv.x + aberration) * 10.0 + u_time) * 0.5 + 0.5, 0.3);
        color.b = mix(color.b, sin((uv.x - aberration) * 10.0 + u_time + 4.188) * 0.5 + 0.5, 0.3);
        
        fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
    }
""".trimIndent()

        private val computeIntensiveFragment = """
    #version 300 es
    precision highp float;
    
    in vec2 v_texCoord;
    in vec2 v_fragCoord;
    out vec4 fragColor;
    
    uniform float u_time;
    uniform int u_frame;
    uniform vec2 u_resolution;
    
    mat3 rotateZ(float angle) {
        float c = cos(angle);
        float s = sin(angle);
        return mat3(c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
    }
    
    float sdf(vec3 p) {
        vec3 p1 = rotateZ(u_time) * p;
        float sphere = length(p1) - 1.0;
        
        float box = max(abs(p.x) - 0.5, max(abs(p.y) - 0.5, abs(p.z) - 0.5));

        float displacement = sin(10.0 * p.x) * sin(10.0 * p.y) * sin(10.0 * p.z) * 0.1;

        return min(sphere, box) + displacement;
    }

    vec3 getNormal(vec3 p) {
        vec2 e = vec2(0.001, 0.0);
        return normalize(vec3(
            sdf(p + e.xyy) - sdf(p - e.xyy),
            sdf(p + e.yxy) - sdf(p - e.yxy),
            sdf(p + e.yyx) - sdf(p - e.yyx)
        ));
    }

    void main() {
        vec2 uv = (v_fragCoord * 2.0 - u_resolution.xy) / u_resolution.y;

        vec3 ro = vec3(0.0, 0.0, -3.0);
        vec3 rd = normalize(vec3(uv, 1.0));

        float dist = 0.0;
        vec3 p = ro;
        
        for(int i = 0; i < 64; i++) {
            float d = sdf(p);
            if(d < 0.001) break;
            dist += d;
            p += rd * d;
            if(dist > 10.0) break;
        }

        vec3 color = vec3(0.0);
        if (dist < 10.0) {
            vec3 normal = getNormal(p);
            vec3 lightDir = normalize(vec3(sin(u_time), 0.5, -1.0));
            float diff = max(dot(normal, lightDir), 0.0);
            color = vec3(0.8, 0.5, 0.3) * diff + vec3(0.1);
        }
        
        fragColor = vec4(color, 1.0);
    }
""".trimIndent()
    }
}