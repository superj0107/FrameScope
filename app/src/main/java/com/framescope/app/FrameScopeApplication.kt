package com.framescope.app

import android.app.Application
import com.framescope.app.gaming.GamingModeEngine
import com.framescope.app.shizuku.ShizukuManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FrameScopeApplication : Application() {

    @Inject
    lateinit var shizukuManager: ShizukuManager

    @Inject
    lateinit var gamingModeEngine: GamingModeEngine

    override fun onCreate() {
        super.onCreate()
        com.framescope.app.utils.CrashHandler.init(this)
        shizukuManager.init()
        // Recover gaming mode state if the process was killed while mode was active.
        gamingModeEngine.recoverPersistedState()
    }
}
