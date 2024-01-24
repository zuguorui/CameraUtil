//
// Created by zu on 2023/12/31.
//

#include "neon_test.h"
#include "log.h"
#include <stdlib.h>

#define TAG "neon_test"

using namespace std;



static inline uint8_t clamp(int32_t n) {
    n &= -(n >= 0);
    return n | ((255 - n) >> 31);
}

static inline uint8x8_t clamp_s16x8(int16x8_t vec) {
    static int16x8_t _255 = vdupq_n_s16(255);

    // n &= -(n >= 0)
    uint16x8_t a = vandq_u16(vcgezq_s16(vec), vdupq_n_u16(1));
    int16x8_t b = vreinterpretq_s16_u16(a);
    int16x8_t c = vmulq_n_s16(b, -1);
    vec = vandq_s16(vec, c);

    // n | ((255 - n) >> 15)
    vec = vorrq_s16(vec, vshrq_n_s16(vsubq_s16(_255, vec), 15));

    // get the lower byte of int16
    int8x8_t d = vmovn_s16(vec);
    // e = (uint8_t)d
    uint8x8_t e = vreinterpret_u8_s8(d);
    return e;
}

static inline int32x4_t clamp_s32x4(int32x4_t vec) {
    static int32x4_t _255 = vdupq_n_s32(255);
    uint32x4_t a = vandq_s32(vcgezq_s32(vec), vdupq_n_u32(1));
    int32x4_t b = vreinterpretq_s32_u32(a);
    int32x4_t c = vmulq_n_s32(b, -1);

    vec = vandq_s32(vec, c);

    int32x4_t d = vsubq_s32(_255, vec);
    int32x4_t e = vshrq_n_s32(d, 31);
    vec = vorrq_s32(vec, e);
    return vec;
}

static inline void yuv2rgb_i32(int32_t y, int32_t u, int32_t v, int32_t &r, int32_t &g, int32_t &b) {
    y -= 16;
    u -= 128;
    v -= 128;

    r = clamp((int32_t)(1.164 * y + 1.793 * v));
    g = clamp((int32_t)(1.164 * y - 0.213 * u - 0.533 * v));
    b = clamp((int32_t)(1.164 * y + 2.112 * u));
}

void shiftTest() {
    int16_t a = 0xFF00;
    int16_t b = a >> 8;

    int16x8_t aNeon = vdupq_n_s16(a);
    int16x8_t bNeon = vshrq_n_s16(aNeon, 8);
}

void movTest() {
    int16x4_t a = vdup_n_s16(0x37FF);
    int32x4_t b = vmovl_s16(a);
    int16x4_t c1;
    int16x8_t c = vmovn_high_s32(c1, b);
    int16x4_t c2 = vmovn_s32(b);
}

