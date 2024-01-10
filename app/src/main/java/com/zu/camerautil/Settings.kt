package com.zu.camerautil

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * @author zuguorui
 * @date 2024/1/7
 * @description
 */

val Context.setting: DataStore<Preferences> by preferencesDataStore(name = "setting")

private val OPEN_CAMERA_METHOD_KEY = stringPreferencesKey("specify_camera_method")

private val ioScope = CoroutineScope(Job() + Dispatchers.IO)
object Settings {
    var openCameraMethod = OpenCameraMethod.DIRECTLY
        set(value) {
            val diff = field != value
            Timber.d("openCameraMethod, field = $field, value = $value")
            field = value
            if (diff) {
                saveData(OPEN_CAMERA_METHOD_KEY, value.name)
            }
        }

    init {
        Timber.d("init")
        // 阻塞加载配置
        runBlocking {
            Timber.d("init, runBlocking start")
            MyApplication.context.setting.data.first().let { preference ->
                Timber.d("init, collect")
                openCameraMethod = preference[OPEN_CAMERA_METHOD_KEY]?.let{ name ->
                    Timber.d("read openCameraMethod = $name")
                    OpenCameraMethod.valueOf(name)
                } ?: OpenCameraMethod.DIRECTLY
            }
            Timber.d("init, runBlocking end")
        }
    }

    private fun <T> saveData(key: Preferences.Key<T>, value: T) {
        Timber.d("saveData start")
        ioScope.launch {
            MyApplication.context.setting.edit {
                Timber.d("saveData, key = ${key.name}, value = $value")
                it[key] = value
            }
        }
        Timber.d("saveData end")
    }

}