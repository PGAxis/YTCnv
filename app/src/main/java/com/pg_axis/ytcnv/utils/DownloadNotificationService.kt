package com.pg_axis.ytcnv.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.pg_axis.ytcnv.MainActivity
import com.pg_axis.ytcnv.R
import com.pg_axis.ytcnv.settings.SettingsSave
import java.io.File

class DownloadNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "ytcnv_download_channel"
        const val FINISH_CHANNEL_ID = "ytcnv_finnish_channel"
        const val FAIL_CHANNEL_ID = "ytcnv_fail_channel"

        /** Fallback only — used if a start Intent somehow arrives without an id attached. */
        const val NOTIFICATION_ID = 1
        const val EXTRA_NOTIFICATION_ID = "notificationId"

        var progressIsRunning = false
        private var startedTime: Long? = null

        /** Builds the Intent to start this service for a specific video's progress notification. */
        fun startIntent(context: Context, notificationId: Int): Intent {
            return Intent(context, DownloadNotificationService::class.java)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

        fun showFinishNotification(context: Context, fileName: String, notificationId: Int) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val settings = SettingsSave.getInstance(context)
            val file = if (settings.fileUri.isNotBlank()) {
                val folder = DocumentFile.fromTreeUri(context, settings.fileUri.toUri())
                folder?.findFile(fileName)?.uri
            }
            else {
                val fileTmp = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    fileTmp
                )
            }

            if (file != null) {
                val mimeType = when (fileName.substringAfterLast('.', "").lowercase()) {
                    "mp3" -> "audio/mpeg"
                    "mp4" -> "video/mp4"
                    else -> "*/*"
                }

                val openFileIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(file, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val openFilePendingIntent = PendingIntent.getActivity(
                    context, 1001, openFileIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val openFileAction = NotificationCompat.Action.Builder(
                    R.drawable.file,
                    context.getString(R.string.not_open_file),
                    openFilePendingIntent
                ).build()

                val notification = NotificationCompat.Builder(context, FINISH_CHANNEL_ID)
                    .setContentTitle(context.getString(R.string.not_finished))
                    .setContentText("${context.getString(R.string.not_downloaded)} $fileName")
                    .setSmallIcon(R.drawable.finish)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .addAction(openFileAction)
                    .build()

                manager.notify(notificationId, notification)
            }
            else {
                val notification = NotificationCompat.Builder(context, FINISH_CHANNEL_ID)
                    .setContentTitle(context.getString(R.string.not_finished))
                    .setContentText("${context.getString(R.string.not_downloaded)} $fileName")
                    .setSmallIcon(R.drawable.finish)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                manager.notify(notificationId, notification)
            }
        }

        fun showFailedNotification(context: Context, errMsg: String, notificationId: Int) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, FAIL_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.not_failed))
                .setContentText(errMsg)
                .setSmallIcon(R.drawable.fail)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(notificationId, notification)
        }

        fun updateProgress(context: Context, progress: Float, notificationId: Int, finale: Boolean = false) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val etaText = if (progressIsRunning && progress > 0) {
                val elapsed = System.currentTimeMillis() - (startedTime ?: System.currentTimeMillis())
                val msPerPercent = elapsed.toFloat() / progress
                val remaining = ((100 - progress) * msPerPercent).toLong()
                val totalSec = remaining / 1000
                val h = totalSec / 3600
                val m = (totalSec % 3600) / 60
                val s = totalSec % 60
                if (h > 0) "~%dh %02dm ${context.getString(R.string.remaining)}".format(h, m)
                else "~%dm %02ds ${context.getString(R.string.remaining)}".format(m, s)
            } else null

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.not_downloading))
                .setContentText(if (!finale) "${context.getString(R.string.not_progress)} | ${progress.toInt()}%" else "${context.getString(R.string.finalizing)} | ${progress.toInt()}%")
                .setSmallIcon(R.drawable.icon)
                .setOngoing(true)
                .setProgress(100, progress.toInt(), !progressIsRunning)

            if (etaText != null) builder.setSubText(etaText)

            manager.notify(notificationId, builder.build())
        }

        fun startTimer() {
            startedTime = System.currentTimeMillis()
        }

        fun setProgressType(running: Boolean) {
            progressIsRunning = running
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "YTCnv Downloads", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Download progress notifications" }
            )
            manager.createNotificationChannel(
                NotificationChannel(FINISH_CHANNEL_ID, "YTCnv Download Complete", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Notifies when a download finishes" }
            )
            manager.createNotificationChannel(
                NotificationChannel(FAIL_CHANNEL_ID, "YTCnv Download Failed", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Notifies when a download fails" }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startedTime = null
        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID) ?: NOTIFICATION_ID

        val openIntent = Intent(this, MainActivity::class.java)
        openIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.not_downloading))
            .setContentText(getString(R.string.not_progress))
            .setSmallIcon(R.drawable.icon)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(100, 0, !progressIsRunning)
            .build()

        startForeground(notificationId, notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}