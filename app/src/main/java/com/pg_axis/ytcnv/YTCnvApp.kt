package com.pg_axis.ytcnv

import android.app.Application
import com.pg_axis.ytcnv.utils.CrashHandler

class YTCnvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}