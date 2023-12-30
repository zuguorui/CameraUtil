//
// Created by 祖国瑞 on 2023/12/27.
//

#ifndef CAMERAUTIL_IMAGEPROXY_H
#define CAMERAUTIL_IMAGEPROXY_H

#include <jni.h>
#include <stdlib.h>
#include "log.h"

class ImageProxy {
public:
    ImageProxy(JNIEnv *env, jobject image);
    ImageProxy(ImageProxy &) = delete;
    ImageProxy(ImageProxy &&) = delete;
    ~ImageProxy();

    int getPlaneCount();
    int getWidth();
    int getHeight();

    void getPlane(int index, uint8_t **buffer, int &bufferSize, int &rowStride, int &pixelStride);

private:
    struct PlaneProxy {
        PlaneProxy(JNIEnv *env, jobject plane, int index) {
            this->index = index;
            this->plane = plane;

            jclass cls = env->FindClass("android/media/Image$Plane");

            jmethodID getRowStrideMethod = env->GetMethodID(cls, "getRowStride", "()I");
            rowStride = env->CallIntMethod(plane, getRowStrideMethod);

            jmethodID getPixelStrideMethod = env->GetMethodID(cls, "getPixelStride", "()I");
            pixelStride = env->CallIntMethod(plane, getPixelStrideMethod);

            jmethodID getBufferMethod = env->GetMethodID(cls, "getBuffer","()Ljava/nio/ByteBuffer;");
            buffer = env->CallObjectMethod(plane, getBufferMethod);
        }

        ~PlaneProxy() {
            LOGD("ImageProxy", "Delete PlaneProxy, %d", index);
        }
        int index = -1;
        jobject plane;
        int rowStride;
        int pixelStride;
        jobject buffer;
    };

    void init();

    JNIEnv *env = nullptr;
    jobject image = nullptr;
    int format = 0;
    PlaneProxy **planes = nullptr;
    int planeCount = 0;

    int width;
    int height;


};


#endif //CAMERAUTIL_IMAGEPROXY_H
