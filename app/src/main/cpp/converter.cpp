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
#include <stdlib.h>

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

inline uint8_t clamp(int32_t n) {
    n &= -(n >= 0);
    return n | ((255 - n) >> 31);
}

inline void yuv2rgb_i32(int32_t y, int32_t u, int32_t v, int32_t &r, int32_t &g, int32_t &b) {
    y -= 16;
    u -= 128;
    v -= 128;

    r = clamp((int32_t)(1.164 * y + 1.793 * v));
    g = clamp((int32_t)(1.164 * y - 0.213 * u - 0.533 * v));
    b = clamp((int32_t)(1.164 * y + 2.112 * u));
}

inline glm::mat3x3 getRotationMat(int bitmapWidth, int bitmapHeight, int rotation) {
    glm::mat3x3 mat;
    if (rotation == ROTATION_0) {
        /*
         * 手机正向
         * bitmap方向相对相机顺时针旋转90度。
         * 转换矩阵是
         *  0 1  0
         * -1 0 bw-1
         *  0 0  1
         * */
        mat = glm::mat3x3(
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
        mat = glm::mat3x3(
                0, 1, 0,
                -1, 0, 0,
                bitmapHeight - 1, 0, 1
                );
    } else if (rotation == ROTATION_90) {
        /*
         * 手机顺时针旋转90度
         * bitmap方向与相机一致。转换矩阵是单位矩阵
         * */
        mat = glm::mat3x3(1);
    } else {
        /*
         * 手机顺时针旋转270度
         * bitmap方向相比相机顺时针旋转180度。
         * 转换矩阵是：
         * -1  0 bh-1
         *  0 -1 bw-1
         *  0  0  1
         * */
        mat = glm::mat3x3(
                -1, 0, 0,
                0, -1, 0,
                bitmapHeight - 1, bitmapWidth - 1, 1
                );
    }
    return mat;
}

inline glm::mat3x3 getFacingMat(int imageWidth, int imageHeight, int facing) {
    glm::mat3x3 mat(1);
    if (facing == FACING_FRONT) {
        /*
         * 如果是前置，则camera输出图像左右颠倒。转换矩阵为
         * 1  0  0
         * 0 -1 bw
         * 0  0  1
         * */
        mat = glm::mat3x3(
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
    glm::mat3x3 posMat;
    glm::vec3 posInCamera, posInBitmap;
    int bitmapHeight, bitmapWidth;
    if (rotation == ROTATION_0 || rotation == ROTATION_180) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
    } else {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();
    }

    glm::mat3x3 rotateMat = getRotationMat(bitmapWidth, bitmapHeight, rotation);
    glm::mat3x3 facingMat = getFacingMat(image.getWidth(), image.getHeight(), facing);

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

    int32_t y, u, v, r, g, b;

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
    glm::mat3x3 posMat;
    glm::vec3 posInCamera, posInBitmap;
    int bitmapHeight, bitmapWidth;
    if (rotation == ROTATION_0 || rotation == ROTATION_180) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
    } else {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();
    }

    glm::mat3x3 rotateMat = getRotationMat(bitmapWidth, bitmapHeight, rotation);
    glm::mat3x3 facingMat = getFacingMat(image.getWidth(), image.getHeight(), facing);


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
        LOGD(TAG, "convert f32, %d images avg cost %d ms, image size = [%d, %d]", debugIndex, (int)avg, bitmapWidth, bitmapHeight);
        debugIndex = 0;
        timeMS = 0;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

static inline int16x8x2_t neon_load_y(uint8_t *buffer) {
    uint8x8x2_t u8_2 = vld2_u8(buffer);
    int16x8x2_t s16_2;
    // Y[even]
    s16_2.val[0] = vreinterpretq_s16_u16(vmovl_u8(u8_2.val[0]));
    // Y[odd]
    s16_2.val[1] = vreinterpretq_s16_u16(vmovl_u8(u8_2.val[1]));
    return s16_2;
}

static inline int16x8_t neon_load_uv(uint8_t *buffer) {
    uint8x8x2_t u8_2 = vld2_u8(buffer);
    uint8x8_t u8 = u8_2.val[0];
    int16x8_t s16 = vreinterpretq_s16_u16(vmovl_u8(u8));
    return s16;
}

jobject convert_YUV_420_888_neon(JNIEnv *env, ImageProxy &image, int rotation, int facing) {
    static int16x8_t _128 = vdupq_n_s16(128);
    glm::mat3x3 posMat;
    glm::vec3 posInCamera, posInBitmap;
    int bitmapHeight, bitmapWidth;
    if (rotation == ROTATION_0 || rotation == ROTATION_180) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
    } else {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();
    }

    glm::mat3x3 rotateMat = getRotationMat(bitmapWidth, bitmapHeight, rotation);
    glm::mat3x3 facingMat = getFacingMat(image.getWidth(), image.getHeight(), facing);

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

    assert(yPixelStride == 1);
    assert(uPixelStride == 2);
    assert(vPixelStride == 2);

    assert(image.getWidth() % 16 == 0);
    assert(image.getHeight() % 2 == 0);

    uint8_t rBuffer[8], gBuffer[8], bBuffer[8];

    chrono::time_point startTime = chrono::system_clock::now();
    int row = 0;
    while (row < image.getHeight()) {
        int col = 0;
        while (col < image.getWidth()) {
            int16x8_t u = neon_load_uv(uBuffer + row / 2 * uRowStride + col);
            // u - 128
            u = vsubq_s16(u, _128);
            int16x8_t v = neon_load_uv(vBuffer + row / 2 * vRowStride + col);
            // v - 128
            v = vsubq_s16(v, _128);

            // Won't overflow
            // 44 * (u - 128)
            int16x8_t u1 = vmulq_n_s16(u, 44);
            // 227 * (u - 128)
            int16x8_t u2 = vmulq_n_s16(u, 227);
            // 179 * (v - 128)
            int16x8_t v1 = vmulq_n_s16(v, 179);
            // 91 * (v - 128)
            int16x8_t v2 = vmulq_n_s16(v, 91);
            // 44 * (u - 128) + 91 * (v - 128)
            int16x8_t c1 = vaddq_s16(u1, v2);

            for (int lineOddEven = 0; lineOddEven < 2; lineOddEven++) {
                int16x8x2_t y_2 = neon_load_y(yBuffer + (row + lineOddEven) * yRowStride + col);
                for (int colOddEven = 0; colOddEven < 2; colOddEven++) {
                    int16x8_t y = y_2.val[colOddEven];
                    // y * 128
                    y = vmulq_n_s16(y, 128);

                    int16x8_t r1 = vqaddq_s16(y, v1);
                    int16x8_t g1 = vqsubq_s16(y, c1);
                    int16x8_t b1 = vqaddq_s16(y, u2);

                    r1 = vshrq_n_s16(r1, 7);
                    g1 = vshrq_n_s16(g1, 7);
                    b1 = vshrq_n_s16(b1, 7);

                    uint8x8_t r2 = vqmovun_s16(r1);
                    uint8x8_t g2 = vqmovun_s16(g1);
                    uint8x8_t b2 = vqmovun_s16(b1);

                    vst1_u8(rBuffer, r2);
                    vst1_u8(gBuffer, g2);
                    vst1_u8(bBuffer, b2);

                    for (int i = 0; i < 8; i++) {
                        uint8_t r = rBuffer[i];
                        uint8_t g = gBuffer[i];
                        uint8_t b = bBuffer[i];

                        uint32_t colorInt = (0x00FF << 24) | ((b & 0x00FF) << 16) | ((g & 0x00FF) << 8) | (r & 0x00FF);

                        posInCamera.x = row + lineOddEven;
                        posInCamera.y = col + 2 * i + colOddEven;
                        posInCamera.z = 1;

                        posInBitmap = posMat * posInCamera;
                        bitmapBuffer[(int)(posInBitmap.x * bitmapWidth) + (int)posInBitmap.y] = colorInt;
                    }
                }
            }
            col += 16;
        }
        row += 2;
    }
    chrono::time_point endTime = chrono::system_clock::now();
    chrono::duration oneImageTime = endTime - startTime;
    long ms = chrono::duration_cast<chrono::milliseconds>(oneImageTime).count();
    debugIndex++;
    timeMS += ms;
    if (debugIndex >= DEBUG_LOOP) {
        long avg = timeMS / debugIndex;
        LOGD(TAG, "convert neon2, %d images avg cost %d ms, image size = [%d, %d]", debugIndex, (int)avg, bitmapWidth, bitmapHeight);
        debugIndex = 0;
        timeMS = 0;
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}





