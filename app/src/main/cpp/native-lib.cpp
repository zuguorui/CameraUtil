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
    //jobject bitmap = convert_YUV_420_888_f32_raw(env, imageProxy, rotation, facing);
    //jobject bitmap = convert_YUV_420_888_i32_raw(env, imageProxy, rotation, facing);
    jobject bitmap = convert_YUV_420_888_neon(env, imageProxy, rotation, facing);
    return bitmap;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_zu_camerautil_util_NeonTest_doNeonTest(JNIEnv *env, jobject thiz) {
    //do_neon_test();
    //assembly_test();
    instruction_test();
}