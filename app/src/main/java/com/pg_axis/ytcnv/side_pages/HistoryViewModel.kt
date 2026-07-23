package com.pg_axis.ytcnv.side_pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.pg_axis.ytcnv.settings.SettingsSave

class HistoryViewModel(application: Application): AndroidViewModel(application) {
    val context = getApplication<Application>()
    val settings = SettingsSave.getInstance(context)

    fun onRemove(urlOrId: String) {
        val updated = settings.downloadHistory.toMutableList()
        updated.removeAll { it.urlOrId == urlOrId }
        settings.downloadHistory = updated
    }

    fun markUndownloaded(urlOrId: String) {
        val updated = settings.downloadHistory.toMutableList()
        val index = updated.indexOfFirst { it.urlOrId == urlOrId }
        if (index != -1) {
            updated[index] = updated[index].copy(downloaded = false, uri = "")
        }
        settings.downloadHistory = updated
    }
}