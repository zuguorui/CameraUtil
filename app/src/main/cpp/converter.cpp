//
// Created by 祖国瑞 on 2023/12/27.
//

#include "converter.h"
#include <android/bitmap.h>
#include "log.h"
#include "glm/mat3x3.hpp"
#include "glm/vec3.hpp"
#include "glm.hpp"

using namespace glm;
using namespace std;

inline void yuv2rgb(float y, float u, float v, float &r, float &g, float &b) {
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
jobject convert_YUV_420_888(JNIEnv *env, ImageProxy &image, int rotation, int facing) {
    // 计算相机坐标到Bitmap坐标的转换矩阵。相机输出图像方向是相对手机正向(rotation = 0)逆时针旋转90度。
    mat3x3 posMatrix;
    vec3 posInCamera, posInBitmap;
    int bitmapHeight, bitmapWidth;
    if (rotation == ROTATION_0) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
        /*
         * 手机正向
         * bitmap方向相对相机顺时针旋转90度。
         * 转换矩阵是
         *  0 1  0
         * -1 0 bw
         *  0 0  1
         * */
        posMatrix = mat3x3(
                0, -1, 0,
                1, 0, 0,
                0, bitmapWidth, 1
                );
    } else if (rotation == ROTATION_180) {
        bitmapWidth = image.getHeight();
        bitmapHeight = image.getWidth();
        /*
         * 手机上下颠倒，即顺时针旋转180度
         * bitmap相对相机顺时针旋转270度
         * 转换矩阵是
         * 0 -1 bh
         * 1  0  0
         * 0  0  1
         * */
        posMatrix = mat3x3(
                0, 1, 0,
                -1, 0, 0,
                bitmapHeight, 0, 1
        );
    } else if (rotation == ROTATION_90) {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();
        /*
         * 手机顺时针旋转90度
         * bitmap方向与相机一致。转换矩阵是单位矩阵
         * */
        posMatrix = mat3x3(1);
    } else {
        bitmapWidth = image.getWidth();
        bitmapHeight = image.getHeight();

        /*
         * 手机顺时针旋转270度
         * bitmap方向相比相机顺时针旋转180度。
         * 转换矩阵是：
         * -1  0 bh
         *  0 -1 bw
         *  0  0  1
         * */
        posMatrix = mat3x3(
                -1, 0, 0,
                0, -1, 0,
                bitmapHeight, bitmapWidth, 1
                );
    }

    mat3x3 facingMatrix(1);
    if (facing == FACING_FRONT) {
        /*
         * 如果是前置，则camera输出图像左右颠倒。转换矩阵为
         * 1  0  0
         * 0 -1 bw
         * 0  0  1
         * */
        facingMatrix = mat3x3(
                1, 0, 0,
                0, -1, 0,
                0, image.getWidth(), 1
                );
    }

    posMatrix = posMatrix * facingMatrix;

    jclass configCls = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(configCls, "ARGB_8888", "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Obj = env->GetStaticObjectField(configCls, argb8888FieldID);

    jclass cls = env->FindClass("android/graphics/Bitmap");
    jmethodID createMethod = env->GetStaticMethodID(cls, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject bitmap = env->CallStaticObjectMethod(cls, createMethod, bitmapWidth, bitmapHeight, argb8888Obj);

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

    for (int row = 0; row < image.getHeight(); row++) {
        for (int col = 0; col < image.getWidth(); col++) {
            y = ((int)yBuffer[row * yRowStride + col * yPixelStride] & 0x00FF) * 1.0f / 0x00FF;
            u = ((int)uBuffer[row / 2 * uRowStride + col / 2 * uPixelStride] & 0x00FF) * 1.0f / 0x00FF;
            v = ((int)vBuffer[row / 2 * vRowStride + col / 2 * vPixelStride] & 0x00FF) * 1.0f / 0x00FF;

            yuv2rgb(y, u, v, r, g, b);

            colorInt = 0xFF << 24 | ((int)(b * 0x00FF) << 16) | ((int)(g * 0x00FF) << 8) | ((int)(r * 0x00FF) & 0x00FF);

            posInCamera.x = row;
            posInCamera.y = col;
            posInCamera.z = 1;
            posInBitmap = posMatrix * posInCamera;
            bitmapBuffer[(int)(posInBitmap.x * bitmapWidth) + (int)posInBitmap.y] = colorInt;
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}
