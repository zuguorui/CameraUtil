//
// Created by 祖国瑞 on 2023/12/27.
//

#ifndef CAMERAUTIL_CONVERTER_H
#define CAMERAUTIL_CONVERTER_H

#include "ImageProxy.h"
#include <jni.h>


jobject convert_YUV_420_888(JNIEnv *env, ImageProxy image);


#endif //CAMERAUTIL_CONVERTER_H
