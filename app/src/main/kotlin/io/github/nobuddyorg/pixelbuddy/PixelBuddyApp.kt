package io.github.nobuddyorg.pixelbuddy

import android.app.Application
import android.content.Context
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashHandler

class PixelBuddyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Local crash log only; nothing is ever sent (#22).
        CrashHandler.install(container.crashReports, container.deviceInfo)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as PixelBuddyApp).container
