package com.zu.camerautil.recorder.encoder

/**
 * @author zuguorui
 * @date 2024/6/6
 * @description
 */
enum class EncoderState {
    // 无效状态，对象创建成功后未prepare时，或者finish后，会切换为此状态
    IDLE,
    // 编码器已准备好，可以调用start开始编码。prepare成功或者stop之后切换为此状态
    READY,
    // 编码器正在编码中。可以调用stop或者finish来停止或结束编码
    RUNNING,
}