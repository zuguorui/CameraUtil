package com.zu.camerautil.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.camera2.CameraCharacteristics
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.zu.camerautil.camera.computeRotation
import timber.log.Timber
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.abs
import kotlin.math.roundToInt


class GLRender {

    val context: Context
    var isReleased = false
        private set

    private val glThread = HandlerThread("gl-thread").apply {
        start()
    }
    private val glHandler = Handler(glThread.looper)

    var inputSurface: InputSurface? = null
        private set

    private var outputSurfaceList = ArrayList<OutputSurface>()

    private var eglCore: EGLCore? = null
    private var oesShader: Shader = Shader()

    var scaleType = ScaleType.FULL
        private set


    var inputSurfaceListener: InputSurfaceListener? = null
        set(value) {
            field = value
            if (inputSurface != null) {
                field?.onSurfaceCreated(inputSurface!!.surface, inputSurface!!.width, inputSurface!!.height)
            }
        }

    private var VAO: Int = 0
    private var VBO: Int = 0
    private var EBO: Int = 0
    // 坐标坐标。屏幕中间为原点，纹理坐标左下角为原点，向右向上为正。注意纹理与屏幕上下是相反的
    private var vertices = floatArrayOf(
        -1f, -1f, 0f, 0f, 1f, // 左下
         1f, -1f, 0f, 1f, 1f, // 右下
         1f,  1f, 0f, 1f, 0f, // 右上
        -1f,  1f, 0f, 0f, 0f  // 左上
    )

    // 输入纹理的坐标转换矩阵
    private var inputVertexTransformMat = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    var cameraOrientation: Int = 0
        private set
    var cameraFacing: Int = CameraCharacteristics.LENS_FACING_BACK
        private set

    var outputOrientation: Int = 0
        private set

    constructor(context: Context) {
        this.context = context
        isReleased = false
        glHandler.post {
            initInner()
        }
    }

    private fun initInner() {
        Timber.d("initInner")
        eglCore = EGLCore()
        val eglCore = eglCore!!
        if (!eglCore.isReady) {
            Timber.e("eglCore is not ready")
            return
        }

        inputSurface = createInputSurface()
        inputSurfaceListener?.onSurfaceCreated(inputSurface!!.surface, inputSurface!!.width, inputSurface!!.height)

        if (!oesShader.compile(vertShaderCode, oesFragShaderCode)) {
            Timber.e("compile oes shader failed")
            return
        }
        updateInputVertexTransform()
        initVertex()
    }

    private fun initVertex() {
        val vao = IntArray(1)
        val vbo = IntArray(1)
        val ebo = IntArray(1)
        GLES.glGenVertexArrays(1, vao, 0)
        GLES.glGenBuffers(1, vbo, 0)
        GLES.glGenBuffers(1, ebo, 0)

        VAO = vao[0]
        VBO = vbo[0]
        EBO = ebo[0]

        GLES.glBindVertexArray(VAO)
        GLES.glBindBuffer(GLES.GL_ARRAY_BUFFER, VBO)
        val vboBuffer = FloatBuffer.allocate(vertices.size).put(vertices).position(0)
        GLES.glBufferData(GLES.GL_ARRAY_BUFFER, vertices.size * Float.SIZE_BYTES, vboBuffer, GLES.GL_STATIC_DRAW)
        GLES.glVertexAttribPointer(0, 3, GLES.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 0)
        GLES.glEnableVertexAttribArray(0)
        GLES.glVertexAttribPointer(1, 2, GLES.GL_FLOAT, false, 5 * Float.SIZE_BYTES, 3 * Float.SIZE_BYTES)
        GLES.glEnableVertexAttribArray(1)

        val eboBuffer = IntBuffer.allocate(VERTEX_INDICES.size).put(VERTEX_INDICES).position(0)
        GLES.glBindBuffer(GLES.GL_ELEMENT_ARRAY_BUFFER, EBO)
        GLES.glBufferData(GLES.GL_ELEMENT_ARRAY_BUFFER, VERTEX_INDICES.size * Int.SIZE_BYTES, eboBuffer, GLES.GL_STATIC_DRAW)

        GLES.glBindVertexArray(0)
    }

    private fun releaseVertex() {
        if (VAO > 0) {
            GLES.glDeleteVertexArrays(1, intArrayOf(VAO), 0)
            VAO = 0
        }
        if (VBO > 0) {
            GLES.glDeleteBuffers(1, intArrayOf(VBO), 0)
            VBO = 0
        }
        if (EBO > 0) {
            GLES.glDeleteBuffers(1, intArrayOf(EBO), 0)
            EBO = 0
        }
    }

