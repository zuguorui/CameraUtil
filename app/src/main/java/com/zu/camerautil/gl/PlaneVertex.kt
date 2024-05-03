package com.zu.camerautil.gl

import java.nio.FloatBuffer
import java.nio.IntBuffer

class PlaneVertex {

    val isReady: Boolean
        get() = VAO > 0 && VBO > 0 && EBO > 0

    // 顶点相关
    var VAO: Int = 0
        private set
    var VBO: Int = 0
        private set
    var EBO: Int = 0
        private set

    fun init(vertices: FloatArray, vertexIndices: IntArray) {
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

        val eboBuffer = IntBuffer.allocate(vertexIndices.size).put(vertexIndices).position(0)
        GLES.glBindBuffer(GLES.GL_ELEMENT_ARRAY_BUFFER, EBO)
        GLES.glBufferData(GLES.GL_ELEMENT_ARRAY_BUFFER, vertexIndices.size * Int.SIZE_BYTES, eboBuffer, GLES.GL_STATIC_DRAW)

        GLES.glBindVertexArray(0)
    }

    fun release() {
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
}