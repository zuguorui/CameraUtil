//
// Created by 祖国瑞 on 2023/12/27.
//

#ifndef CAMERAUTIL_CONVERTER_H
#define CAMERAUTIL_CONVERTER_H

#include "ImageProxy.h"
#include <jni.h>
#include "constants.h"
#include <arm_neon.h>

//extern "C" void neonYUV420ToRGBAFullSwing(const uint8_t *yInput, const uint8_t *uInput, const uint8_t *vInput, uint8_t *rgbaOutput, int width, int height, int rgbaStride, int lumaStride, int chromaStride);

jobject convert_YUV_420_888_f32(JNIEnv *env, ImageProxy &image, int rotation, int facing);
jobject convert_YUV_420_888_i32(JNIEnv *env, ImageProxy &image, int rotation, int facing);
jobject convert_YUV_420_888_neon(JNIEnv *env, ImageProxy &image, int rotation, int facing);
//jobject convert_YUV_420_888_assembly(JNIEnv *env, ImageProxy &image, int rotation, int facing);


#endif //CAMERAUTIL_CONVERTER_H
