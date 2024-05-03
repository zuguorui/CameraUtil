package com.zu.camerautil.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.camera2.CameraCharacteristics
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.zu.camerautil.camera.computeRotation
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.roundToInt


class GLRender {

    val context: Context
    // 是否已被释放的标志
    var isReleased = false
        private set

    // OpenGL的线程，所有关于OpenGL的操作都在这个线程里
    private val glThread = HandlerThread("gl-thread").apply {
        start()
    }
    private val glHandler = Handler(glThread.looper)

    /**
     * 用于输入的surface，可以给相机使用
     * */
    var inputSurface: InputSurface? = null
        private set

    private lateinit var frameBuffer: FrameBuffer

    /**
     * 输出的surface列表
     * */
    private var outputSurfaceList = ArrayList<OutputSurface>()

    // EGL相关
    private var eglCore: EGLCore? = null
    // 用于从inputSurface渲染到frameBuffer的shader，由于是给相机使用，因此需要oes采样
    private var oesShader: Shader = Shader()
    // 用于从frameBuffer渲染到outputSurface的shader
    private var frameBufferShader: Shader = Shader()

    private var oesVertex = PlaneVertex()
    private var frameBufferVertex = PlaneVertex()

    /**
     * 缩放模式。正常情况下，应使用INSIDE模式来确保所有output的宽高比与预览一致
     * */
    var scaleType = ScaleType.INSIDE
        private set


    /**
     * inputSurface的监听器。
     * */
    var inputSurfaceListener: InputSurfaceListener? = null
        set(value) {
            field = value
            if (inputSurface != null) {
                field?.onSurfaceCreated(inputSurface!!.surface, inputSurface!!.width, inputSurface!!.height)
            }
        }

    /**
     * 相机方向，可以通过[CameraCharacteristics.SENSOR_ORIENTATION]获取到。
     * 有四种取值：0，90，180，270。单位是度
     * */
    var cameraOrientation: Int = 0
        private set

    /**
     * 相机朝向，前置还是后置。可以通过[CameraCharacteristics.LENS_FACING]取到
     * */
    var cameraFacing: Int = CameraCharacteristics.LENS_FACING_BACK
        private set

    /**
     * 输出方向，是view的方向。这个值可以与相机方向来一起计算相机输出的图像要旋转多少度。
     * 在inputSurface输出到frameBuffer时进行纹理旋转，使其与输出方向一致
     * */
    var outputOrientation: Int = 0
        private set

    // 运行统计信息
    private val renderInfo = RenderInfo()

    constructor(context: Context) {
        this.context = context
        isReleased = false
        glHandler.post {
            initInner()
        }
    }

    private fun initInner() {
        Timber.d("initInner")
        // 要先初始化EGL环境，否则OpenGL的函数都不可用
        eglCore = EGLCore()
        val eglCore = eglCore!!
        if (!eglCore.isReady) {
            Timber.e("eglCore is not ready")
            return
        }

        // 初始化inputSurface和frameBuffer这两个和输入有关的
        inputSurface = InputSurface()
        inputSurface!!.surfaceTexture.setOnFrameAvailableListener(
            OnFrameAvailableListener {
                draw()
            },
            glHandler
        )
        frameBuffer = FrameBuffer(inputSurface!!.width, inputSurface!!.height)
        inputSurfaceListener?.onSurfaceCreated(inputSurface!!.surface, inputSurface!!.width, inputSurface!!.height)

        initProgram()
        updateInputVertexTransform()
        initVertex()
    }

    private fun initProgram() {
        if (!oesShader.compile(vertShaderCode, oesFragShaderCode)) {
            Timber.e("compile oes shader failed")
            return
        }
        oesShader.use()
        oesShader.setMat4("coordTransform", eye4())
        oesShader.endUse()

        if (!frameBufferShader.compile(vertShaderCode, fragShaderCode)) {
            Timber.e("compile process shader failed")
            return
        }
        frameBufferShader.use()
        frameBufferShader.setMat4("coordTransform", eye4())
        frameBufferShader.endUse()
    }

    private fun releaseProgram() {
        oesShader.release()
        frameBufferShader.release()
    }

