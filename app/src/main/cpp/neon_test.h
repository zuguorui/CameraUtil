//
// Created by zu on 2023/12/31.
//

#ifndef CAMERAUTIL_NEON_TEST_H
#define CAMERAUTIL_NEON_TEST_H

#include <stdlib.h>
#include <arm_neon.h>

/**
 * neon相关测试
 * 链接：
 * 首页：https://developer.arm.com/Architectures/Neon
 * 指令集：https://developer.arm.com/architectures/instruction-sets/intrinsics
 * RGB分离示例：https://developer.arm.com/documentation/102467/0201/Example---RGB-deinterleaving?lang=en
 * 指南：https://developer.arm.com/documentation/den0018/a/NEON-Intrinsics/Introduction
 * 汇编：https://developer.arm.com/documentation/107829/0200/What-is-assembly-language-/How-assembly-code-works?lang=en
 * 基础汇编指令：https://developer.arm.com/documentation/ddi0602/2022-09/Base-Instructions?lang=en
 * */

void do_neon_test();

void instruction_test();

extern "C" int assembly_add(int a, int b);
extern "C" int my_function(int a, int b);

void assembly_test();


#endif //CAMERAUTIL_NEON_TEST_H
