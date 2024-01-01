//
// Created by 祖国瑞 on 2023/12/27.
//

#include <jni.h>
#include "ImageProxy.h"
#include "converter.h"
#include "neon_test.h"


extern "C"
JNIEXPORT jobject JNICALL
Java_com_zu_camerautil_util_ImageConverter_nYUV_1420_1888_1to_1bitmap(JNIEnv *env, jobject thiz,
                                                                      jobject image, jint rotation, jint facing) {
    ImageProxy imageProxy(env, image);
    jobject bitmap = convert_YUV_420_888_i32(env, imageProxy, rotation, facing);
    return bitmap;
}