//
// Created by 祖国瑞 on 2023/12/27.
//

#ifndef CAMERAUTIL_CONVERTER_H
#define CAMERAUTIL_CONVERTER_H

#include "ImageProxy.h"
#include <jni.h>
#include "constants.h"
#include <arm_neon.h>

jobject convert_YUV_420_888_f32(JNIEnv *env, ImageProxy &image, int rotation, int facing);
jobject convert_YUV_420_888_i32(JNIEnv *env, ImageProxy &image, int rotation, int facing);
jobject convert_YUV_420_888_neon(JNIEnv *env, ImageProxy &image, int rotation, int facing);


#endif //CAMERAUTIL_CONVERTER_H
