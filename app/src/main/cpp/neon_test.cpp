//
// Created by zu on 2023/12/31.
//

#include "neon_test.h"
#include "log.h"
#include <stdlib.h>

#define TAG "neon_test"

using namespace std;

void instruction_test() {
    // 饱和式加法
//    int8x8_t a = vdup_n_s8(100);
//    int8x8_t b = vdup_n_s8(1);
//    int8x8_t c = vqadd_s8(a, b);

//    int16x8_t a = vdupq_n_s16(INT16_MIN);
//    uint8x8_t c = vqmovun_s16(a);

    int8_t a1[8] = {1, 2, 3, 4, 5, 6, 7, 8};
    int8_t a2[8] = {9, 10, 11, 12, 13, 14, 15, 16};

    int8x8_t a = vld1_s8(a1);
    int8x8_t b = vld1_s8(a2);

    int8x8_t c = vzip1_s8(a, b);
    int8x8_t d = vzip2_s8(a, b);
    int8x8x2_t e = vzip_s8(a, b);

}

void do_neon_test() {

}

void assembly_test() {
    int a = 4;
    int b = 7;
    //int c = assembly_add(a, b);
    int c = my_function(a, b);
    LOGD(TAG, "%d", c);
}
