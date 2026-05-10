package com.pg_axis.ytcnv.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import androidx.core.net.toUri

object FileSaver {

    fun saveAudio(context: Context, fileName: String, inputFilePath: String, fileUri: String?): Uri? {
        return if (!fileUri.isNullOrBlank()) {
            saveToChosenFolder(context, fileName, inputFilePath, "audio/mpeg", fileUri)
        } else {
            saveAudioToDownloads(context, fileName, inputFilePath)
        }
    }

    fun saveVideo(context: Context, fileName: String, inputFilePath: String, fileUri: String?) {
        if (!fileUri.isNullOrBlank()) {
            saveToChosenFolder(context, fileName, inputFilePath, "video/mp4", fileUri)
        } else {
            saveVideoToDownloads(context, fileName, inputFilePath)
        }
    }

    private fun saveToChosenFolder(
        context: Context,
        fileName: String,
        inputFilePath: String,
        mimeType: String,
        folderUriString: String
    ): Uri {
        val folderUri = folderUriString.toUri()
        val pickedDir = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IllegalStateException("Could not access chosen folder")
        val newFile = pickedDir.createFile(mimeType, fileName)
            ?: throw IllegalStateException("Could not create file in chosen folder")
        context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
            File(inputFilePath).inputStream().use { it.copyTo(out) }
        }
        return newFile.uri
    }

    private fun saveAudioToDownloads(context: Context, fileName: String, inputFilePath: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore entry")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                File(inputFilePath).inputStream().use { it.copyTo(out) }
            }
            return uri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(downloadsDir, fileName)
            File(inputFilePath).copyTo(outFile, overwrite = true)
            return Uri.fromFile(outFile)
        }
    }

    private fun saveVideoToDownloads(context: Context, fileName: String, inputFilePath: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create MediaStore entry")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                File(inputFilePath).inputStream().use { it.copyTo(out) }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(downloadsDir, fileName)
            File(inputFilePath).copyTo(outFile, overwrite = true)
        }
    }
}