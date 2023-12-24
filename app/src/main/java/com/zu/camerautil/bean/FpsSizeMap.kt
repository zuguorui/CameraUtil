package com.zu.camerautil.bean

import android.util.Size

class FpsSizeMap {
    val fpsToSizeMap = HashMap<Int, HashSet<Size>>()
    val sizeToFpsMap = HashMap<Size, HashSet<Int>>()

    fun generateSizeToFps() {
        sizeToFpsMap.clear()

        for (fps in fpsToSizeMap.keys) {
            val sizeSet = fpsToSizeMap[fps]!!
            for (size in sizeSet) {
                if (sizeToFpsMap[size] == null) {
                    sizeToFpsMap[size] = HashSet()
                }
                sizeToFpsMap[size]!!.add(fps)
            }
        }
    }

    fun generateFpsToSize() {
        fpsToSizeMap.clear()

        for (size in sizeToFpsMap.keys) {
            val fpsSet = sizeToFpsMap[size]!!
            for (fps in fpsSet) {
                if (fpsToSizeMap[fps] == null) {
                    fpsToSizeMap[fps] = HashSet()
                }
                fpsToSizeMap[fps]!!.add(size)
            }
        }
    }
}