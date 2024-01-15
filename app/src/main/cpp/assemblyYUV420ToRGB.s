/*------------------------------------------------------------------------------
* Global declarations (for use with linkage to C/C++ world)
* ----------------------------------------------------------------------------*/

.global neonYUV420ToRGBAFullSwing
.internal neonYUV420ToRGBAFullSwing
.type neonYUV420ToRGBAFullSwing, %function

/*------------------------------------------------------------------------------
* Definitions (internal)
* ----------------------------------------------------------------------------*/


.struct 0
yr_stackptr:
.struct yr_stackptr+8
yr_width:
.struct yr_width+4
yr_height:
.struct yr_height+4
yr_outputstride:
.struct yr_outputstride+4
yr_lumastride:
.struct yr_lumastride+4
yr_chromastride:
.struct yr_chromastride+4
yr_transbuf:
.struct yr_transbuf+16


/*------------------------------------------------------------------------------
* Text segment
* ----------------------------------------------------------------------------*/

.text


/**
* neonYUV420ToRGBAFullSwing:
*
* \param X0 Pointer to input Y data
* \param X1 Pointer to input U data
* \param X2 Pointer to input V data
* \param X3 Pointer to output RGBA data
* \param W4 width of block to process (in output RGBA quadruplets)
* \param W5 height of block to process (in pixels)
* \param W6 row-stride (in bytes) of output data block (rgba stride)
* \param W7 row-stride (in bytes) of input Y block (luma stride)
* \param stack row-stride (in bytes) of input U/V blocks (chroma stride)
*
*
* This function converts YUV420 (planar) in BT.601 (full-swing) format to
* 32-Bit RGBA (8888) with alpha channel set to opaque.
*
* Fixed-point arithmetic is used here and RGB is computed as follows:
*
* R = [ ( 128*Y +               179*(V-128) ) >> 7 ]
* G = [ ( 128*Y -  44*(U-128) -  91*(V-128) ) >> 7 ]
* B = [ ( 128*Y + 227*(U-128)               ) >> 7 ]
*
* A is set to 255 statically.
*
*-----------------------------------------------------------------------------*/

        .align 2