    private fun updateViewport(inputSurface: InputSurface, outputSurface: OutputSurface, scaleType: ScaleType): Boolean {

        inputSurface.run {
            if (width <= 0 || height <= 0) {
                Timber.e("inputSurface size not correct, width = $width, height = $height")
                return false
            }
        }

        outputSurface.run {
            if (width <= 0 || height <= 0) {
                Timber.e("outputSurface size not correct, width = $width, height = $height")
                return false
            }
        }

        val rotation = abs(computeRotation(cameraOrientation, outputOrientation, cameraFacing))

        val a = rotation % 180

        val texWidth: Int
        val texHeight: Int

        if (a == 0) {
            // 输入和输出的宽高是对应的
            texWidth = inputSurface.width
            texHeight = inputSurface.height
        } else {
            // 输入的宽高分别对应输出的高宽
            texWidth = inputSurface.height
            texHeight = inputSurface.width
        }

        val screenWidth = outputSurface.width
        val screenHeight = outputSurface.height

        val texW2H = texWidth.toFloat() / texHeight
        val screenW2H = screenWidth.toFloat() / screenHeight

        var left = 0
        var top = 0
        var width = outputSurface.width
        var height = outputSurface.height

        when (scaleType) {
            ScaleType.SCALE_FULL -> {
                // 什么也不做，用初始值即可
            }
            ScaleType.FULL -> {
                // 全屏显示，保持宽高比
                if (texW2H >= screenW2H) {
                    // 纹理比屏幕宽，则纹理高度缩放至与屏幕高度相同
                    height = screenHeight
                    width = (screenHeight * texW2H).roundToInt()
                    top = 0
                    left = -(width - screenWidth) / 2
                } else {
                    // 纹理比屏幕高，则纹理宽度缩放至与屏幕宽度相同
                    width = screenWidth
                    height = (width / texW2H).roundToInt()
                    left = 0
                    top = -(height - screenHeight) / 2
                }
            }
            ScaleType.INSIDE -> {
                // 纹理完全显示，保持宽高比
                if (texW2H >= screenW2H) {
                    // 纹理比屏幕宽，保持屏幕宽度，裁剪屏幕高度。屏幕上下有黑边
                    width = screenWidth
                    height = (width / texW2H).roundToInt()
                    left = 0
                    top = -(height - screenHeight) / 2
                } else {
                    // 纹理比屏幕高，保持屏幕高度，裁剪屏幕宽度，屏幕左右有黑边
                    height = screenHeight
                    width = (screenHeight * texW2H).roundToInt()
                    top = 0
                    left = -(width - screenWidth) / 2
                }
            }
        }
        GLES.glViewport(left, top, width, height)

        return true
    }

    private fun addOutputSurfaceInner(surfaceObj: Any, width: Int, height: Int) {
        val eglCore = eglCore ?: return
        val outputSurface = OutputSurface(eglCore, surfaceObj, width, height)
        if (!outputSurface.isReady) {
            Timber.e("addSurfaceInner failed")
            return
        }
        outputSurfaceList.add(outputSurface)
    }

    private fun removeOutputSurfaceInner(surfaceObj: Any) {
        outputSurfaceList.removeIf {
            it.surface == surfaceObj
        }
    }


    private fun setOutputSizeInner(surfaceObj: Any, width: Int, height: Int) {
        val target = outputSurfaceList.find {
            it.surface == surfaceObj
        }
        target?.setSize(width, height)
    }


    private fun setInputSizeInner(width: Int, height: Int) {
        inputSurface?.let {
            it.setSize(width, height)
            inputSurfaceListener?.onSizeChanged(it.surface, it.width, it.height)
        }
    }

    private fun updateInputVertexTransform() {
        val rotate = computeRotation(cameraOrientation, outputOrientation, cameraFacing)
        var ret = eye4()
        rotate(ret, -rotate, 0f, 0f, 1f)
        ret.copyInto(inputVertexTransformMat)
        if (oesShader.isReady) {
            oesShader.use()
            oesShader.setMat4("coordTransform", inputVertexTransformMat)
            oesShader.endUse()
        }
        Timber.d("update transform: camOri = $cameraOrientation, camFacing = $cameraFacing, outputOri = $outputOrientation, rotate = $rotate")
        Timber.d("update transform: mat = \n${matToString(inputVertexTransformMat, 4, 4)}")
    }

    private fun setCameraOrientationInner(orientation: Int) {
        if (orientation != cameraOrientation) {
            cameraOrientation = orientation
            updateInputVertexTransform()
        }
    }

    private fun setCameraFacingInner(facing: Int) {
        if (facing != cameraFacing) {
            cameraFacing = facing
            updateInputVertexTransform()
        }
    }

    private fun setOutputOrientationInner(orientation: Int) {
        if (orientation != outputOrientation) {
            outputOrientation = orientation
            updateInputVertexTransform()
        }
    }


    private fun releaseInner() {
        outputSurfaceList.clear()
        inputSurface?.release()
        oesShader?.release()
        releaseVertex()
        eglCore?.release()
    }

