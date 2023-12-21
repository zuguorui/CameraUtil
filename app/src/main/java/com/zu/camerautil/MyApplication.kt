package com.zu.camerautil

import android.app.Application
import android.content.Context
import timber.log.Timber

/**
 * @author zuguorui
 * @date 2023/12/12
 * @description
 */
class MyApplication: Application() {

    override fun onCreate() {
        super.onCreate()
        context = this
        initTimber()
    }

    private fun initTimber() {
        Timber.plant(Timber.DebugTree())
    }

    companion object {
        @JvmStatic
        lateinit var context: Context
            private set
    }
}