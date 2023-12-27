//
// Created by 祖国瑞 on 2023/12/27.
//

#include "ImageProxy.h"

ImageProxy::ImageProxy(JNIEnv *env, jobject image) {
    this->env = env;
    this->image = image;
    init();
}

ImageProxy::~ImageProxy() {

    if (planes != nullptr) {
        for (int i = 0; i < planeCount; i++) {
            if (planes[i] != nullptr) {
                delete planes[i];
                planes[i] = nullptr;
            }
        }
        free(planes);
        planes = nullptr;
    }
}

void ImageProxy::init() {
    jclass cls = env->FindClass("android/media/Image");

    jmethodID getFormatMethod = env->GetMethodID(cls, "getFormat", "()I");
    format = env->CallIntMethod(image, getFormatMethod);

    jmethodID getWidthMethod = env->GetMethodID(cls, "getWidth", "()I");
    width = env->CallIntMethod(image, getWidthMethod);

    jmethodID getHeightMethod = env->GetMethodID(cls, "getHeight", "()I");
    height = env->CallIntMethod(image, getHeightMethod);

    jmethodID getPlanesMethod = env->GetMethodID(cls, "getPlanes", "()[Landroid/media/Image$Plane;");
    jobjectArray planeObjArray = (jobjectArray)env->CallObjectMethod(image, getPlanesMethod);
    planeCount = env->GetArrayLength(planeObjArray);
    planes = (PlaneProxy **) malloc(planeCount * sizeof(PlaneProxy *));
    for (int i = 0; i < planeCount; i++) {
        jobject planeObj = env->GetObjectArrayElement(planeObjArray, i);
        PlaneProxy *proxy = new PlaneProxy(env, planeObj);
        planes[i] = proxy;
    }
}

int ImageProxy::getWidth() {
    return width;
}

int ImageProxy::getHeight() {
    return height;
}

int ImageProxy::getPlaneCount() {
    return planeCount;
}

void ImageProxy::getPlane(int index, uint8_t **buffer, int &bufferSize, int &rowStride,
                          int &pixelStride) {
    if (index >= planeCount) {
        return;
    }
    PlaneProxy *planeProxy = planes[index];
    *buffer = (uint8_t *)env->GetDirectBufferAddress(planeProxy->buffer);
    bufferSize = env->GetDirectBufferCapacity(planeProxy->buffer);
    rowStride = planeProxy->rowStride;
    pixelStride = planeProxy->pixelStride;
}

