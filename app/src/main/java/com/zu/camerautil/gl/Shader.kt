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

        val status = IntArray(1) {
            GLES.GL_TRUE
        }
        var result = true

        try {
            vertexShader = GLES.glCreateShader(GLES.GL_VERTEX_SHADER)
            GLES.glShaderSource(vertexShader, vertexShaderCode)
            GLES.glCompileShader(vertexShader)
            GLES.glGetShaderiv(vertexShader, GLES.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES.GL_TRUE) {
                val message = "compile vertex shader failed, info = ${GLES.glGetShaderInfoLog(vertexShader)}\ncode = \n$vertexShaderCode"
                throw RuntimeException(message)
            }

            fragmentShader = GLES.glCreateShader(GLES.GL_FRAGMENT_SHADER)
            GLES.glShaderSource(fragmentShader, fragmentShaderCode)
            GLES.glCompileShader(fragmentShader)
            GLES.glGetShaderiv(fragmentShader, GLES.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES.GL_TRUE) {
                val message = "compile fragment shader failed, info = ${GLES.glGetShaderInfoLog(fragmentShader)}\ncode = $fragmentShaderCode"
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
            Timber.d("compile shader succeed, program = $id")
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
        assert(vec.size == 2) {
            "<vec> must be 1x2 or 2x1"
        }
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
        assert(vec.size == 3) {
            "<vec> must be 1x3 or 3x1"
        }
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
        assert(vec.size == 4) {
            "<vec> must be 1x4 or 4x1"
        }
        if (id > 0) {
            GLES.glUniform4fv(GLES.glGetUniformLocation(id, name), 1, vec, 0)
        }
    }

    fun setMat2(name: String, mat: FloatArray) {
        assert(mat.size == 4) {
            "<mat> must be 2x2"
        }
        if (id > 0) {
            GLES.glUniformMatrix2fv(GLES.glGetUniformLocation(id, name), 1, false, mat, 0)
        }
    }

    fun setMat3(name: String, mat: FloatArray) {
        assert(mat.size == 9) {
            "<mat> must be 3x3"
        }
        if (id > 0) {
            GLES.glUniformMatrix3fv(GLES.glGetUniformLocation(id, name), 1, false, mat, 0)
        }
    }

    fun setMat4(name: String, mat: FloatArray) {
        assert(mat.size == 16) {
            "<mat> must be 4x4"
        }
        if (id > 0) {
            GLES.glUniformMatrix4fv(GLES.glGetUniformLocation(id, name), 1, false, mat, 0)
        }
    }

    protected fun finalize() {
        release()
    }


}