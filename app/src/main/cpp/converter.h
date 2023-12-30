//
// Created by 祖国瑞 on 2023/12/27.
//

#ifndef CAMERAUTIL_CONVERTER_H
#define CAMERAUTIL_CONVERTER_H

#include "ImageProxy.h"
#include <jni.h>
#include "constants.h"


jobject convert_YUV_420_888(JNIEnv *env, ImageProxy &image, int rotation, int facing);
jobject convert_YUV_420_888_neon(JNIEnv *env, ImageProxy image);


#endif //CAMERAUTIL_CONVERTER_H
