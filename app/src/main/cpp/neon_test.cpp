//
// Created by zu on 2023/12/31.
//

#include "neon_test.h"
#include "log.h"
#include <stdlib.h>

using namespace std;


/*
inline std::uint8_t clamp(std::int32_t n) {
    n &= -(n >= 0);
    return n | ((255 - n) >> 31);
}
 */
inline uint8x8_t clamp_s16x8(int16x8_t vec) {
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

void do_neon_test() {
    uint8_t rBuffer[8], gBuffer[8], bBuffer[8];

    int16x8_t ro = vdupq_n_s16(-10);
    uint8x8_t r = clamp_s16x8(ro);

    int16x8_t go = vdupq_n_s16(300);
    uint8x8_t g = clamp_s16x8(go);

    int16x8_t bo = vdupq_n_s16(255);
    uint8x8_t b = clamp_s16x8(bo);

    vst1_u8(rBuffer, r);
    vst1_u8(gBuffer, g);
    vst1_u8(bBuffer, b);

    for (int j = 0; j < 8; j++) {
        uint32_t ri = ((uint32_t)rBuffer[j]) & 0x00FF;
        uint32_t gi = ((uint32_t)gBuffer[j]) & 0x00FF;
        uint32_t bi = ((uint32_t)bBuffer[j]) & 0x00FF;

        uint32_t colorInt = (0x00FF << 24) | ((bi & 0x00FF) << 16) | ((gi & 0x00FF) << 8) | (ri & 0x00FF);
    }

}
