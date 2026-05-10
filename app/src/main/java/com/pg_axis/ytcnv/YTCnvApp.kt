package com.pg_axis.ytcnv

import android.app.Application

class YTCnvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}