    private var frameLogCount = 0
    private fun createInputSurface(): InputSurface {
        val tex = IntArray(1)
        GLES.glGenTextures(1, tex, 0)
        GLES.glBindTexture(GLESExt.GL_TEXTURE_EXTERNAL_OES, tex[0])
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_MIN_FILTER,
            GLES.GL_LINEAR
        )
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_MAG_FILTER,
            GLES.GL_LINEAR
        )
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_WRAP_S,
            GLES.GL_CLAMP_TO_EDGE
        )
        GLES.glTexParameteri(
            GLESExt.GL_TEXTURE_EXTERNAL_OES,
            GLES.GL_TEXTURE_WRAP_T,
            GLES.GL_CLAMP_TO_EDGE
        )
        GLES.glBindTexture(GLESExt.GL_TEXTURE_EXTERNAL_OES, 0)
        var inputSurface = InputSurface(tex[0])
        inputSurface.surfaceTexture.setOnFrameAvailableListener(
            OnFrameAvailableListener {
                if (frameLogCount >= 20) {
                    //Timber.d("frame update")
                    frameLogCount = 0
                } else {
                    frameLogCount++
                }
                draw()
            },
            glHandler
        )
        return inputSurface
    }

    private fun draw() {
        val eglCore = eglCore ?: return
        val inputSurface = inputSurface ?: return
        if (!eglCore.isReady) {
            Timber.e("eglCore is not ready")
            return
        }
        if (!oesShader.isReady) {
            Timber.e("oesShader is not ready")
            return
        }

        inputSurface.surfaceTexture.updateTexImage()

        oesShader.use()
        oesShader.setMat4("coordTransform", inputVertexTransformMat)
        GLES.glActiveTexture(GLES.GL_TEXTURE0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, inputSurface.textureId)
        oesShader.setInt("tex", 0)

        GLES.glBindVertexArray(VAO)
        for (outputSurface in outputSurfaceList) {
            eglCore.makeCurrent(outputSurface)

            if (!updateViewport(inputSurface, outputSurface, scaleType)) {
                Timber.e("updateVertex failed, src.width = ${inputSurface.width}, src.height = ${inputSurface.height}, dst.width = ${outputSurface.width}, dst.height = ${outputSurface.height}")
                continue
            }

            GLES.glClearColor(0f, 0f, 0f, 1f)
            GLES.glClear(GLES.GL_COLOR_BUFFER_BIT)
            GLES.glDrawElements(GLES.GL_TRIANGLES, 6, GLES.GL_UNSIGNED_INT, 0)
            eglCore.swapBuffers(outputSurface)
        }
        GLES.glBindVertexArray(0)
        oesShader.endUse()
    }

    fun addOutputSurface(surfaceObj: Any, width: Int, height: Int) {
        if (surfaceObj !is Surface && surfaceObj !is SurfaceTexture) {
            return
        }
        glHandler.post {
            addOutputSurfaceInner(surfaceObj, width, height)
        }
    }

    fun removeOutputSurface(surfaceObj: Any) {
        if (surfaceObj !is Surface && surfaceObj !is SurfaceTexture) {
            return
        }
        glHandler.post {
            removeOutputSurfaceInner(surfaceObj)
        }
    }

    fun setOutputSize(surfaceObj: Any, width: Int, height: Int) {
        if (surfaceObj !is Surface && surfaceObj !is SurfaceTexture) {
            return
        }
        glHandler.post {
            setOutputSizeInner(surfaceObj, width, height)
        }
    }

    fun setInputSize(width: Int, height: Int) {
        glHandler.post {
            setInputSizeInner(width, height)
        }
    }

    fun setCameraOrientation(orientation: Int) {
        glHandler.post {
            setCameraOrientationInner(orientation)
        }
    }

    fun setCameraFacing(facing: Int) {
        glHandler.post {
            setCameraFacingInner(facing)
        }
    }

    fun setOutputOrientation(orientation: Int) {
        glHandler.post {
            setOutputOrientationInner(orientation)
        }
    }


    fun release() {
        glHandler.post {
            releaseInner()
        }
        glThread.quitSafely()
    }


    companion object {
        private val VERTEX_INDICES = intArrayOf(
            0, 3, 2,
            2, 1, 0
        )
    }

    enum class ScaleType {
        // 纹理完整在屏幕内显示，保持宽高比。可能会在屏幕内留黑边
        INSIDE,
        // 纹理占满整个屏幕，保持宽高比。可能纹理的一部分会超出屏幕范围
        FULL,
        // 纹理占满整个屏幕，并且缩放成与屏幕一样的宽高比。可能会导致纹理变形
        SCALE_FULL;
    }

    interface InputSurfaceListener {
        fun onSurfaceCreated(surface: Surface, width: Int, height: Int)
        fun onSizeChanged(surface: Surface, width: Int, height: Int)
    }
}