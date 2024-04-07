package com.zu.camerautil.camera

/**
 * @author zuguorui
 * @date 2024/4/7
 * @description
 */
object FlashUtil {
    enum class FlushMode(val id: Int) {
        OFF(0),
        ON(1),
        AUTO(2),
        TORCH(3);

        companion object {
            fun valueOf(id: Int): FlushMode? {
                for (v in values()) {
                    if (v.id == id) {
                        return v
                    }
                }
                return null
            }
        }
    }

    fun getFlushModeName(mode: FlushMode): String {
        return when (mode) {
            FlushMode.OFF -> "关"
            FlushMode.ON -> "开"
            FlushMode.AUTO -> "自动"
            FlushMode.TORCH -> "常亮"
        }
    }


}