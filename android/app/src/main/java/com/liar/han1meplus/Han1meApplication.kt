package com.liar.han1meplus

import android.app.Application

class Han1meApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CrashReporter.uploadStartupDiagnostics(this)
    }
}