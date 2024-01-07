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
        LOGD(TAG, "convert f32, %d images avg cost %d ms", debugIndex, (int)avg);
        debugIndex = 0;
        timeMS = 0;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

const int NEON_BUFFER_SIZE = 8;

// int16_t yShortBuffer1[NEON_BUFFER_SIZE], yShortBuffer2[NEON_BUFFER_SIZE], uShortBuffer[NEON_BUFFER_SIZE], vShortBuffer[NEON_BUFFER_SIZE];
// uint8x8x2_t yNeonBuffer, uNeonBuffer, vNeonBuffer;

/**
 * 使用neon进行计算
 * YUV到BT.601 RGB的int计算公式如下：
 * R = [ ( 128*Y +               179*(V-128) ) >> 7 ]
 * G = [ ( 128*Y -  44*(U-128) -  91*(V-128) ) >> 7 ]
 * B = [ ( 128*Y + 227*(U-128)               ) >> 7 ]
 * 有空再更新BT709的int计算公式
 * */

static int32x4_t _128 = vdupq_n_s32(128);
static int32x4_t _255 = vdupq_n_s32(255);
static int32x4_t _16 = vdupq_n_s32(16);
int32_t rBuffer[8], gBuffer[8], bBuffer[8];

inline uint8x8_t clamp_s16x8(int16x8_t vec) {

    // n &= -(n >= 0)
    uint16x8_t a = vandq_u16(vcgezq_s16(vec), vdupq_n_u16(1));
    int16x8_t b = vreinterpretq_s16_u16(a);
    int16x8_t c = vmulq_n_s16(b, -1);
    vec = vandq_s16(vec, c);

    // n | ((255 - n) >> 15)
    vec = vorrq_s16(vec, vshrq_n_s16(vsubq_s16(_255, vec), 15));

    // get the lower byte of int16
    int8x8_t d = vmovn_s16(vec);
    // e = (uint8_t)d
    uint8x8_t e = vreinterpret_u8_s8(d);
    return e;
}

inline int32x4_t clamp_s32x4(int32x4_t vec) {
    uint32x4_t a = vandq_s32(vcgezq_s32(vec), vdupq_n_u32(1));
    int32x4_t b = vreinterpretq_s32_u32(a);
    int32x4_t c = vmulq_n_s32(b, -1);

    vec = vandq_s32(vec, c);

    int32x4_t d = vsubq_s32(_255, vec);
    int32x4_t e = vshrq_n_s32(d, 31);
    vec = vorrq_s32(vec, e);
    return vec;
}