neonYUV420ToRGBAFullSwing:
        /*----------------------------------------------------------------------
        * First establish proper stack-frame for facilitated gdb debugging...
        * --------------------------------------------------------------------*/
        mov x17,sp
        sub sp,sp,#(3*16+8*16+256)
        ldr w9,[x17]                                        // R9: chroma stride
        stp x29,x30,[x17,#-16]!                             // Push return address (link register) and framepointer
        stp x20,x28,[x17,#-16]!
        stp x18,x19,[x17,#-16]!
        sub x17,x17,#4*16
        st4 {v8.16b,v9.16b,v10.16b,v11.16b},[x17]
        sub x17,x17,#4*16
        st4 {v12.16b,v13.16b,v14.16b,v15.16b},[x17]
        str w4,[sp,#yr_width]
        str w5,[sp,#yr_height]
        str w6,[sp,#yr_outputstride]
        str w7,[sp,#yr_lumastride]
        str w9,[sp,#yr_chromastride]
        adr x9,yuvrgb_full_multiplier1
        ld1 {v30.4h},[x9]
        adr x9,yuvrgb_full_multiplier2
        ld1 {v31.4h},[x9]
        movi v9.8h,#128
        dup v10.8h,v30.4h[0]
        dup v11.8h,v30.4h[1]
        dup v12.8h,v30.4h[2]
        dup v13.8h,v30.4h[3]
        dup v14.8h,v31.4h[0]
        sxtw x7,w7
        add x4,x3,x6                                        // X4:  RGBA data (next row)
        add x5,x0,x7                                        // X5:  luma data (next row)
0:
        ldr w6,[sp,#yr_width]
        /*----------------------------------------------------------------------
        * Horizontal conversion loop (8 pixel per iteration, vectorized)
        *
        * X0: input Y (current row)       X1: input U (current row)
        * X2: input V (current row)       X3: output RGB (current row)
        * X4: output RGB (next row)       X5: input Y (next row)
        * W6: loop counter
        *---------------------------------------------------------------------*/
        stp x0,x1,[sp,#-16]!
        stp x2,x3,[sp,#-16]!
        stp x4,x5,[sp,#-16]!
        cmp w6,#8
        blt 3f
1:
        ld1 {v0.8b},[x0],#8                                 // V0: y7 y6 y5 y4 y3 y2 y1 y0 (8-bit)
        ld1r {v1.4s},[x1],#4                                // V1: u6 u4 u2 u0 u6 u4 u2 u0
        ld1r {v2.4s},[x2],#4                                // V2: u6 u4 u2 u0 v6 v4 v2 v0
        uxtl v3.8h,v0.8b                                    // V3: y7 y6 y5 y4 y3 y2 y1 y0 (16-bit)
        mov v4.8b,v1.8b
        mov v5.8b,v2.8b
        ld1 {v0.8b},[x5],#8                                 // V0: y7 y6 y5 y4 y3 y2 y1 y0 (8-bit) (next row)
        sub w6,w6,#8
        zip1 v1.8b,v1.8b,v4.8b                              // V1: u6 u6 u4 u4 u2 u2 u0 u0
        zip2 v2.8b,v2.8b,v5.8b                              // V2: v6 v6 v4 v4 v2 v2 v0 v0
        uxtl v4.8h,v0.8b                                    // V4: y7 y6 y5 y4 y3 y2 y1 y0 (next row, 16-bit)
        uxtl v5.8h,v1.8b                                    // V5: u7 u6 u5 u4 u3 u2 u1 u0
        uxtl v6.8h,v2.8b                                    // V6: v7 v6 v5 v4 v3 v2 v1 v0
        sub v5.8h,v5.8h,v9.8h
        sub v6.8h,v6.8h,v9.8h
        mul v3.8h,v3.8h,v10.8h                              // V3: 128*Y
        mul v4.8h,v4.8h,v10.8h                              // V4: 128*Y (next row)
        mul v0.8h,v6.8h,v11.8h                              // V0: 179*(V-128)
        mul v7.8h,v5.8h,v14.8h                              // V7: 227*(U-128)
        mul v1.8h,v5.8h,v12.8h                              // V1: -44*(U-128)
        sqadd v0.8h,v0.8h,v3.8h                             // V0: R << 7
        mla v1.8h,v6.8h,v13.8h                              // V1: -44*(U-128)-91*(V-128)
        sqadd v7.8h,v7.8h,v3.8h                             // V7: B << 7
        sqadd v1.8h,v1.8h,v3.8h                             // V1: G << 7
        sqshrun v0.8b,v0.8h,#7                              // V0: R (8-bit)
        sqshrun v1.8b,v1.8h,#7                              // V1: G (8-bit)
        cmp w6,#8
        sqshrun v2.8b,v7.8h,#7                              // V2: B (8-bit)
        movi v3.8b,#0xff
        mul v7.8h,v6.8h,v11.8h                              // V7: 179*(V-128)
        mul v6.8h,v6.8h,v13.8h                              // V6: -91*(V-128)
        st4 {v0.8b,v1.8b,v2.8b,v3.8b},[x3],#32
        sqadd v3.8h,v7.8h,v4.8h                             // V3: R << 7 (next row)
        mla v6.8h,v5.8h,v12.8h                              // V6: -44*(U-128) - 91*(V-128)
        mul v7.8h,v5.8h,v14.8h
        sqadd v6.8h,v6.8h,v4.8h                             // V6: G << 7 (next row)
        sqadd v4.8h,v4.8h,v7.8h                             // V4: B << 7 (next row)
        sqshrun v0.8b,v3.8h,#7                              // V0: R (8-bit, next row)
        movi v3.8b,#0xff
        sqshrun v1.8b,v6.8h,#7                              // V1: G (8-bit, next row)
        sqshrun v2.8b,v4.8h,#7                              // V2: B (8-bit, next row)
        st4 {v0.8b,v1.8b,v2.8b,v3.8b},[x4],#32
        bge 1b
3:
        mov w19,#255
        cmp w6,#0
        ble 5f
        /*----------------------------------------------------------------------
        * Horizontal conversion loop (scalar)
        *
        * X0: input Y (current row)          X1: input U/V (current row)
        * X2: output RGB (current row)       X3: output RGB (next row)
        * W4: loop counter                   X5: input Y (next row)
        *---------------------------------------------------------------------*/
4:
        ldrb w7,[x0],#1                                     // W7: Y
        ldrb w8,[x1],#1                                     // W8: U
        ldrb w9,[x2],#1                                     // W9: V
        mov w12,#179
        sub w8,w8,#128
        sub w9,w9,#128
        lsl w7,w7,#7                                        // W7: 128*Y
        mov w11,#227
        mul w10,w9,w12                                      // W10: 179*(V-128) (re-usable)
        mov w12,#-44
        mul w11,w8,w11                                      // W11: 227*(U-128) (re-usable)
        mul w8,w8,w12                                       // WR8:  -44*(U-128)
        mov w12,#-91
        madd w8,w9,w12,w8                                   // W8:  -44*(U-128) - 91*(V-128) (re-usable)
        add w9,w7,w10                                       // W9:  R << 7
        add w12,w7,w8                                       // W12: G << 7
        add w7,w7,w11                                       // W7:  B << 7
        asr w9,w9,#7                                        // W9:  R (unclipped)
        cmp w9,#0
        csel w9,wzr,w9,mi
        asr w12,w12,#7                                      // W12: G (unclipped)
        cmp w12,#0
        csel w12,wzr,w12,mi
        asr w7,w7,#7                                        // W7:  B (unclipped)
        cmp w7,#0
        csel w7,wzr,w7,mi
        cmp w9,w19
        csel w9,w19,w9,gt
        cmp w12,w19
        csel w12,w19,w12,gt
        cmp w7,w19
        csel w7,w19,w7,gt
        strb w9,[x3],#1
        strb w12,[x3],#1
        strb w7,[x3],#1
        strb w19,[x3],#1
        /* next pixel */
        ldrb w7,[x0],#1                                     // W7:  Y
        lsl w7,w7,#7
        add w9,w7,w10                                       // W9:  R << 7
        add w12,w7,w8                                       // W12: G << 7
        add w7,w7,w11                                       // W7:  B << 7
        asr w9,w9,#7                                        // W9:  R (unclipped)
        cmp w9,#0
        csel w9,wzr,w9,mi
        asr w12,w12,#7                                      // W12: G (unclipped)
        cmp w12,#0
        csel w12,wzr,w12,mi
        asr w7,w7,#7                                        // W7:  B (unclipped)
        cmp w7,#0
        csel w7,wzr,w7,mi
        cmp w9,w19
        csel w9,w19,w9,gt
        cmp w12,w19
        csel w12,w19,w12,gt
        cmp w7,w19
        csel w7,w19,w7,gt
        strb w9,[x3],#1
        strb w12,[x3],#1
        strb w7,[x3],#1
        strb w19,[x3],#1
        /* next row */
        ldrb w7,[x5],#1                                     // W7:  Y
        lsl w7,w7,#7
        add w9,w7,w10                                       // W9:  R << 7
        add w12,w7,w8                                       // W12: G << 7
        add w7,w7,w11                                       // W7:  B << 7
        asr w9,w9,#7                                        // W9:  R (unclipped)
        cmp w9,#0
        csel w9,wzr,w9,mi
        asr w12,w12,#7                                      // W12: G (unclipped)
        cmp w12,#0
        csel w12,wzr,w12,mi
        asr w7,w7,#7                                        // W7:  B (unclipped)
        cmp w7,#0
        csel w7,wzr,w7,mi
        cmp w9,w19
        csel w9,w19,w9,gt
        cmp w12,w19
        csel w12,w19,w12,gt
        cmp w7,w19
        csel w7,w19,w7,gt
        strb w9,[x4],#1
        strb w12,[x4],#1
        strb w7,[x4],#1
        strb w19,[x4],#1
        /* next pixel */
        ldrb w7,[x5],#1                                     // W7:  Y
        lsl w7,w7,#7
        add w9,w7,w10                                       // W9:  R << 7
        add w12,w7,w8                                       // W12: G << 7
        add w7,w7,w11                                       // W7:  B << 7
        asr w9,w9,#7                                        // W9:  R (unclipped)
        cmp w9,#0
        csel w9,wzr,w9,mi
        asr w12,w12,#7                                      // W12: G (unclipped)
        cmp w12,#0
        csel w12,wzr,w12,mi
        asr w7,w7,#7                                        // W7:  B (unclipped)
        cmp w7,#0
        csel w7,wzr,w7,mi
        cmp w9,w19
        csel w9,w19,w9,gt
        cmp w12,w19
        csel w12,w19,w12,gt
        cmp w7,w19
        csel w7,w19,w7,gt
        subs w6,w6,#2
        strb w9,[x4],#1
        strb w12,[x4],#1
        strb w7,[x4],#1
        strb w19,[x4],#1
        bgt 4b
5:
        ldp x4,x5,[sp],#16
        ldp x2,x3,[sp],#16
        ldp x0,x1,[sp],#16
        ldr w7,[sp,#yr_height]
        ldrsw x6,[sp,#yr_outputstride]
        subs w7,w7,#2
        ldrsw x8,[sp,#yr_lumastride]
        add x3,x3,x6,lsl #1
        ldrsw x9,[sp,#yr_chromastride]
        add x4,x4,x6,lsl #1
        add x0,x0,x8,lsl #1
        add x5,x5,x8,lsl #1
        add x1,x1,x9
        add x2,x2,x9
        str w7,[sp,#yr_height]
        bgt 0b
        /*----------------------------------------------------------------------
        * Epilogue and out...
        *---------------------------------------------------------------------*/
        add x17,sp,#256
        ld4 {v12.16b,v13.16b,v14.16b,v15.16b},[x17],#64
        ld4 {v8.16b,v9.16b,v10.16b,v11.16b},[x17],#64
        ldp x18,x19,[x17],#16
        ldp x20,x28,[x17],#16
        ldp x29,x30,[x17],#16
        add sp,sp,#(3*16+8*16+256)
        ret                                   // Restore frame-pointer and return




/*------------------------------------------------------------------------------
* Constants
* ----------------------------------------------------------------------------*/

        .align 4
yuvrgb_full_multiplier1:
        .short 128,179,-44,-91

        .align 4
yuvrgb_full_multiplier2:
        .short 227,0,0,0