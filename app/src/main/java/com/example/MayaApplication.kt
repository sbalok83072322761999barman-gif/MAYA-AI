package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.core.AppContainer

class MayaApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            container.releaseHeavyResourcesOnLowMemory()
        }
    }
}
