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


val fragShaderCode = """
    #version 300 es
    precision mediump float;
    uniform sampler2D tex;
    in vec2 TexCoord;
    out vec4 FragColor;
    
    const float offset = 1.0 / 300.0;
    
    void main() {
        vec2 offsets[9] = vec2[] (
            vec2(-offset,  offset), // top-left
            vec2( 0.0f,    offset), // top-center
            vec2( offset,  offset), // top-right
            vec2(-offset,  0.0f),   // center-left
            vec2( 0.0f,    0.0f),   // center-center
            vec2( offset,  0.0f),   // center-right
            vec2(-offset, -offset), // bottom-left
            vec2( 0.0f,   -offset), // bottom-center
            vec2( offset, -offset)  // bottom-right    
        );
        
        float sharpeKernel[9] = float[](
            -1.0, -1.0, -1.0,
            -1.0,  9.0, -1.0,
            -1.0, -1.0, -1.0
        );
    
        float blurKernel[9] = float[](
            1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0,
            2.0 / 16.0, 4.0 / 16.0, 2.0 / 16.0,
            1.0 / 16.0, 2.0 / 16.0, 1.0 / 16.0 
        );
    
        float edgeDetectionKernel[9] = float[] (
            1.0, 1.0, 1.0,
            1.0, -8.0, 1.0,
            1.0, 1.0, 1.0
        );
        
        vec3 sampleTex[9];
        for(int i = 0; i < 9; i++)
        {
            sampleTex[i] = vec3(texture(tex, TexCoord.st + offsets[i]));
        }
    
        vec3 col = vec3(0.0);
        for (int i = 0; i < 9; i++) {
            col += sampleTex[i] * edgeDetectionKernel[i];
        }
        
        FragColor = vec4(col, 1.0);
        //FragColor = texture(tex, TexCoord);
        //FragColor = vec4(1.0, 0.0, 0.0, 1.0);
    }
""".trimIndent()