void stepByStep() {
    static int16x8_t _128 = vdupq_n_s16(128);
    static int16x8_t _255 = vdupq_n_s16(255);

    uint8_t y = 255;
    uint8_t u = 255;
    uint8_t v = 255;

    // Neon
    uint8x8_t yNeonU8 = vdup_n_u8(y);
    uint8x8_t uNeonU8 = vdup_n_u8(u);
    uint8x8_t vNeonU8 = vdup_n_u8(v);

    // y * 128
    int16x8_t yNeonS16 = vreinterpretq_s16_u16(vmovl_u8(yNeonU8));
    yNeonS16 = vmulq_n_s16(yNeonS16, 128);

    int16_t y1 = (int16_t)y * 128;

    // u - 128
    int16x8_t uNeonS16 = vreinterpretq_s16_u16(vmovl_u8(uNeonU8));
    uNeonS16 = vsubq_s16(uNeonS16, _128);

    int16_t u1 = (int16_t)u - 128;

    // v - 128
    int16x8_t vNeonS16 = vreinterpretq_s16_u16(vmovl_u8(vNeonU8));
    vNeonS16 = vsubq_s16(vNeonS16, _128);

    int16_t v1 = (int16_t)v - 128;

    int16x8_t rn1 = vmulq_n_s16(vNeonS16, 179); // v * 179
    int16_t rc1 = v1 * 179;
    int16x8_t rn2 = vaddq_s16(yNeonS16, rn1);
    int16_t rc2 = y1 + rc1;
    int16x8_t rn = vshrq_n_s16(rn2, 7);
    int16_t rc = rc2 >> 7;

    int16x8_t gn1 = vmulq_n_s16(uNeonS16, 44);
    int16_t gc1 = u1 * 44;
    int16x8_t gn2 = vmulq_n_s16(vNeonS16, 91);
    int16_t gc2 = v1 * 91;
    int16x8_t gn3 = vsubq_s16(yNeonS16, gn1);
    int16_t gc3 = y1 - gc1;
    int16x8_t gn4 = vsubq_s16(gn3, gn2);
    int16_t gc4 = gc3 - gc2;
    int16x8_t gn = vshrq_n_s16(gn4, 7);
    int16_t gc = gc4 >> 7;

    int16x8_t bn1 = vmulq_n_s16(uNeonS16, 227);
    int16_t bc1 = u1 * 227;
    int16x8_t bn2 = vaddq_s16(yNeonS16, bn1);
    int16_t bc2 = y1 + bc1;
    int16x8_t bn = vshrq_n_s16(bn2, 7);
    int16_t bc = bc2 >> 7;

    uint8x8_t rnu = clamp_s16x8(rn);
    uint8x8_t gnu = clamp_s16x8(gn);
    uint8x8_t bun = clamp_s16x8(bn);

    uint8_t rcu = clamp(rc);
    uint8_t gcu = clamp(gc);
    uint8_t bcu = clamp(bc);

}

