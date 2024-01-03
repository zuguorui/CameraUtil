//
// Created by 祖国瑞 on 2023/12/27.
//

#include "converter.h"
#include <android/bitmap.h>
#include "log.h"
#include "glm/mat3x3.hpp"
#include "glm/vec3.hpp"
#include <chrono>
#include <algorithm>

using namespace glm;
using namespace std;

#define TAG "convert.cpp"

jclass bitmapClass = nullptr;
jmethodID bitmapCreateMethod = nullptr;

jclass configClass = nullptr;
jobject argb8888Obj = nullptr;

void initJNI(JNIEnv *env) {
    bitmapClass = (jclass)env->NewGlobalRef(env->FindClass("android/graphics/Bitmap"));
    bitmapCreateMethod = env->GetStaticMethodID(bitmapClass, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    configClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(configClass, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    argb8888Obj = env->NewGlobalRef(env->GetStaticObjectField(configClass, argb8888FieldID));
}


inline void yuv2rgb_f32(float y, float u, float v, float &r, float &g, float &b) {
    y -= 0.0625f;
    u -= 0.5f;
    v -= 0.5f;

    r = 1.164f * y + 1.793f * v;
    g = 1.164f * y - 0.213f * u - 0.533f * v;
    b = 1.164f * y + 2.112f * u;

    if (r > 1) {
        r = 1;
    } else if (r < 0) {
        r = 0;
    }

    if (g > 1) {
        g = 1;
    } else if (g < 0) {
        g = 0;
    }

    if (b > 1) {
        b = 1;
    } else if (b < 0) {
        b = 0;
    }
}

inline std::uint8_t clamp(std::int32_t n) {
    n &= -(n >= 0);
    return n | ((255 - n) >> 31);
}

inline void yuv2rgb_i32(std::int32_t y, std::int32_t u, std::int32_t v, std::int32_t &r, std::int32_t &g, std::int32_t &b) {
    y -= 16;
    u -= 128;
    v -= 128;

    r = clamp((std::int32_t)(1.164 * y + 1.596 * v));
    g = clamp((std::int32_t)(1.164 * y - 0.392 * u - 0.813 * v));
    b = clamp((std::int32_t)(1.164 * y + 2.017 * u));
}

inline glm::mat3x3 getRotationMat(int bitmapWidth, int bitmapHeight, int rotation) {
    mat3x3 mat;
    if (rotation == ROTATION_0) {
        /*
         * 手机正向
         * bitmap方向相对相机顺时针旋转90度。
         * 转换矩阵是
         *  0 1  0
         * -1 0 bw-1
         *  0 0  1
         * */
        mat = mat3x3(
        0, -1, 0,
                1, 0, 0,
                0, bitmapWidth - 1, 1
        );
    } else if (rotation == ROTATION_180) {
        /*
         * 手机上下颠倒，即顺时针旋转180度
         * bitmap相对相机顺时针旋转270度
         * 转换矩阵是
         * 0 -1 bh-1
         * 1  0  0
         * 0  0  1
         * */
        mat = mat3x3(
                0, 1, 0,
                -1, 0, 0,
                bitmapHeight - 1, 0, 1
                );
    } else if (rotation == ROTATION_90) {
        /*
         * 手机顺时针旋转90度
         * bitmap方向与相机一致。转换矩阵是单位矩阵
         * */
        mat = mat3x3(1);
    } else {
        /*
         * 手机顺时针旋转270度
         * bitmap方向相比相机顺时针旋转180度。
         * 转换矩阵是：
         * -1  0 bh-1
         *  0 -1 bw-1
         *  0  0  1
         * */
        mat = mat3x3(
                -1, 0, 0,
                0, -1, 0,
                bitmapHeight - 1, bitmapWidth - 1, 1
                );
    }
    return mat;
}

inline glm::mat3x3 getFacingMat(int imageWidth, int imageHeight, int facing) {
    mat3x3 mat(1);
    if (facing == FACING_FRONT) {
        /*
         * 如果是前置，则camera输出图像左右颠倒。转换矩阵为
         * 1  0  0
         * 0 -1 bw
         * 0  0  1
         * */
        mat = mat3x3(
                1, 0, 0,
                0, -1, 0,
                0, imageWidth - 1, 1);
    }
    return mat;
}

int debugIndex = 0;
long timeMS = 0;
const int DEBUG_LOOP = 20;

/**
 * glm的矩阵是列序的，而不是数学中习惯的行序。
 * 例如，一个数学中的矩阵如下：
 * 1 2 3
 * 4 5 6
 * 7 8 9
 * 在glm中初始化时就是：
 * mat3x3(
 * 1, 4, 7,
 * 2, 5, 8,
 * 3, 6, 9
 * )。
 * 或者更直观使用向量初始化矩阵：
 * mat3x3(
 * vec3(1, 4, 7),
 * vec3(2, 5, 8),
 * vec3(3, 6, 9)
 * )
 * */
jobject convert_YUV_420_888_i32(JNIEnv *env, ImageProxy &image, int rotation, int facing) {
    // 计算相机坐标到Bitmap坐标的转换矩阵。相机输出图像方向是相对手机正向(rotation = 0)逆时针旋转90度。
    mat3x3 posMat;
    vec3 posInCamera, posInBitmap;
    int bitmapHeight, bitmapWidth;
    if (rotation == ROTATION_0 || rotation == ROTATION_180) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
    } else {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();
    }

    mat3x3 rotateMat = getRotationMat(bitmapWidth, bitmapHeight, rotation);
    mat3x3 facingMat = getFacingMat(image.getWidth(), image.getHeight(), facing);

    posMat = rotateMat * facingMat;

    if (bitmapClass == nullptr) {
        LOGE(TAG, "JNI object not init, init");
        initJNI(env);
    }

    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, bitmapCreateMethod, bitmapWidth, bitmapHeight, argb8888Obj);

    int32_t *bitmapBuffer = nullptr;
    AndroidBitmap_lockPixels(env, bitmap, (void **)&bitmapBuffer);

    uint8_t *yBuffer, *uBuffer, *vBuffer;
    int yBufferLen, uBufferLen, vBufferLen;
    int yRowStride, uRowStride, vRowStride;
    int yPixelStride, uPixelStride, vPixelStride;

    image.getPlane(0, &yBuffer, yBufferLen, yRowStride, yPixelStride);
    image.getPlane(1, &uBuffer, uBufferLen, uRowStride, uPixelStride);
    image.getPlane(2, &vBuffer, vBufferLen, vRowStride, vPixelStride);

    std::int32_t y, u, v, r, g, b;

    int32_t colorInt;
    chrono::time_point startTime = chrono::system_clock::now();
    for (int row = 0; row < image.getHeight(); row++) {
        for (int col = 0; col < image.getWidth(); col++) {
            y = ((int)yBuffer[row * yRowStride + col * yPixelStride] & 0x00FF);
            u = ((int)uBuffer[row / 2 * uRowStride + col / 2 * uPixelStride] & 0x00FF);
            v = ((int)vBuffer[row / 2 * vRowStride + col / 2 * vPixelStride] & 0x00FF);
            yuv2rgb_i32(y, u, v, r, g, b);
            colorInt = (0x00FF << 24) | ((b & 0x00FF) << 16) | ((g & 0x00FF) << 8) | (r & 0x00FF);

            posInCamera.x = row;
            posInCamera.y = col;
            posInCamera.z = 1;
            posInBitmap = posMat * posInCamera;
            // LOGD(TAG, "pos x = %d, y = %d, array pos = %d, size = %d", (int)posInBitmap.x, (int)posInBitmap.y, (int)(posInBitmap.x * bitmapWidth) + (int)posInBitmap.y, bitmapWidth * bitmapHeight);
            bitmapBuffer[(int)(posInBitmap.x * bitmapWidth) + (int)posInBitmap.y] = colorInt;
        }
    }
    chrono::time_point endTime = chrono::system_clock::now();
    chrono::duration oneImageTime = endTime - startTime;
    long ms = chrono::duration_cast<chrono::milliseconds>(oneImageTime).count();
    debugIndex++;
    timeMS += ms;
    if (debugIndex >= DEBUG_LOOP) {
        long avg = timeMS / debugIndex;
        LOGD(TAG, "convert i32, %d images avg cost %d ms", debugIndex, (int)avg);
        debugIndex = 0;
        timeMS = 0;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

jobject convert_YUV_420_888_f32(JNIEnv *env, ImageProxy &image, int rotation, int facing) {
    // 计算相机坐标到Bitmap坐标的转换矩阵。相机输出图像方向是相对手机正向(rotation = 0)逆时针旋转90度。
    mat3x3 posMat;
    vec3 posInCamera, posInBitmap;
    int bitmapHeight, bitmapWidth;
    if (rotation == ROTATION_0 || rotation == ROTATION_180) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
    } else {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();
    }

    mat3x3 rotateMat = getRotationMat(bitmapWidth, bitmapHeight, rotation);
    mat3x3 facingMat = getFacingMat(image.getWidth(), image.getHeight(), facing);


    posMat = rotateMat * facingMat;

    if (bitmapClass == nullptr) {
        LOGE(TAG, "JNI object not init, init");
        initJNI(env);
    }

    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, bitmapCreateMethod, bitmapWidth, bitmapHeight, argb8888Obj);

    int32_t *bitmapBuffer = nullptr;
    AndroidBitmap_lockPixels(env, bitmap, (void **)&bitmapBuffer);

    uint8_t *yBuffer, *uBuffer, *vBuffer;
    int yBufferLen, uBufferLen, vBufferLen;
    int yRowStride, uRowStride, vRowStride;
    int yPixelStride, uPixelStride, vPixelStride;

    image.getPlane(0, &yBuffer, yBufferLen, yRowStride, yPixelStride);
    image.getPlane(1, &uBuffer, uBufferLen, uRowStride, uPixelStride);
    image.getPlane(2, &vBuffer, vBufferLen, vRowStride, vPixelStride);

    float y, u, v, r, g, b;

    int32_t colorInt;
    chrono::time_point startTime = chrono::system_clock::now();
    for (int row = 0; row < image.getHeight(); row++) {
        for (int col = 0; col < image.getWidth(); col++) {
            y = ((int)yBuffer[row * yRowStride + col * yPixelStride] & 0x00FF) * 1.0f / 0x00FF;
            u = ((int)uBuffer[row / 2 * uRowStride + col / 2 * uPixelStride] & 0x00FF) * 1.0f / 0x00FF;
            v = ((int)vBuffer[row / 2 * vRowStride + col / 2 * vPixelStride] & 0x00FF) * 1.0f / 0x00FF;
            yuv2rgb_f32(y, u, v, r, g, b);
            colorInt = 0xFF << 24 | ((int)(b * 0x00FF) << 16) | ((int)(g * 0x00FF) << 8) | ((int)(r * 0x00FF) & 0x00FF);

            posInCamera.x = row;
            posInCamera.y = col;
            posInCamera.z = 1;
            posInBitmap = posMat * posInCamera;
            // LOGD(TAG, "pos x = %d, y = %d, array pos = %d, size = %d", (int)posInBitmap.x, (int)posInBitmap.y, (int)(posInBitmap.x * bitmapWidth) + (int)posInBitmap.y, bitmapWidth * bitmapHeight);
            bitmapBuffer[(int)(posInBitmap.x * bitmapWidth) + (int)posInBitmap.y] = colorInt;
        }
    }
    chrono::time_point endTime = chrono::system_clock::now();
    chrono::duration oneImageTime = endTime - startTime;
    long ms = chrono::duration_cast<chrono::milliseconds>(oneImageTime).count();
    debugIndex++;
    timeMS += ms;
    if (debugIndex >= DEBUG_LOOP) {
        long avg = timeMS / debugIndex;
        LOGD(TAG, "convert f32, %d images avg cost %d ms", debugIndex, (int)avg);
        debugIndex = 0;
        timeMS = 0;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

const int NEON_BUFFER_SIZE = 8;

std::int16_t yShortBuffer1[NEON_BUFFER_SIZE], yShortBuffer2[NEON_BUFFER_SIZE], uShortBuffer[NEON_BUFFER_SIZE], vShortBuffer[NEON_BUFFER_SIZE];


/**
 * 使用neon进行计算
 * YUV到BT.601 RGB的int计算公式如下：
 * R = [ ( 128*Y +               179*(V-128) ) >> 7 ]
 * G = [ ( 128*Y -  44*(U-128) -  91*(V-128) ) >> 7 ]
 * B = [ ( 128*Y + 227*(U-128)               ) >> 7 ]
 * 有空再更新BT709的int计算公式
 * */
jobject convert_YUV_420_888_neon(JNIEnv *env, ImageProxy &image, int rotation, int facing) {

    mat3x3 posMat;
    vec3 posInCamera, posInBitmap;
    int bitmapHeight, bitmapWidth;
    if (rotation == ROTATION_0 || rotation == ROTATION_180) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
    } else {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();
    }

    mat3x3 rotateMat = getRotationMat(bitmapWidth, bitmapHeight, rotation);
    mat3x3 facingMat = getFacingMat(image.getWidth(), image.getHeight(), facing);

    posMat = rotateMat * facingMat;

    if (bitmapClass == nullptr) {
        LOGE(TAG, "JNI object not init, init");
        initJNI(env);
    }

    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, bitmapCreateMethod, bitmapWidth, bitmapHeight, argb8888Obj);

    int32_t *bitmapBuffer = nullptr;
    AndroidBitmap_lockPixels(env, bitmap, (void **)&bitmapBuffer);

    uint8_t *yBuffer, *uBuffer, *vBuffer;
    int yBufferLen, uBufferLen, vBufferLen;
    int yRowStride, uRowStride, vRowStride;
    int yPixelStride, uPixelStride, vPixelStride;

    image.getPlane(0, &yBuffer, yBufferLen, yRowStride, yPixelStride);
    image.getPlane(1, &uBuffer, uBufferLen, uRowStride, uPixelStride);
    image.getPlane(2, &vBuffer, vBufferLen, vRowStride, vPixelStride);

//    assert(yPixelStride == 1);
//    assert(uPixelStride == 2);
//    assert(vPixelStride == 2);

//    assert(image.getWidth() % 16 == 0);
//    assert(image.getHeight() % 2 == 0);

    /*
     * YUV420是4个Y共用一个UV，如下：
     * Y1  Y2 | Y3  Y4 | // Y row1
     *   U1   |   U2   | // U row1
     *   V1   |   V2   | // V row1
     * Y5  Y6 | Y7  Y8 | // Y row2
     * 在NEON中，使用一个uint8x16x2_t来解交织存储Y row1，达到
     * Y1 Y3 ...
     * Y2 Y4 ...
     * 这样，可以将Y分成两个向量，这两个向量分别都与UV做相同计算。
     * 而在内存中，UV分量是有空白字节的，这就是pixelStride的意义。例如U分量：
     * U1 0 U2 0 U3 0...
     * 0代表空白字节。
     * 同样使用uint8x16x2_t来解交织存储
     * U1 U2 U3 ... // vec1
     * 0  0  0  ... // vec2
     * 这样就可以只使用vec1了。
     * 该算法一次使用128bit寄存器，这里假设了camera输出图像的宽是16的倍数，
     * 高度是2的倍数以达到最简单解法，也是最快性能。
     * */
    int row = 0;
    std::int16_t* yShortBuffers[2] = {yShortBuffer1, yShortBuffer2};
    while (row < image.getHeight()) {
        int col = 0;
        while (col < image.getWidth()) {
            int i = 0;
            int startCol = col;
            while (i * 2 < NEON_BUFFER_SIZE && col < image.getWidth()) {
                yShortBuffers[col % 2][i / 2] = (std::int16_t)yBuffer[row * yRowStride + col * yPixelStride] & 0x00FF;
                uShortBuffer[i / 2] = (std::int16_t)uBuffer[row / 2 * uRowStride + col / 2 * uPixelStride] & 0x00FF;
                vShortBuffer[i / 2] = (std::int16_t)vBuffer[row / 2 * vRowStride + col / 2 * vPixelStride] & 0x00FF;
                col++;
                i++;
            }
            int16x8_t uNeon = vld1q_s16(uShortBuffer);
            int16x8_t vNeon = vld1q_s16(vShortBuffer);
            int16x8_t _128 = vdupq_n_s16(128);
            uNeon = vsubq_s16(uNeon, _128);
            vNeon = vsubq_s16(vNeon, _128);
            for (int j = 0; j < 2; j++) {
                int16x8_t yNeon = vld1q_s16(yShortBuffers[i]);
                yNeon = vmulq_n_s16(yNeon, 128);
                int16x8_t rNeon = vshrq_n_s16(vaddq_s16(yNeon, vmulq_n_s16(vNeon, 179)), 7);
                int16x8_t gNeon = vshrq_n_s16(vsubq_s16(vsubq_s16(yNeon, vmulq_n_s16(uNeon, 44)),
                                                    vmulq_n_s16(vNeon, 91)), 7);
                int16x8_t bNeon = vshrq_n_s16(vaddq_s16(yNeon, vmulq_n_s16(uNeon, 227)), 7);
            }


        }
        row += 2;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

