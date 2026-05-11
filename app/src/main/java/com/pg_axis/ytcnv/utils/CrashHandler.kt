package com.pg_axis.ytcnv.utils

import android.content.Context
import java.io.File
import java.time.LocalDateTime

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val default = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val logsDir = File(context.filesDir, "crash_logs").also { it.mkdirs() }

            val newLog = File(logsDir, "${System.currentTimeMillis()}.txt")
            newLog.writeText(buildString {
                appendLine("=== ${LocalDateTime.now()} ===")
                appendLine("Thread: ${thread.name}")
                appendLine(throwable.stackTraceToString())
            })

            logsDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }

        } catch (_: Exception) {}

        default?.uncaughtException(thread, throwable)
    }
}