    /**
     * 初始化顶点数据
     * */
    private fun initVertex() {
        // inputSurface的顶点数据，屏幕中间为原点，纹理坐标左下角为原点，向右向上为正。注意oes纹理与屏幕上下是相反的
        val oesVertexData = floatArrayOf(
            -1f, -1f, 0f, 0f, 1f, // 左下
            1f, -1f, 0f, 1f, 1f, // 右下
            1f,  1f, 0f, 1f, 0f, // 右上
            -1f,  1f, 0f, 0f, 0f  // 左上
        )
        oesVertex.init(oesVertexData, VERTEX_INDICES)

        // frameBuffer的顶点数据，它不会将纹理颠倒
        val fbVertexData = floatArrayOf(
            -1f, -1f, 0f, 0f, 0f, // 左下
            1f, -1f, 0f, 1f, 0f, // 右下
            1f,  1f, 0f, 1f, 1f, // 右上
            -1f,  1f, 0f, 0f, 1f  // 左上
        )
        frameBufferVertex.init(fbVertexData, VERTEX_INDICES)
    }

    private fun releaseVertex() {
        oesVertex.release()
        frameBufferVertex.release()
    }

    /**
     * 更新viewport。viewport决定了OpenGL在当前surface上绘制的区域。可以超过实际surface区域，
     * 这会导致纹理被裁切。
     * */
    private fun updateViewport(inputWidth: Int, inputHeight: Int, outputWidth: Int, outputHeight: Int, scaleType: ScaleType): Boolean {
        if (inputWidth <= 0 || inputHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) {
            return false
        }
        //Timber.d("updateViewport: inputWidth = $inputWidth, inputHeight = $inputHeight, outputWidth = $outputWidth, outputHeight = $outputHeight")
        val texWidth = inputWidth
        val texHeight = inputHeight
        val screenWidth = outputWidth
        val screenHeight = outputHeight

        val texW2H = texWidth.toFloat() / texHeight
        val screenW2H = screenWidth.toFloat() / screenHeight

        var left = 0
        var top = 0
        var width = outputWidth
        var height = outputHeight

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

    private fun updateInputVertexTransform() {
        val rotate = computeRotation(cameraOrientation, outputOrientation, cameraFacing)
        var ret = eye4()
        Matrix.setRotateM(ret, 0, -rotate.toFloat(), 0f, 0f, 1f)
        if (oesShader.isReady) {
            oesShader.use()
            oesShader.setMat4("coordTransform", ret)
            oesShader.endUse()
        }
        Timber.d("update transform: camOri = $cameraOrientation, camFacing = $cameraFacing, outputOri = $outputOrientation, rotate = $rotate")
        Timber.d("update transform: mat = \n${matToString(ret, 4, 4)}")
    }

    /**
     * 更新frameBuffer的尺寸，要求必须和inputSurface尺寸一致。但是方向可以不一样。总之保证frameBuffer的方向
     * 始终和outputSurface的方向是一致的
     * */
    private fun updateFrameBufferSize() {
        val inputSurface = inputSurface ?: return
        val rotate = computeRotation(cameraOrientation, outputOrientation, cameraFacing)
        val width: Int
        val height: Int
        if (rotate % 180 == 0) {
            width = inputSurface.width
            height = inputSurface.height
        } else {
            width = inputSurface.height
            height = inputSurface.width
        }
        frameBuffer.setSize(width, height)
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
        val inputSurface = inputSurface ?: return
        inputSurface.setSize(width, height)
        frameBuffer.setSize(width, height)
        updateFrameBufferSize()
        inputSurfaceListener?.onSizeChanged(inputSurface.surface, inputSurface.width, inputSurface.height)
    }

    private fun setCameraOrientationInner(orientation: Int) {
        if (orientation != cameraOrientation) {
            cameraOrientation = orientation
            updateInputVertexTransform()
            updateFrameBufferSize()
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
            updateFrameBufferSize()
        }
    }


    private fun releaseInner() {
        outputSurfaceList.clear()
        inputSurface?.release()
        releaseProgram()
        releaseVertex()
        eglCore?.release()
    }

    private fun isReadyToDraw(): Boolean {
        val eglCore = eglCore ?: return false
        if (!eglCore.isReady) {
            Timber.e("eglCore is not ready")
            return false
        }
        if (!oesShader.isReady) {
            Timber.e("oesShader is not ready")
            return false
        }
        if (!frameBufferShader.isReady) {
            Timber.e("frameBufferShader is not ready")
            return false
        }

        if (!oesVertex.isReady) {
            Timber.e("oesVertex is not ready")
            return false
        }

        if (!frameBufferVertex.isReady) {
            Timber.e("frameBufferVertex is not ready")
            return false
        }

        if (!frameBuffer.isReady) {
            Timber.e("frameBuffer is not ready")
            return false
        }
        return true
    }

    private fun draw() {
        if (renderInfo.fpsStartTime == 0L) {
            renderInfo.fpsStartTime = System.currentTimeMillis()
        }
        val eglCore = eglCore ?: return
        val inputSurface = inputSurface ?: return
        if (!isReadyToDraw()) {
            Timber.e("not ready to draw")
            return
        }

        // 将输入的纹理绘制到frameBuffer上
        inputSurface.surfaceTexture.updateTexImage()
        GLES.glBindFramebuffer(GLES.GL_FRAMEBUFFER, frameBuffer.FBO)
        GLES.glBindVertexArray(oesVertex.VAO)
        oesShader.use()
        GLES.glActiveTexture(GLES.GL_TEXTURE0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, inputSurface.textureId)
        oesShader.setInt("tex", 0)

        GLES.glClearColor(0f, 0f, 0f, 1f)
        GLES.glClear(GLES.GL_COLOR_BUFFER_BIT)
        GLES.glViewport(0, 0, frameBuffer.width, frameBuffer.height)
        GLES.glDrawElements(GLES.GL_TRIANGLES, 6, GLES.GL_UNSIGNED_INT, 0)
        GLES.glBindVertexArray(0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, 0)
        oesShader.endUse()
        GLES.glBindFramebuffer(GLES.GL_FRAMEBUFFER, 0)

        // 将frameBuffer再绘制到outputSurface上
        GLES.glBindVertexArray(frameBufferVertex.VAO)
        frameBufferShader.use()
        GLES.glActiveTexture(GLES.GL_TEXTURE0)
        GLES.glBindTexture(GLES.GL_TEXTURE_2D, frameBuffer.colorTex)
        frameBufferShader.setInt("tex", 0)
        for (outputSurface in outputSurfaceList) {
            eglCore.makeCurrent(outputSurface)
            if (!updateViewport(frameBuffer.width, frameBuffer.height, outputSurface.width, outputSurface.height, scaleType)) {
                Timber.e("updateVertex failed, src.width = ${inputSurface.width}, src.height = ${inputSurface.height}, dst.width = ${outputSurface.width}, dst.height = ${outputSurface.height}")
                continue
            }
            GLES.glClearColor(0f, 0f, 0f, 1f)
            GLES.glClear(GLES.GL_COLOR_BUFFER_BIT)
            GLES.glDrawElements(GLES.GL_TRIANGLES, 6, GLES.GL_UNSIGNED_INT, 0)
            eglCore.swapBuffers(outputSurface)
        }
        GLES.glBindVertexArray(0)
        frameBufferShader.endUse()
        renderInfo.frames++
        renderInfo.fpsEndTime = System.currentTimeMillis()
        if (renderInfo.fpsEndTime - renderInfo.fpsStartTime >= 1000) {
            Timber.e("FPS: ${renderInfo.fps}")
            renderInfo.fpsStartTime = renderInfo.fpsEndTime
            renderInfo.frames = 0
        }
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

    // 渲染的统计信息
    private inner class RenderInfo {
        var fpsStartTime = 0L
        var fpsEndTime = 0L
        var frames = 0
        val fps: Float
            get() {
                val seconds = (fpsEndTime - fpsStartTime).toFloat() / 1000
                return frames / seconds
            }

        var drawStartTime = 0L
        var drawEndTime = 0L
        val drawCost: Int
            get() {
                return (drawEndTime - drawStartTime).toInt()
            }
    }
}