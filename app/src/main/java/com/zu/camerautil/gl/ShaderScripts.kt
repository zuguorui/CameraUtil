package com.zu.camerautil.gl

val vertShaderCode = """
    #version 300 es
    layout (location = 0) in vec3 aPos;
    layout (location = 1) in vec2 aTexCoord;
    out vec2 TexCoord;
    void main() {
        gl_Position = vec4(aPos, 1.0f);
        TexCoord = aTexCoord;
    }
""".trimIndent()

val fragShaderCode = """
    #version 300 se
    uniform sampler2D tex;
    in vec2 TexCoord;
    out vec4 FragColor;
    void main() {
        FragColor = texture(tex, TexCoord);
    }
""".trimIndent()

val oesFragShaderCode = """
    #version 300 se
    #extension GL_OES_EGL_image_external : require
    uniform samplerExternalOES tex;
    in vec2 TexCoord;
    out vec4 FragColor;
    void main() {
        FragColor = texture(tex, TexCoord);
    }
""".trimIndent()
