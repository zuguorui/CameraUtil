//
// Created by zu on 2023/12/31.
//

#include "neon_test.h"
#include "log.h"

using namespace std;

static int16x8_t _255 = vdupq_n_s16(255);

inline int16x8_t clamp_s16x8(int16x8_t vec) {
    uint16x8_t a1 = vdupq_n_u16(1);
    uint16x8_t a = vandq_u16(vcgezq_s16(vec), a1);
    int16x8_t b = vreinterpretq_s16_u16(a);
    int16x8_t c = vmulq_n_s16(b, -1);
    vec = vandq_s16(vec, c);
    vec = vorrq_s16(vec, vshrq_n_s16(vsubq_s16(_255, vec), 15));
    return vec;
}

void do_neon_test() {
    int16x8_t r = vdupq_n_s16(256);
    r = clamp_s16x8(r);
}