void do_neon_test() {
    movTest();
    stepByStep();
    shiftTest();
    static int32x4_t _128 = vdupq_n_s32(128);
    static int32x4_t _255 = vdupq_n_s32(255);
    static int32x4_t _16 = vdupq_n_s32(16);

    uint8_t y = 255;
    uint8_t u = 255;
    uint8_t v = 255;

    // Neon raw
    uint8x8_t yNeonU8 = vdup_n_u8(y);
    uint8x8_t uNeonU8 = vdup_n_u8(u);
    uint8x8_t vNeonU8 = vdup_n_u8(v);

    // y * 128
    int16x8_t yNeonS16 = vreinterpretq_s16_u16(vmovl_u8(yNeonU8));
    int32x4_t yLowNeonS32 = vmovl_s16(vget_low_s16(yNeonS16));
    int32x4_t yHighNeonS32 = vmovl_s16(vget_high_s16(yNeonS16));
    yLowNeonS32 = vsubq_s32(yLowNeonS32, _16);
    float32x4_t yNeonF32 = vcvtq_f32_s32(yLowNeonS32);


    // u - 128
    int16x8_t uNeonS16 = vreinterpretq_s16_u16(vmovl_u8(uNeonU8));
    int32x4_t uLowNeonS32 = vmovl_s16(vget_low_s16(uNeonS16));
    int32x4_t uHighNeonS32 = vmovl_s16(vget_high_s16(uNeonS16));
    uLowNeonS32 = vsubq_s32(uLowNeonS32, _128);
    float32x4_t uNeonF32 = vcvtq_f32_s32(uLowNeonS32);

    // v - 128
    int16x8_t vNeonS16 = vreinterpretq_s16_u16(vmovl_u8(vNeonU8));
    int32x4_t vLowNeonS32 = vmovl_s16(vget_low_s16(vNeonS16));
    int32x4_t vHighNeonS32 = vmovl_s16(vget_high_s16(vNeonS16));
    vLowNeonS32 = vsubq_s32(vLowNeonS32, _128);
    float32x4_t vNeonF32 = vcvtq_f32_s32(vLowNeonS32);

    float32x4_t rNeonF32 =
            vaddq_f32(
                    vmulq_n_f32(yNeonF32, 1.164f),
                    vmulq_n_f32(vNeonF32, 1.793f)
            );
    float32x4_t gNeonF32 =
            vsubq_f32(
                    vsubq_f32(
                            vmulq_n_f32(yNeonF32, 1.164f),
                            vmulq_n_f32(uNeonF32, 0.213f)
                    ),
                    vmulq_n_f32(vNeonF32, 0.533f)
            );

    float32x4_t bNeonF32 =
            vaddq_f32(
                    vmulq_n_f32(yNeonF32, 1.164f),
                    vmulq_n_f32(uNeonF32, 2.112f)
                    );

    int32x4_t rNeonS32 = vcvtq_s32_f32(rNeonF32);
    int32x4_t gNeonS32 = vcvtq_s32_f32(gNeonF32);
    int32x4_t bNeonS32 = vcvtq_s32_f32(bNeonF32);

    rNeonS32 = clamp_s32x4(rNeonS32);
    gNeonS32 = clamp_s32x4(gNeonS32);
    bNeonS32 = clamp_s32x4(bNeonS32);

    int32_t rBuffer[4], gBuffer[4], bBuffer[4];

    vst1q_s32(rBuffer, rNeonS32);
    vst1q_s32(gBuffer, gNeonS32);
    vst1q_s32(bBuffer, bNeonS32);

    uint32_t r1 = (uint32_t)rBuffer[0];
    uint32_t g1 = (uint32_t)gBuffer[0];
    uint32_t b1 = (uint32_t)bBuffer[0];


//    int16x8_t rNeonS16 = vshrq_n_s16(vaddq_s16(yNeonS16, vmulq_n_s16(vNeonS16, 179)), 7);
//    int16x8_t gNeonS16 = vshrq_n_s16(vsubq_s16(vsubq_s16(yNeonS16, vmulq_n_s16(uNeonS16, 44)), vmulq_n_s16(vNeonS16, 91)), 7);
//    int16x8_t bNeonS16 = vshrq_n_s16(vaddq_s16(yNeonS16, vmulq_n_s16(uNeonS16, 227)), 7);
//
//    uint8x8_t rNeonU8 = clamp_s16x8(rNeonS16);
//    uint8x8_t gNeonU8 = clamp_s16x8(gNeonS16);
//    uint8x8_t bNeonU8 = clamp_s16x8(bNeonS16);
//
//    uint8_t rBuffer[8], gBuffer[8], bBuffer[8];
//
//    vst1_u8(rBuffer, rNeonU8);
//    vst1_u8(gBuffer, gNeonU8);
//    vst1_u8(bBuffer, bNeonU8);

//    uint32_t r1 = ((uint32_t)rBuffer[0]) & 0x00FF;
//    uint32_t g1 = ((uint32_t)gBuffer[0]) & 0x00FF;
//    uint32_t b1 = ((uint32_t)bBuffer[0]) & 0x00FF;

    uint32_t colorInt1 = (0x00FF << 24) | ((b1 & 0x00FF) << 16) | ((g1 & 0x00FF) << 8) | (r1 & 0x00FF);


    // c++
    int32_t r2, g2, b2;
    yuv2rgb_i32((uint32_t)y, (uint32_t)u, (uint32_t)v, r2, g2, b2);
//    int16_t rS32 = (128 * y + 179 * ((int16_t)v - 128)) >> 7;
//    int16_t gS32 = (128 * y - 44 * ((int16_t)u - 128) - 91 * ((int16_t)v - 128)) >> 7;
//    int32_t bS32 = (128 * y + 227 * ((int16_t)u - 128)) >> 7;
//
//    uint8_t r2 = clamp(rS32);
//    uint8_t g2 = clamp(gS32);
//    uint8_t b2 = clamp(bS32);

    uint32_t colorInt2 = (0x00FF << 24) | ((b2 & 0x00FF) << 16) | ((g2 & 0x00FF) << 8) | (r2 & 0x00FF);

    LOGD(TAG, "colorInt1 = 0x%x, colorInt2 = 0x%x, diff = %d", colorInt1, colorInt2, (int32_t)colorInt1 - (int32_t)colorInt2);
}

void assembly_test() {
    int a = 4;
    int b = 7;
    //int c = assembly_add(a, b);
    int c = my_function(a, b);
    LOGD(TAG, "%d", c);
}
