package com.zu.camerautil.util

import android.content.Context
import android.util.TypedValue

/**
 * @author zuguorui
 * @date 2024/1/9
 * @description
 */

fun dpToPx(context: Context, dp: Float): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
}