jobject convert_YUV_420_888_neon(JNIEnv *env, ImageProxy &image, int rotation, int facing) {

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
    while (row < image.getHeight()) {
        int col = 0;
        while (col < image.getWidth()) {

            // 每次读16个像素
            uint8x8x2_t yNeonU8_2 = vld2_u8(yBuffer + row * yRowStride + col);
            uint8x8x2_t uNeonU8_2 = vld2_u8(uBuffer + row / 2 * uRowStride + col);
            uint8x8x2_t vNeonU8_2 = vld2_u8(vBuffer + row / 2 * vRowStride + col);

            int16x8x2_t yNeonS16_2;
            // Y[even]
            yNeonS16_2.val[0] = vreinterpretq_s16_u16(vmovl_u8(yNeonU8_2.val[0]));
            // Y[odd]
            yNeonS16_2.val[1] = vreinterpretq_s16_u16(vmovl_u8(yNeonU8_2.val[1]));

            int32x4x4_t yNeonS32_4;
            // Y[low, even]
            yNeonS32_4.val[0] = vmovl_s16(vget_low_s16(yNeonS16_2.val[0]));
            // Y[low, odd]
            yNeonS32_4.val[1] = vmovl_s16(vget_low_s16(yNeonS16_2.val[1]));
            // Y[high, even]
            yNeonS32_4.val[2] = vmovl_s16(vget_high_s16(yNeonS16_2.val[0]));
            // Y[high, odd]
            yNeonS32_4.val[3] = vmovl_s16(vget_high_s16(yNeonS16_2.val[1]));

            // convert UV from U8 to F32, and split to low 4 elements and high 4
            uint8x8_t uNeonU8 = uNeonU8_2.val[0];
            int16x8_t uNeonS16 = vreinterpretq_s16_u16(vmovl_u8(uNeonU8));
            int32x4x2_t uNeonS32_2;
            // U[low]
            uNeonS32_2.val[0] = vmovl_s16(vget_low_s16(uNeonS16));
            // U[high]
            uNeonS32_2.val[1] = vmovl_s16(vget_high_s16(uNeonS16));


            uint8x8_t vNeonU8 = vNeonU8_2.val[0];
            int16x8_t vNeonS16 = vreinterpretq_s16_u16(vmovl_u8(vNeonU8));
            int32x4x2_t vNeonS32_2;
            // V[low]
            vNeonS32_2.val[0] = vmovl_s16(vget_low_s16(vNeonS16));
            // V[high]
            vNeonS32_2.val[1] = vmovl_s16(vget_high_s16(vNeonS16));

            for (int lowHigh = 0; lowHigh < 2; lowHigh++) {
                int32x4_t uNeonS32 = uNeonS32_2.val[lowHigh];
                uNeonS32 = vsubq_s32(uNeonS32, _128);
                float32x4_t uNeonF32 = vcvtq_f32_s32(uNeonS32);

                int32x4_t vNeonS32 = vNeonS32_2.val[lowHigh];
                vNeonS32 = vsubq_s32(vNeonS32, _128);
                float32x4_t vNeonF32 = vcvtq_f32_s32(vNeonS32);

                for (int oddEven = 0; oddEven < 2; oddEven++) {
                    int32x4_t yNeonS32 = yNeonS32_4.val[2 * lowHigh + oddEven];
                    yNeonS32 = vsubq_s32(yNeonS32, _16);
                    float32x4_t yNeonF32 = vcvtq_f32_s32(yNeonS32);

                    float32x4_t rNeonF32 =
                            vaddq_f32(
                                    vmulq_n_f32(yNeonF32, 1.164f),
                                    vmulq_n_f32(vNeonF32, 1.793f)
                            );
                    float32x4_t gNeonF32 =
                            vsubq_f32(
                                    vsubq_f32(
                                            vmulq_n_f32(yNeonF32, 1.164f),
                                            vmulq_n_f32(uNeonF32, 0.213f)
                                    ),
                                    vmulq_n_f32(vNeonF32, 0.533f)
                            );

                    float32x4_t bNeonF32 =
                            vaddq_f32(
                                    vmulq_n_f32(yNeonF32, 1.164f),
                                    vmulq_n_f32(uNeonF32, 2.112f)
                            );

                    int32x4_t rNeonS32 = vcvtq_s32_f32(rNeonF32);
                    int32x4_t gNeonS32 = vcvtq_s32_f32(gNeonF32);
                    int32x4_t bNeonS32 = vcvtq_s32_f32(bNeonF32);

                    rNeonS32 = clamp_s32x4(rNeonS32);
                    gNeonS32 = clamp_s32x4(gNeonS32);
                    bNeonS32 = clamp_s32x4(bNeonS32);

                    vst1q_s32(rBuffer, rNeonS32);
                    vst1q_s32(gBuffer, gNeonS32);
                    vst1q_s32(bBuffer, bNeonS32);

                    for (int i = 0; i < 4; i++) {
                        uint32_t r = (uint32_t)rBuffer[i];
                        uint32_t g = (uint32_t)gBuffer[i];
                        uint32_t b = (uint32_t)bBuffer[i];

                        uint32_t colorInt = (0x00FF << 24) | ((b & 0x00FF) << 16) | ((g & 0x00FF) << 8) | (r & 0x00FF);

                        posInCamera.x = row;
                        posInCamera.y = col + 2 * i + oddEven + 8 * lowHigh;
                        posInCamera.z = 1;

                        posInBitmap = posMat * posInCamera;

                        bitmapBuffer[(int)(posInBitmap.x * bitmapWidth) + (int)posInBitmap.y] = colorInt;
                    }
                }
            }
            col += 16;
        }
        row += 1;
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}



