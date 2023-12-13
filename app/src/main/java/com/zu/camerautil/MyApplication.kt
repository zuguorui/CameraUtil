package com.zu.camerautil

import android.app.Application
import timber.log.Timber

/**
 * @author zuguorui
 * @date 2023/12/12
 * @description
 */
class MyApplication: Application() {


    override fun onCreate() {
        super.onCreate()
        initTimber()
    }

    private fun initTimber() {
        Timber.plant(Timber.DebugTree())
    }
}