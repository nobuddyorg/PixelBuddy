package io.github.nobuddyorg.pixelbuddy

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashHandler

class PixelBuddyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationService.attach(this)
        // Local crash log only; nothing is ever sent (#22).
        CrashHandler.install(container.crashReports, container.deviceInfo)
    }

    /** Home is in the background and Android needs memory: give most decoded icons back (#56). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) container.iconRepository.trimMemory()
    }

    /** Density or icon theme (e.g. Galaxy Themes) may have changed: re-check icons. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.iconRepository.onConfigurationChanged()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PixelBuddyApp).container
