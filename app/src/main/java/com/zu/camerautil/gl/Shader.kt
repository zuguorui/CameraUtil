package com.zu.camerautil.gl

import timber.log.Timber
import java.lang.Exception
import kotlin.RuntimeException

class Shader {
    private var id: Int = 0

    val isReady: Boolean
        get() = id > 0

    fun compile(vertexShaderCode: String, fragmentShaderCode: String): Boolean {
        release()
        var vertexShader: Int = 0
        var fragmentShader: Int = 0

        val status = IntArray(0)
        var result = true

        try {
            vertexShader = GLES.glCreateShader(GLES.GL_VERTEX_SHADER)
            GLES.glShaderSource(vertexShader, vertexShaderCode)
            GLES.glCompileShader(vertexShader)
            GLES.glGetShaderiv(vertexShader, GLES.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES.GL_TRUE) {
                val message = "compile vertex shader failed, info = ${GLES.glGetShaderInfoLog(vertexShader)}"
                throw RuntimeException(message)
            }

            fragmentShader = GLES.glCreateShader(GLES.GL_FRAGMENT_SHADER)
            GLES.glShaderSource(fragmentShader, fragmentShaderCode)
            GLES.glCompileShader(fragmentShader)
            GLES.glGetShaderiv(fragmentShader, GLES.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES.GL_TRUE) {
                val message = "compile fragment shader failed, info = ${GLES.glGetShaderInfoLog(fragmentShader)}"
                throw RuntimeException(message)
            }

            id = GLES.glCreateProgram()
            GLES.glAttachShader(id, vertexShader)
            GLES.glAttachShader(id, fragmentShader)

            GLES.glLinkProgram(id)
            GLES.glGetProgramiv(id, GLES.GL_LINK_STATUS, status, 0)
            if (status[0] != GLES.GL_TRUE) {
                val message = "link program failed, info = ${GLES.glGetProgramInfoLog(id)}"
                throw RuntimeException(message)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            release()
            result = false
        }

        if (vertexShader > 0) {
            GLES.glDeleteShader(vertexShader)
        }

        if (fragmentShader > 0) {
            GLES.glDeleteShader(fragmentShader)
        }

        if (result) {
            Timber.d("compile shader succeed")
        } else {
            Timber.e("compile shader failed")
        }

        return result


    }

    fun release() {
        if (id > 0) {
            GLES.glDeleteShader(id)
        }
        id = 0
    }

    fun use() {
        if (id > 0) {
            GLES.glUseProgram(id)
        }
    }

    fun endUse() {
        if (id > 0) {
            GLES.glUseProgram(0)
        }
    }

    fun setBool(name: String, value: Boolean) {
        if (id > 0) {
            GLES.glUniform1i(GLES.glGetUniformLocation(id, name), if (value) 1 else 0)
        }
    }

    fun setInt(name: String, value: Int) {
        if (id > 0) {
            GLES.glUniform1i(GLES.glGetUniformLocation(id, name), value)
        }
    }

    fun setFloat(name: String, value: Float) {
        if (id > 0) {
            GLES.glUniform1f(GLES.glGetUniformLocation(id, name), value)
        }
    }

    fun setVec2(name: String, x: Float, y: Float) {
        if (id > 0) {
            GLES.glUniform2f(GLES.glGetUniformLocation(id, name), x, y)
        }
    }

    fun setVec2(name: String, vec: FloatArray) {
        if (id > 0) {
            GLES.glUniform2fv(GLES.glGetUniformLocation(id, name), 1,  vec, 0)
        }
    }

    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        if (id > 0) {
            GLES.glUniform3f(GLES.glGetUniformLocation(id, name), x, y, z)
        }
    }

    fun setVec3(name: String, vec: FloatArray) {
        if (id > 0) {
            GLES.glUniform3fv(GLES.glGetUniformLocation(id, name), 1,  vec, 0)
        }
    }

    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        if (id > 0) {
            GLES.glUniform4f(GLES.glGetUniformLocation(id, name), x, y, z, w)
        }
    }

    fun setVec4(name: String, vec: FloatArray) {
        if (id > 0) {
            GLES.glUniform4fv(GLES.glGetUniformLocation(id, name), 1, vec, 0)
        }
    }

    protected fun finalize() {
        release()
    }


}