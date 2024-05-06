package com.zu.camerautil.gl

val vertShaderCode = """
    #version 300 es
    layout (location = 0) in vec3 aPos;
    layout (location = 1) in vec2 aTexCoord;
    out vec2 TexCoord;
    uniform mat4 coordTransform;
    void main() {
        vec4 finalPos = coordTransform * vec4(aPos, 1.0f);
        gl_Position = finalPos;
        //gl_Position = vec4(aPos, 1.0f);
        TexCoord = aTexCoord;
    }
""".trimIndent()

val fragShaderCode = """
    #version 300 es
    precision mediump float;
    uniform sampler2D tex;
    in vec2 TexCoord;
    out vec4 FragColor;
    void main() {
        FragColor = texture(tex, TexCoord);
        //FragColor = vec4(1.0, 0.0, 0.0, 1.0);
    }
""".trimIndent()

val oesFragShaderCode = """
    #version 300 es
    #extension GL_OES_EGL_image_external_essl3 : require
    precision mediump float;
    uniform samplerExternalOES tex;
    in vec2 TexCoord;
    out vec4 FragColor;
    void main() {
        FragColor = texture(tex, TexCoord);
        //FragColor = vec4(1.0f, 0.0f, 0.0f, 1.0f);
    }
""".trimIndent()
